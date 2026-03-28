#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.tmp/smoke"
mkdir -p "${LOG_DIR}"

RUN_ID="${RUN_ID:-$(date +%s)}"
SMOKE_OPENED_AT="${SMOKE_OPENED_AT:-$(date +%F)}"
BOOTSTRAP_USERNAME="${BOOTSTRAP_USERNAME:-bootstrap-admin}"
BOOTSTRAP_PASSWORD="${BOOTSTRAP_PASSWORD:-Admin123!}"
SMOKE_USER_USERNAME="${SMOKE_USER_USERNAME:-smoke-user-${RUN_ID}}"
SMOKE_USER_PASSWORD="${SMOKE_USER_PASSWORD:-Smoke123!}"
SMOKE_ROLE_CODE="${SMOKE_ROLE_CODE:-org_smoke_role_${RUN_ID}}"
SMOKE_REGION_CODE="${SMOKE_REGION_CODE:-SMOKE-HCM-${RUN_ID}}"
SMOKE_OUTLET_CODE="${SMOKE_OUTLET_CODE:-SMOKE-OUTLET-${RUN_ID}}"
SMOKE_EFFECTIVE_FROM="${SMOKE_EFFECTIVE_FROM:-$(date +%F)}"
SMOKE_INGREDIENT_CATEGORY_CODE="${SMOKE_INGREDIENT_CATEGORY_CODE:-SMI${RUN_ID}}"
SMOKE_PRODUCT_CATEGORY_CODE="${SMOKE_PRODUCT_CATEGORY_CODE:-SMP${RUN_ID}}"
SMOKE_BASE_UOM_CODE="${SMOKE_BASE_UOM_CODE:-G${RUN_ID}}"
SMOKE_YIELD_UOM_CODE="${SMOKE_YIELD_UOM_CODE:-C${RUN_ID}}"
SMOKE_INGREDIENT_CODE="${SMOKE_INGREDIENT_CODE:-ING${RUN_ID}}"
SMOKE_PRODUCT_CODE="${SMOKE_PRODUCT_CODE:-PROD${RUN_ID}}"
SMOKE_RECIPE_CODE="${SMOKE_RECIPE_CODE:-RCP${RUN_ID}}"
SMOKE_SUPPLIER_CODE="${SMOKE_SUPPLIER_CODE:-SUP${RUN_ID}}"
SMOKE_SUPPLIER_NAME="${SMOKE_SUPPLIER_NAME:-Smoke Supplier ${RUN_ID}}"
SMOKE_SUPPLIER_INVOICE_NUMBER="${SMOKE_SUPPLIER_INVOICE_NUMBER:-INV-${RUN_ID}}"
KEEP_INFRA_UP="${KEEP_INFRA_UP:-1}"
SKIP_INFRA_BOOTSTRAP="${SKIP_INFRA_BOOTSTRAP:-0}"
SMOKE_DB_POOL_MAX_SIZE="${SMOKE_DB_POOL_MAX_SIZE:-4}"
SMOKE_DB_POOL_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE:-1}"

IAM_PID=""
ORG_PID=""
CATALOG_PID=""
POS_PID=""
INVENTORY_PID=""
PROCUREMENT_PID=""
HR_PID=""
REPORT_PID=""
FINANCE_PID=""
AUDIT_PID=""
GATEWAY_PID=""

log() {
  printf '[smoke] %s\n' "$*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

json_get() {
  local path="$1"
  python3 -c '
import json
import sys

path = sys.argv[1].split(".")
value = json.load(sys.stdin)
for key in path:
    if isinstance(value, list):
        value = value[int(key)]
    else:
        value = value[key]
if isinstance(value, (dict, list)):
    print(json.dumps(value))
else:
    print(value)
' "$path"
}

decimal_equals() {
  local expected="$1"
  local actual="$2"
  python3 -c '
from decimal import Decimal
import sys

expected = Decimal(sys.argv[1])
actual = Decimal(sys.argv[2])
sys.exit(0 if expected == actual else 1)
' "$expected" "$actual"
}

http_json() {
  http_json_with_headers "$1" "$2" "${3:-}" "${4:-}"
}

http_json_with_headers() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local auth_header="${4:-}"
  shift 4 || true
  local response_file
  local status

  response_file="$(mktemp)"
  local -a curl_args=(-sS -o "${response_file}" -w '%{http_code}' -X "${method}")
  if [[ -n "${auth_header}" ]]; then
    curl_args+=(-H "Authorization: ${auth_header}")
  fi
  if [[ -n "${body}" ]]; then
    curl_args+=(-H 'Content-Type: application/json' --data "${body}")
  fi
  while [[ $# -gt 0 ]]; do
    curl_args+=(-H "$1")
    shift
  done
  status="$(curl "${curl_args[@]}" "${url}")"

  if [[ "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    printf 'Request failed: %s %s -> %s\n' "${method}" "${url}" "${status}" >&2
    cat "${response_file}" >&2
    rm -f "${response_file}"
    return 1
  fi

  cat "${response_file}"
  rm -f "${response_file}"
}

http_status() {
  local method="$1"
  local url="$2"
  local auth_header="${3:-}"
  if [[ -n "${auth_header}" ]]; then
    curl -sS -o /dev/null -w '%{http_code}' -X "${method}" -H "Authorization: ${auth_header}" "${url}"
  else
    curl -sS -o /dev/null -w '%{http_code}' -X "${method}" "${url}"
  fi
}

login_access_token() {
  local base_url="$1"
  local username="$2"
  local password="$3"
  local login_response

  login_response="$(http_json POST "${base_url}/auth/login" "{\"username\":\"${username}\",\"password\":\"${password}\"}")" || exit 1
  printf '%s' "${login_response}" | json_get accessToken
}

docker_psql_scalar() {
  local database="$1"
  local sql="$2"
  docker exec fern-postgres psql -U "${FERN_DB_USERNAME:-fern}" -d "${database}" -Atqc "${sql}"
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local retries="${3:-60}"

  for _ in $(seq 1 "${retries}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  printf 'Timed out waiting for %s at %s\n' "${name}" "${url}" >&2
  exit 1
}

wait_for_container_health() {
  local container_name="$1"
  local retries="${2:-60}"

  for _ in $(seq 1 "${retries}"); do
    local health
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_name}" 2>/dev/null || true)"
    if [[ "${health}" == "healthy" || "${health}" == "running" ]]; then
      return 0
    fi
    sleep 2
  done

  printf 'Timed out waiting for container %s to become healthy\n' "${container_name}" >&2
  exit 1
}

cleanup() {
  local exit_code=$?

  if [[ -n "${GATEWAY_PID}" ]] && kill -0 "${GATEWAY_PID}" >/dev/null 2>&1; then
    kill "${GATEWAY_PID}" >/dev/null 2>&1 || true
    wait "${GATEWAY_PID}" 2>/dev/null || true
  fi
  if [[ -n "${ORG_PID}" ]] && kill -0 "${ORG_PID}" >/dev/null 2>&1; then
    kill "${ORG_PID}" >/dev/null 2>&1 || true
    wait "${ORG_PID}" 2>/dev/null || true
  fi
  if [[ -n "${CATALOG_PID}" ]] && kill -0 "${CATALOG_PID}" >/dev/null 2>&1; then
    kill "${CATALOG_PID}" >/dev/null 2>&1 || true
    wait "${CATALOG_PID}" 2>/dev/null || true
  fi
  if [[ -n "${POS_PID}" ]] && kill -0 "${POS_PID}" >/dev/null 2>&1; then
    kill "${POS_PID}" >/dev/null 2>&1 || true
    wait "${POS_PID}" 2>/dev/null || true
  fi
  if [[ -n "${INVENTORY_PID}" ]] && kill -0 "${INVENTORY_PID}" >/dev/null 2>&1; then
    kill "${INVENTORY_PID}" >/dev/null 2>&1 || true
    wait "${INVENTORY_PID}" 2>/dev/null || true
  fi
  if [[ -n "${PROCUREMENT_PID}" ]] && kill -0 "${PROCUREMENT_PID}" >/dev/null 2>&1; then
    kill "${PROCUREMENT_PID}" >/dev/null 2>&1 || true
    wait "${PROCUREMENT_PID}" 2>/dev/null || true
  fi
  if [[ -n "${HR_PID}" ]] && kill -0 "${HR_PID}" >/dev/null 2>&1; then
    kill "${HR_PID}" >/dev/null 2>&1 || true
    wait "${HR_PID}" 2>/dev/null || true
  fi
  if [[ -n "${REPORT_PID}" ]] && kill -0 "${REPORT_PID}" >/dev/null 2>&1; then
    kill "${REPORT_PID}" >/dev/null 2>&1 || true
    wait "${REPORT_PID}" 2>/dev/null || true
  fi
  if [[ -n "${FINANCE_PID}" ]] && kill -0 "${FINANCE_PID}" >/dev/null 2>&1; then
    kill "${FINANCE_PID}" >/dev/null 2>&1 || true
    wait "${FINANCE_PID}" 2>/dev/null || true
  fi
  if [[ -n "${AUDIT_PID}" ]] && kill -0 "${AUDIT_PID}" >/dev/null 2>&1; then
    kill "${AUDIT_PID}" >/dev/null 2>&1 || true
    wait "${AUDIT_PID}" 2>/dev/null || true
  fi
  if [[ -n "${IAM_PID}" ]] && kill -0 "${IAM_PID}" >/dev/null 2>&1; then
    kill "${IAM_PID}" >/dev/null 2>&1 || true
    wait "${IAM_PID}" 2>/dev/null || true
  fi

  if [[ "${KEEP_INFRA_UP}" != "1" ]]; then
    (cd "${ROOT_DIR}" && docker compose down >/dev/null 2>&1) || true
  fi

  exit "${exit_code}"
}

trap cleanup EXIT

require_cmd docker
require_cmd curl
require_cmd python3

log "Starting local infrastructure"
if [[ "${SKIP_INFRA_BOOTSTRAP}" != "1" ]]; then
  (cd "${ROOT_DIR}" && docker compose up -d postgres redis kafka >/dev/null)
else
  log "Using existing local infrastructure"
fi
wait_for_container_health fern-postgres
wait_for_container_health fern-redis
wait_for_container_health fern-kafka 90

log "Building runnable modules for smoke flow"
(
  cd "${ROOT_DIR}" &&
  ./mvnw -q -pl services/iam-service,services/org-service,services/catalog-service,services/pos-service,services/inventory-service,services/procurement-service,services/hr-service,services/report-service,services/finance-service,services/audit-service,services/api-gateway -am install -DskipTests >"${LOG_DIR}/build.log" 2>&1
)

log "Applying database migrations for smoke flow"
(
  cd "${ROOT_DIR}" &&
  ./scripts/migrate-platform.sh all >"${LOG_DIR}/migrate.log" 2>&1
)

log "Starting iam-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  ./mvnw -q -f services/iam-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/iam-service.log" 2>&1
) &
IAM_PID=$!
wait_for_http "iam-service" "http://localhost:8081/actuator/health"

log "Starting org-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  ./mvnw -q -f services/org-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/org-service.log" 2>&1
) &
ORG_PID=$!
wait_for_http "org-service" "http://localhost:8082/actuator/health"

log "Starting catalog-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  FERN_OUTBOX_PUBLISH_DELAY_MS=1000 ./mvnw -q -f services/catalog-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/catalog-service.log" 2>&1
) &
CATALOG_PID=$!
wait_for_http "catalog-service" "http://localhost:8085/actuator/health"

log "Starting inventory-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  ./mvnw -q -f services/inventory-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/inventory-service.log" 2>&1
) &
INVENTORY_PID=$!
wait_for_http "inventory-service" "http://localhost:8087/actuator/health"

log "Starting pos-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  FERN_OUTBOX_PUBLISH_DELAY_MS=1000 ./mvnw -q -f services/pos-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/pos-service.log" 2>&1
) &
POS_PID=$!
wait_for_http "pos-service" "http://localhost:8086/actuator/health"

log "Starting procurement-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  FERN_DATASOURCE_MAX_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  FERN_DATASOURCE_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  FERN_OUTBOX_PUBLISH_DELAY_MS=1000 ./mvnw -q -f services/procurement-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/procurement-service.log" 2>&1
) &
PROCUREMENT_PID=$!
wait_for_http "procurement-service" "http://localhost:8088/actuator/health"

log "Starting hr-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  FERN_DATASOURCE_MAX_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  FERN_DATASOURCE_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  FERN_OUTBOX_PUBLISH_DELAY_MS=1000 ./mvnw -q -f services/hr-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/hr-service.log" 2>&1
) &
HR_PID=$!
wait_for_http "hr-service" "http://localhost:8089/actuator/health"

log "Starting finance-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  FERN_DATASOURCE_MAX_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  FERN_DATASOURCE_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  ./mvnw -q -f services/finance-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/finance-service.log" 2>&1
) &
FINANCE_PID=$!
wait_for_http "finance-service" "http://localhost:8091/actuator/health"

log "Starting report-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  ./mvnw -q -f services/report-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/report-service.log" 2>&1
) &
REPORT_PID=$!
wait_for_http "report-service" "http://localhost:8090/actuator/health"

log "Starting audit-service"
(
  cd "${ROOT_DIR}" &&
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
  ./mvnw -q -f services/audit-service/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/audit-service.log" 2>&1
) &
AUDIT_PID=$!
wait_for_http "audit-service" "http://localhost:8084/actuator/health"

log "Starting api-gateway"
(
  cd "${ROOT_DIR}" &&
  ./mvnw -q -f services/api-gateway/pom.xml spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/api-gateway.log" 2>&1
) &
GATEWAY_PID=$!
wait_for_http "api-gateway" "http://localhost:8080/actuator/health"

log "Logging in as bootstrap admin through gateway"
bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"

log "Creating region and outlet through gateway"
region_response="$(http_json POST "http://localhost:8080/regions" "{\"code\":\"${SMOKE_REGION_CODE}\",\"parentRegionId\":1,\"currencyCode\":\"VND\",\"name\":\"Smoke Region ${RUN_ID}\",\"timezoneName\":\"Asia/Ho_Chi_Minh\"}" "Bearer ${bootstrap_access_token}")"
region_id="$(printf '%s' "${region_response}" | json_get id)"

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
outlet_response="$(http_json POST "http://localhost:8080/outlets" "{\"regionId\":${region_id},\"code\":\"${SMOKE_OUTLET_CODE}\",\"name\":\"Smoke Outlet ${RUN_ID}\",\"status\":\"ACTIVE\",\"openedAt\":\"${SMOKE_OPENED_AT}\"}" "Bearer ${bootstrap_access_token}")"
outlet_id="$(printf '%s' "${outlet_response}" | json_get id)"

log "Creating catalog reference data through gateway"
bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
http_json POST "http://localhost:8080/ingredient-categories" "{\"code\":\"${SMOKE_INGREDIENT_CATEGORY_CODE}\",\"name\":\"Smoke Ingredients ${RUN_ID}\",\"description\":\"Smoke ingredient category\",\"active\":true}" "Bearer ${bootstrap_access_token}" >/dev/null
http_json POST "http://localhost:8080/product-categories" "{\"code\":\"${SMOKE_PRODUCT_CATEGORY_CODE}\",\"name\":\"Smoke Products ${RUN_ID}\",\"description\":\"Smoke product category\",\"active\":true}" "Bearer ${bootstrap_access_token}" >/dev/null
http_json POST "http://localhost:8080/units-of-measure" "{\"code\":\"${SMOKE_BASE_UOM_CODE}\",\"name\":\"Smoke Gram ${RUN_ID}\",\"symbol\":\"g\"}" "Bearer ${bootstrap_access_token}" >/dev/null
http_json POST "http://localhost:8080/units-of-measure" "{\"code\":\"${SMOKE_YIELD_UOM_CODE}\",\"name\":\"Smoke Cup ${RUN_ID}\",\"symbol\":\"cup\"}" "Bearer ${bootstrap_access_token}" >/dev/null

log "Creating catalog master data through gateway"
ingredient_response="$(http_json POST "http://localhost:8080/ingredients" "{\"code\":\"${SMOKE_INGREDIENT_CODE}\",\"name\":\"Smoke Coffee ${RUN_ID}\",\"categoryCode\":\"${SMOKE_INGREDIENT_CATEGORY_CODE}\",\"baseUomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"status\":\"ACTIVE\"}" "Bearer ${bootstrap_access_token}")"
ingredient_id="$(printf '%s' "${ingredient_response}" | json_get id)"

product_response="$(http_json POST "http://localhost:8080/products" "{\"code\":\"${SMOKE_PRODUCT_CODE}\",\"name\":\"Smoke Latte ${RUN_ID}\",\"categoryCode\":\"${SMOKE_PRODUCT_CATEGORY_CODE}\",\"status\":\"ACTIVE\",\"description\":\"Smoke test product\"}" "Bearer ${bootstrap_access_token}")"
product_id="$(printf '%s' "${product_response}" | json_get id)"

recipe_response="$(http_json POST "http://localhost:8080/recipes" "{\"productId\":${product_id},\"recipeCode\":\"${SMOKE_RECIPE_CODE}\",\"description\":\"Smoke test recipe\"}" "Bearer ${bootstrap_access_token}")"
recipe_id="$(printf '%s' "${recipe_response}" | json_get id)"

recipe_version_response="$(http_json POST "http://localhost:8080/recipe-versions" "{\"recipeId\":${recipe_id},\"versionNo\":\"v1\",\"yieldQty\":1.0000,\"yieldUomCode\":\"${SMOKE_YIELD_UOM_CODE}\",\"status\":\"ACTIVE\",\"effectiveFrom\":\"${SMOKE_EFFECTIVE_FROM}\",\"ingredients\":[{\"ingredientId\":${ingredient_id},\"uomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"qty\":10.0000,\"sortOrder\":1}]}" "Bearer ${bootstrap_access_token}")"
recipe_version_id="$(printf '%s' "${recipe_version_response}" | json_get id)"

http_json POST "http://localhost:8080/tax-rates" "{\"productId\":${product_id},\"taxPercent\":10.00,\"effectiveFrom\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${bootstrap_access_token}" >/dev/null
http_json POST "http://localhost:8080/product-prices" "{\"productId\":${product_id},\"scopeType\":\"GLOBAL\",\"priceType\":\"RETAIL\",\"currencyCode\":\"VND\",\"priceValue\":55000.00,\"effectiveFrom\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${bootstrap_access_token}" >/dev/null
http_json PUT "http://localhost:8080/product-availability" "{\"productId\":${product_id},\"outletId\":${outlet_id},\"available\":true}" "Bearer ${bootstrap_access_token}" >/dev/null

log "Catalog reference data prepared; internal resolution will be exercised via POS completion flow"

log "Creating smoke role and user through gateway"
bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
role_response="$(http_json POST "http://localhost:8080/roles" "{\"code\":\"${SMOKE_ROLE_CODE}\",\"name\":\"Smoke Role\",\"permissionCodes\":[\"org.region.read\",\"org.outlet.read\"]}" "Bearer ${bootstrap_access_token}")"
role_code="$(printf '%s' "${role_response}" | json_get code)"

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
user_response="$(http_json POST "http://localhost:8080/users" "{\"username\":\"${SMOKE_USER_USERNAME}\",\"password\":\"${SMOKE_USER_PASSWORD}\",\"fullName\":\"Smoke User\",\"status\":\"ACTIVE\"}" "Bearer ${bootstrap_access_token}")"
user_id="$(printf '%s' "${user_response}" | json_get id)"

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
http_json POST "http://localhost:8080/users/${user_id}/roles" "{\"roleCodes\":[\"${role_code}\",\"outlet_manager\",\"regional_finance\",\"finance\",\"hr\"]}" "Bearer ${bootstrap_access_token}" >/dev/null

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
http_json POST "http://localhost:8080/users/${user_id}/scopes" "{\"regionIds\":[${region_id}],\"outletIds\":[${outlet_id}]}" "Bearer ${bootstrap_access_token}" >/dev/null

log "Logging in as smoke user through gateway"
smoke_access_token="$(login_access_token "http://localhost:8080" "${SMOKE_USER_USERNAME}" "${SMOKE_USER_PASSWORD}")"

log "Calling Org API through gateway"
http_json GET "http://localhost:8080/regions/${region_id}" "" "Bearer ${smoke_access_token}" >/dev/null

log "Running HR payroll source flow through gateway"
bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
employee_response="$(http_json POST "http://localhost:8080/employees" "{\"employeeCode\":\"EMP-${RUN_ID}\",\"fullName\":\"Smoke Employee ${RUN_ID}\",\"status\":\"ACTIVE\",\"hiredAt\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${bootstrap_access_token}")"
employee_id="$(printf '%s' "${employee_response}" | json_get id)"
http_json POST "http://localhost:8080/employee-contracts" "{\"employeeId\":${employee_id},\"employmentType\":\"FULL_TIME\",\"salaryType\":\"MONTHLY\",\"baseSalary\":12000000.00,\"regionId\":${region_id},\"taxCode\":\"TAX-${RUN_ID}\",\"contractStatus\":\"ACTIVE\",\"startDate\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${bootstrap_access_token}" >/dev/null

assignment_response="$(http_json POST "http://localhost:8080/employee-assignments" "{\"employeeId\":${employee_id},\"regionId\":${region_id},\"outletId\":${outlet_id},\"positionTitle\":\"Barista\",\"startDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"primaryAssignment\":true,\"status\":\"ACTIVE\"}" "Bearer ${smoke_access_token}")"
employee_assignment_id="$(printf '%s' "${assignment_response}" | json_get id)"

shift_schedule_response="$(http_json POST "http://localhost:8080/shift-schedules" "{\"regionId\":${region_id},\"outletId\":${outlet_id},\"shiftDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"shiftName\":\"Morning Smoke\",\"startTime\":\"08:00:00\",\"endTime\":\"16:00:00\",\"status\":\"SCHEDULED\"}" "Bearer ${smoke_access_token}")"
shift_schedule_id="$(printf '%s' "${shift_schedule_response}" | json_get id)"

shift_assignment_response="$(http_json POST "http://localhost:8080/shift-assignments" "{\"shiftScheduleId\":${shift_schedule_id},\"employeeId\":${employee_id},\"assignedRole\":\"STAFF\",\"note\":\"Smoke payroll shift\"}" "Bearer ${smoke_access_token}")"
shift_assignment_id="$(printf '%s' "${shift_assignment_response}" | json_get id)"

http_json POST "http://localhost:8080/attendance-events" "{\"employeeId\":${employee_id},\"regionId\":${region_id},\"outletId\":${outlet_id},\"shiftAssignmentId\":${shift_assignment_id},\"eventType\":\"CLOCK_IN\",\"eventTime\":\"${SMOKE_EFFECTIVE_FROM}T08:00:00Z\",\"sourceSystem\":\"SMOKE\"}" "Bearer ${smoke_access_token}" >/dev/null
http_json POST "http://localhost:8080/attendance-events" "{\"employeeId\":${employee_id},\"regionId\":${region_id},\"outletId\":${outlet_id},\"shiftAssignmentId\":${shift_assignment_id},\"eventType\":\"CLOCK_OUT\",\"eventTime\":\"${SMOKE_EFFECTIVE_FROM}T17:00:00Z\",\"sourceSystem\":\"SMOKE\"}" "Bearer ${smoke_access_token}" >/dev/null
attendance_approval_response="$(http_json POST "http://localhost:8080/attendance-approvals/${shift_assignment_id}/approve" "{\"comments\":\"Smoke attendance approved\"}" "Bearer ${smoke_access_token}")"
attendance_approval_status="$(printf '%s' "${attendance_approval_response}" | json_get status)"
if [[ "${attendance_approval_status}" != "APPROVED" ]]; then
  printf 'Expected attendance approval status APPROVED, got %s\n' "${attendance_approval_status}" >&2
  exit 1
fi

log "Seeding operational inventory through gateway"
adjustment_response="$(http_json POST "http://localhost:8080/stock-adjustments" "{\"regionId\":${region_id},\"outletId\":${outlet_id},\"ingredientId\":${ingredient_id},\"adjustmentDirection\":\"IN\",\"qty\":50.0000,\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"reason\":\"BOOTSTRAP\",\"note\":\"Seed opening stock\"}" "Bearer ${smoke_access_token}")"
adjustment_id="$(printf '%s' "${adjustment_response}" | json_get id)"
http_json_with_headers POST "http://localhost:8080/stock-adjustments/${adjustment_id}/post" "" "Bearer ${smoke_access_token}" "Idempotency-Key: smoke-adjustment-post-${RUN_ID}" >/dev/null

waste_response="$(http_json POST "http://localhost:8080/waste-records" "{\"regionId\":${region_id},\"outletId\":${outlet_id},\"ingredientId\":${ingredient_id},\"qty\":2.0000,\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"reason\":\"SPILL\",\"note\":\"Smoke waste\"}" "Bearer ${smoke_access_token}")"
waste_id="$(printf '%s' "${waste_response}" | json_get id)"
http_json_with_headers POST "http://localhost:8080/waste-records/${waste_id}/post" "" "Bearer ${smoke_access_token}" "Idempotency-Key: smoke-waste-post-${RUN_ID}" >/dev/null

stock_count_response="$(http_json POST "http://localhost:8080/stock-count-sessions" "{\"regionId\":${region_id},\"outletId\":${outlet_id},\"countDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"ingredientIds\":[${ingredient_id}],\"note\":\"Smoke stock count\"}" "Bearer ${smoke_access_token}")"
stock_count_id="$(printf '%s' "${stock_count_response}" | json_get id)"
http_json POST "http://localhost:8080/stock-count-sessions/${stock_count_id}/start" "" "Bearer ${smoke_access_token}" >/dev/null
http_json PUT "http://localhost:8080/stock-count-sessions/${stock_count_id}/lines" "{\"lines\":[{\"ingredientId\":${ingredient_id},\"actualQty\":48.0000,\"note\":\"Count confirmed\"}]}" "Bearer ${smoke_access_token}" >/dev/null
http_json_with_headers POST "http://localhost:8080/stock-count-sessions/${stock_count_id}/post" "" "Bearer ${smoke_access_token}" "Idempotency-Key: smoke-count-post-${RUN_ID}" >/dev/null

inventory_balance_response="$(http_json GET "http://localhost:8080/stock-balances?outletId=${outlet_id}&ingredientId=${ingredient_id}" "" "Bearer ${smoke_access_token}")"
balance_qty_on_hand="$(printf '%s' "${inventory_balance_response}" | json_get 0.qtyOnHand)"
if ! decimal_equals "48.0000" "${balance_qty_on_hand}"; then
  printf 'Expected seeded inventory qty_on_hand 48.0000, got %s\n' "${balance_qty_on_hand}" >&2
  exit 1
fi

log "Running POS flow through gateway"
pos_session_response="$(http_json POST "http://localhost:8080/pos-sessions" "{\"regionId\":${region_id},\"outletId\":${outlet_id},\"currencyCode\":\"VND\",\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"note\":\"Smoke POS session\"}" "Bearer ${smoke_access_token}")"
pos_session_id="$(printf '%s' "${pos_session_response}" | json_get id)"

sale_order_response="$(http_json POST "http://localhost:8080/sale-orders" "{\"posSessionId\":${pos_session_id},\"orderType\":\"DINE_IN\",\"note\":\"Smoke main order\",\"lines\":[{\"productId\":${product_id},\"qty\":1.0000,\"note\":\"Initial line\"}]}" "Bearer ${smoke_access_token}")"
sale_order_id="$(printf '%s' "${sale_order_response}" | json_get id)"

updated_order_response="$(http_json PATCH "http://localhost:8080/sale-orders/${sale_order_id}" "{\"note\":\"Smoke updated order\",\"lines\":[{\"productId\":${product_id},\"qty\":1.0000,\"note\":\"Updated line\"}]}" "Bearer ${smoke_access_token}")"
updated_total_amount="$(printf '%s' "${updated_order_response}" | json_get totalAmount)"
if [[ "${updated_total_amount}" != "60500.00" && "${updated_total_amount}" != "60500.0" && "${updated_total_amount}" != "60500" ]]; then
  printf 'Expected updated sale order total 60500.00, got %s\n' "${updated_total_amount}" >&2
  exit 1
fi

http_json_with_headers POST "http://localhost:8080/sale-orders/${sale_order_id}/payments" "{\"paymentMethod\":\"CASH\",\"amount\":30000.00}" "Bearer ${smoke_access_token}" "Idempotency-Key: smoke-pay-cash-${RUN_ID}" >/dev/null
payment_response="$(http_json_with_headers POST "http://localhost:8080/sale-orders/${sale_order_id}/payments" "{\"paymentMethod\":\"CARD\",\"amount\":30500.00,\"transactionRef\":\"SMOKE-TXN-${RUN_ID}\"}" "Bearer ${smoke_access_token}" "Idempotency-Key: smoke-pay-card-${RUN_ID}")"
payment_status="$(printf '%s' "${payment_response}" | json_get paymentStatus)"
if [[ "${payment_status}" != "PAID" ]]; then
  printf 'Expected PAID payment status, got %s\n' "${payment_status}" >&2
  exit 1
fi

complete_response="$(http_json POST "http://localhost:8080/sale-orders/${sale_order_id}/complete" "" "Bearer ${smoke_access_token}")"
completed_status="$(printf '%s' "${complete_response}" | json_get status)"
if [[ "${completed_status}" != "COMPLETED" ]]; then
  printf 'Expected completed sale order, got %s\n' "${completed_status}" >&2
  exit 1
fi

cancel_order_response="$(http_json POST "http://localhost:8080/sale-orders" "{\"posSessionId\":${pos_session_id},\"orderType\":\"TAKEAWAY\",\"note\":\"Smoke cancel order\",\"lines\":[{\"productId\":${product_id},\"qty\":1.0000}]}" "Bearer ${smoke_access_token}")"
cancel_order_id="$(printf '%s' "${cancel_order_response}" | json_get id)"
cancelled_response="$(http_json POST "http://localhost:8080/sale-orders/${cancel_order_id}/cancel" "" "Bearer ${smoke_access_token}")"
cancelled_status="$(printf '%s' "${cancelled_response}" | json_get status)"
if [[ "${cancelled_status}" != "CANCELLED" ]]; then
  printf 'Expected cancelled sale order, got %s\n' "${cancelled_status}" >&2
  exit 1
fi

for _ in $(seq 1 60); do
  inventory_transactions="$(http_json GET "http://localhost:8080/inventory-transactions?outletId=${outlet_id}&ingredientId=${ingredient_id}&txnType=SALE_USAGE&sourceType=SALE_ORDER&sourceId=${sale_order_id}" "" "Bearer ${smoke_access_token}" 2>/dev/null || true)"
  sale_usage_count="$(printf '%s' "${inventory_transactions}" | python3 -c 'import json,sys
data=json.load(sys.stdin) if sys.stdin.readable() else []
print(len(data))
' 2>/dev/null || true)"
  if [[ "${sale_usage_count}" == "1" ]]; then
    break
  fi
  sleep 2
done
if [[ "${sale_usage_count:-0}" != "1" ]]; then
  printf 'Timed out waiting for SALE_USAGE inventory transaction\n' >&2
  exit 1
fi

inventory_balance_response="$(http_json GET "http://localhost:8080/stock-balances?outletId=${outlet_id}&ingredientId=${ingredient_id}" "" "Bearer ${smoke_access_token}")"
balance_qty_on_hand="$(printf '%s' "${inventory_balance_response}" | json_get 0.qtyOnHand)"
if ! decimal_equals "38.0000" "${balance_qty_on_hand}"; then
  printf 'Expected post-sale inventory qty_on_hand 38.0000, got %s\n' "${balance_qty_on_hand}" >&2
  exit 1
fi

closed_session_response="$(http_json POST "http://localhost:8080/pos-sessions/${pos_session_id}/close" "" "Bearer ${smoke_access_token}")"
closed_session_status="$(printf '%s' "${closed_session_response}" | json_get status)"
if [[ "${closed_session_status}" != "CLOSED" ]]; then
  printf 'Expected CLOSED session status, got %s\n' "${closed_session_status}" >&2
  exit 1
fi

reconciled_session_response="$(http_json POST "http://localhost:8080/pos-sessions/${pos_session_id}/reconcile" "{\"countedCashAmount\":30000.00,\"note\":\"Smoke reconciliation\"}" "Bearer ${smoke_access_token}")"
reconciled_session_status="$(printf '%s' "${reconciled_session_response}" | json_get status)"
if [[ "${reconciled_session_status}" != "RECONCILED" ]]; then
  printf 'Expected RECONCILED session status, got %s\n' "${reconciled_session_status}" >&2
  exit 1
fi

log "Running procurement and finance trace flow through gateway"
supplier_response="$(http_json POST "http://localhost:8080/suppliers" "{\"supplierCode\":\"${SMOKE_SUPPLIER_CODE}\",\"name\":\"${SMOKE_SUPPLIER_NAME}\",\"email\":\"supplier-${RUN_ID}@example.com\",\"phone\":\"0900123456\",\"address\":\"Smoke Address\",\"defaultRegionId\":${region_id},\"status\":\"INACTIVE\"}" "Bearer ${smoke_access_token}")"
supplier_id="$(printf '%s' "${supplier_response}" | json_get id)"
http_json POST "http://localhost:8080/suppliers/${supplier_id}/activate" "" "Bearer ${smoke_access_token}" >/dev/null

purchase_order_response="$(http_json POST "http://localhost:8080/purchase-orders" "{\"regionId\":${region_id},\"outletId\":${outlet_id},\"supplierId\":${supplier_id},\"orderDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"expectedDeliveryDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"note\":\"Smoke purchase order\",\"lines\":[{\"ingredientId\":${ingredient_id},\"uomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"qtyOrdered\":5.0000,\"expectedUnitPrice\":12500.00,\"taxPercent\":10.00}]}" "Bearer ${smoke_access_token}")"
purchase_order_id="$(printf '%s' "${purchase_order_response}" | json_get id)"
http_json POST "http://localhost:8080/purchase-orders/${purchase_order_id}/submit" "" "Bearer ${smoke_access_token}" >/dev/null
http_json POST "http://localhost:8080/purchase-orders/${purchase_order_id}/approve" "" "Bearer ${smoke_access_token}" >/dev/null
issued_purchase_order_response="$(http_json POST "http://localhost:8080/purchase-orders/${purchase_order_id}/issue" "" "Bearer ${smoke_access_token}")"
purchase_order_line_id="$(printf '%s' "${issued_purchase_order_response}" | json_get lines.0.id)"

goods_receipt_response="$(http_json POST "http://localhost:8080/goods-receipts" "{\"purchaseOrderId\":${purchase_order_id},\"receiptTime\":\"${SMOKE_EFFECTIVE_FROM}T10:00:00Z\",\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"supplierLotNumber\":\"LOT-${RUN_ID}\",\"note\":\"Smoke goods receipt\",\"lines\":[{\"purchaseOrderLineId\":${purchase_order_line_id},\"ingredientId\":${ingredient_id},\"uomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"qtyReceived\":3.0000,\"unitCost\":12500.00}]}" "Bearer ${smoke_access_token}")"
goods_receipt_id="$(printf '%s' "${goods_receipt_response}" | json_get id)"
http_json POST "http://localhost:8080/goods-receipts/${goods_receipt_id}/receive" "" "Bearer ${smoke_access_token}" >/dev/null
posted_goods_receipt_response="$(http_json_with_headers POST "http://localhost:8080/goods-receipts/${goods_receipt_id}/post" "" "Bearer ${smoke_access_token}" "Idempotency-Key: smoke-gr-post-${RUN_ID}")"
goods_receipt_line_id="$(printf '%s' "${posted_goods_receipt_response}" | json_get lines.0.id)"

supplier_invoice_response="$(http_json POST "http://localhost:8080/supplier-invoices" "{\"supplierId\":${supplier_id},\"regionId\":${region_id},\"outletId\":${outlet_id},\"currencyCode\":\"VND\",\"invoiceNumber\":\"${SMOKE_SUPPLIER_INVOICE_NUMBER}\",\"invoiceDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"lines\":[{\"lineType\":\"STOCK\",\"goodsReceiptLineId\":${goods_receipt_line_id},\"description\":\"Smoke ingredient delivery\",\"qtyInvoiced\":3.0000,\"unitPrice\":12500.00,\"taxPercent\":10.00,\"taxAmount\":3750.00,\"lineTotal\":41250.00}]}" "Bearer ${smoke_access_token}")"
supplier_invoice_id="$(printf '%s' "${supplier_invoice_response}" | json_get id)"
http_json POST "http://localhost:8080/supplier-invoices/${supplier_invoice_id}/approve" "" "Bearer ${smoke_access_token}" >/dev/null

supplier_payment_response="$(http_json_with_headers POST "http://localhost:8080/supplier-payments" "{\"supplierId\":${supplier_id},\"currencyCode\":\"VND\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":41250.00,\"paymentTime\":\"${SMOKE_EFFECTIVE_FROM}T12:00:00Z\",\"transactionRef\":\"PAY-${RUN_ID}\",\"invoiceAllocations\":[{\"supplierInvoiceId\":${supplier_invoice_id},\"allocatedAmount\":41250.00,\"note\":\"Smoke settlement\"}]}" "Bearer ${smoke_access_token}" "Idempotency-Key: smoke-supplier-payment-${RUN_ID}")"
supplier_payment_id="$(printf '%s' "${supplier_payment_response}" | json_get id)"

for _ in $(seq 1 60); do
  inventory_transactions="$(http_json GET "http://localhost:8080/inventory-transactions?outletId=${outlet_id}&ingredientId=${ingredient_id}&txnType=PURCHASE_IN&sourceType=GOODS_RECEIPT&sourceId=${goods_receipt_id}" "" "Bearer ${smoke_access_token}" 2>/dev/null || true)"
  purchase_in_count="$(printf '%s' "${inventory_transactions}" | python3 -c 'import json,sys
data=json.load(sys.stdin) if sys.stdin.readable() else []
print(len(data))
' 2>/dev/null || true)"
  if [[ "${purchase_in_count}" == "1" ]]; then
    break
  fi
  sleep 2
done
if [[ "${purchase_in_count:-0}" != "1" ]]; then
  printf 'Timed out waiting for PURCHASE_IN inventory transaction\n' >&2
  exit 1
fi

inventory_balance_response="$(http_json GET "http://localhost:8080/stock-balances?outletId=${outlet_id}&ingredientId=${ingredient_id}" "" "Bearer ${smoke_access_token}")"
balance_qty_on_hand="$(printf '%s' "${inventory_balance_response}" | json_get 0.qtyOnHand)"
if ! decimal_equals "41.0000" "${balance_qty_on_hand}"; then
  printf 'Expected post-procurement inventory qty_on_hand 41.0000, got %s\n' "${balance_qty_on_hand}" >&2
  exit 1
fi

purchase_order_status_response="$(http_json GET "http://localhost:8080/purchase-orders/${purchase_order_id}" "" "Bearer ${smoke_access_token}")"
purchase_order_status="$(printf '%s' "${purchase_order_status_response}" | json_get status)"
if [[ "${purchase_order_status}" != "PARTIALLY_RECEIVED" ]]; then
  printf 'Expected PARTIALLY_RECEIVED purchase order status, got %s\n' "${purchase_order_status}" >&2
  exit 1
fi

for _ in $(seq 1 60); do
  finance_expense_count="$(docker_psql_scalar fern_operational "SELECT COUNT(*) FROM finance.expense_inventory_purchase WHERE goods_receipt_id = ${goods_receipt_id};" | tr -d '[:space:]')"
  finance_posting_count="$(docker_psql_scalar fern_master "SELECT COUNT(*) FROM finance_projection.accounting_posting_projection WHERE reference_id = '${supplier_payment_id}';" | tr -d '[:space:]')"
  finance_reconciliation_count="$(docker_psql_scalar fern_master "SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot WHERE snapshot_type = 'SUPPLIER_PAYMENT' AND snapshot_value = 41250.00;" | tr -d '[:space:]')"
  if [[ "${finance_expense_count}" == "1" && "${finance_posting_count}" == "1" && "${finance_reconciliation_count}" -ge 1 ]]; then
    break
  fi
  sleep 2
done
if [[ "${finance_expense_count:-0}" != "1" || "${finance_posting_count:-0}" != "1" || "${finance_reconciliation_count:-0}" -lt 1 ]]; then
  printf 'Timed out waiting for finance trace rows\n' >&2
  exit 1
fi

log "Running payroll draft -> approve -> paid flow through gateway"
payroll_period_response="$(http_json POST "http://localhost:8080/payroll-periods" "{\"regionId\":${region_id},\"name\":\"Smoke Payroll ${RUN_ID}\",\"startDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"endDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"payDate\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${smoke_access_token}")"
payroll_period_id="$(printf '%s' "${payroll_period_response}" | json_get id)"
payroll_run_response="$(http_json POST "http://localhost:8080/payroll-runs" "{\"payrollPeriodId\":${payroll_period_id},\"runDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"note\":\"Smoke payroll draft\"}" "Bearer ${smoke_access_token}")"
payroll_run_id="$(printf '%s' "${payroll_run_response}" | json_get id)"
http_json POST "http://localhost:8080/payroll-runs/${payroll_run_id}/submit" "{\"note\":\"Smoke submit\"}" "Bearer ${smoke_access_token}" >/dev/null
http_json POST "http://localhost:8080/payroll-runs/${payroll_run_id}/approve" "{\"note\":\"Smoke approve\"}" "Bearer ${smoke_access_token}" >/dev/null
payroll_paid_response="$(http_json POST "http://localhost:8080/payroll-runs/${payroll_run_id}/mark-paid" "{\"paymentReference\":\"PAYROLL-${RUN_ID}\",\"note\":\"Smoke payroll payment\"}" "Bearer ${smoke_access_token}")"
payroll_status="$(printf '%s' "${payroll_paid_response}" | json_get status)"
if [[ "${payroll_status}" != "PAID" ]]; then
  printf 'Expected payroll run status PAID, got %s\n' "${payroll_status}" >&2
  exit 1
fi

for _ in $(seq 1 60); do
  payroll_expense_count="$(docker_psql_scalar fern_operational "SELECT COUNT(*) FROM finance.expense_payroll WHERE payroll_run_id = ${payroll_run_id};" | tr -d '[:space:]')"
  payroll_fact_count="$(docker_psql_scalar fern_master "SELECT COUNT(*) FROM report.payroll_fact WHERE payroll_run_id = ${payroll_run_id};" | tr -d '[:space:]')"
  payroll_expense_fact_count="$(docker_psql_scalar fern_master "SELECT COUNT(*) FROM report.expense_fact WHERE payroll_run_id = ${payroll_run_id};" | tr -d '[:space:]')"
  if [[ "${payroll_expense_count:-0}" -ge 1 && "${payroll_fact_count:-0}" -ge 1 && "${payroll_expense_fact_count:-0}" -ge 1 ]]; then
    break
  fi
  sleep 2
done
if [[ "${payroll_expense_count:-0}" -lt 1 || "${payroll_fact_count:-0}" -lt 1 || "${payroll_expense_fact_count:-0}" -lt 1 ]]; then
  printf 'Timed out waiting for payroll finance/report rows\n' >&2
  exit 1
fi

payroll_summary_response="$(http_json GET "http://localhost:8080/reports/payroll/summary?regionId=${region_id}&fromDate=${SMOKE_EFFECTIVE_FROM}&toDate=${SMOKE_EFFECTIVE_FROM}" "" "Bearer ${smoke_access_token}")"
payroll_run_report_response="$(http_json GET "http://localhost:8080/reports/payroll/runs/${payroll_run_id}" "" "Bearer ${smoke_access_token}")"
payroll_export_response="$(http_json POST "http://localhost:8080/reports/payroll/export" "{\"regionId\":${region_id},\"fromDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"toDate\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${smoke_access_token}")"
payroll_export_job_id="$(printf '%s' "${payroll_export_response}" | json_get exportJobId)"
payroll_export_status="$(printf '%s' "${payroll_export_response}" | json_get status)"
for _ in $(seq 1 60); do
  if [[ "${payroll_export_status}" == "COMPLETED" || "${payroll_export_status}" == "FAILED" ]]; then
    break
  fi
  sleep 2
  payroll_export_response="$(http_json GET "http://localhost:8080/reports/exports/${payroll_export_job_id}" "" "Bearer ${smoke_access_token}")"
  payroll_export_status="$(printf '%s' "${payroll_export_response}" | json_get status)"
done
if [[ "${payroll_export_status}" != "COMPLETED" ]]; then
  printf 'Expected payroll export status COMPLETED, got %s\n' "${payroll_export_status}" >&2
  exit 1
fi
payroll_export_preview="$(http_json GET "http://localhost:8080/reports/exports/${payroll_export_job_id}/preview" "" "Bearer ${smoke_access_token}")"
payroll_export_download_status="$(http_status GET "http://localhost:8080/reports/exports/${payroll_export_job_id}/download" "Bearer ${smoke_access_token}")"
if [[ "${payroll_export_download_status}" != "200" ]]; then
  printf 'Expected payroll export download status 200, got %s\n' "${payroll_export_download_status}" >&2
  exit 1
fi

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
for endpoint in "/sale-orders/${sale_order_id}/complete" "/goods-receipts/${goods_receipt_id}/post" "/supplier-payments" "/attendance-approvals/${shift_assignment_id}/approve" "/payroll-runs/${payroll_run_id}/approve" "/payroll-runs/${payroll_run_id}/mark-paid"; do
  trace_found="0"
  for _ in $(seq 1 60); do
    trace_response="$(http_json GET "http://localhost:8080/audit/request-traces?sourceService=api-gateway&endpoint=${endpoint}&statusCode=200&limit=10" "" "Bearer ${bootstrap_access_token}" 2>/dev/null || true)"
    trace_found="$(printf '%s' "${trace_response}" | python3 -c 'import json,sys
try:
    data=json.load(sys.stdin)
    print(1 if data.get("items") else 0)
except Exception:
    print(0)
' 2>/dev/null || true)"
    if [[ "${trace_found}" == "1" ]]; then
      break
    fi
    sleep 2
  done
  if [[ "${trace_found}" != "1" ]]; then
    printf 'Timed out waiting for audit request trace for endpoint %s\n' "${endpoint}" >&2
    exit 1
  fi
done

log "Logging out smoke user and verifying revocation"
http_json POST "http://localhost:8080/auth/logout" '{}' "Bearer ${smoke_access_token}" >/dev/null
revoked_status="$(http_status GET "http://localhost:8080/regions/${region_id}" "Bearer ${smoke_access_token}")"
if [[ "${revoked_status}" != "401" ]]; then
  printf 'Expected revoked token to return 401, got %s\n' "${revoked_status}" >&2
  exit 1
fi

log "Smoke flow completed successfully"
log "Region ID: ${region_id}"
log "Outlet ID: ${outlet_id}"
log "Catalog product ID: ${product_id}"
log "User ID: ${user_id}"
log "POS session ID: ${pos_session_id}"
log "Sale order ID: ${sale_order_id}"
log "Goods receipt ID: ${goods_receipt_id}"
log "Supplier payment ID: ${supplier_payment_id}"
log "Employee ID: ${employee_id}"
log "Payroll run ID: ${payroll_run_id}"
log "Service logs: ${LOG_DIR}"
