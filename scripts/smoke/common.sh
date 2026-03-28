#!/usr/bin/env bash

if [[ -n "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  return 0
fi
FERN_SMOKE_COMMON_LOADED=1

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${ROOT_DIR}/.tmp/smoke"
mkdir -p "${LOG_DIR}"

RUN_ID="${RUN_ID:-$(date +%s)}"
SMOKE_OPENED_AT="${SMOKE_OPENED_AT:-$(date +%F)}"
SMOKE_EFFECTIVE_FROM="${SMOKE_EFFECTIVE_FROM:-$(date +%F)}"

FERN_BASE_URL="${FERN_BASE_URL:-http://localhost:8080}"
FERN_IAM_BASE_URL="${FERN_IAM_BASE_URL:-http://localhost:8081}"
FERN_POSTGRES_CONTAINER="${FERN_POSTGRES_CONTAINER:-fern-postgres}"
FERN_REDIS_CONTAINER="${FERN_REDIS_CONTAINER:-fern-redis}"
FERN_KAFKA_CONTAINER="${FERN_KAFKA_CONTAINER:-fern-kafka}"
FERN_MASTER_DB="${FERN_MASTER_DB:-fern_master}"
FERN_OPERATIONAL_DB="${FERN_OPERATIONAL_DB:-fern_operational}"
FERN_DB_USERNAME="${FERN_DB_USERNAME:-fern}"
FERN_DB_PASSWORD="${FERN_DB_PASSWORD:-fern}"
FERN_REDIS_PASSWORD="${FERN_REDIS_PASSWORD:-localdev}"
FERN_JWT_SECRET="${FERN_JWT_SECRET:-fern-local-smoke-secret-0123456789abcdef}"

IAM_HEALTH_URL="${IAM_HEALTH_URL:-http://localhost:8081/actuator/health}"
ORG_HEALTH_URL="${ORG_HEALTH_URL:-http://localhost:8082/actuator/health}"
AUDIT_HEALTH_URL="${AUDIT_HEALTH_URL:-http://localhost:8084/actuator/health}"
CATALOG_HEALTH_URL="${CATALOG_HEALTH_URL:-http://localhost:8085/actuator/health}"
POS_HEALTH_URL="${POS_HEALTH_URL:-http://localhost:8086/actuator/health}"
INVENTORY_HEALTH_URL="${INVENTORY_HEALTH_URL:-http://localhost:8087/actuator/health}"
PROCUREMENT_HEALTH_URL="${PROCUREMENT_HEALTH_URL:-http://localhost:8088/actuator/health}"
HR_HEALTH_URL="${HR_HEALTH_URL:-http://localhost:8089/actuator/health}"
REPORT_HEALTH_URL="${REPORT_HEALTH_URL:-http://localhost:8090/actuator/health}"
FINANCE_HEALTH_URL="${FINANCE_HEALTH_URL:-http://localhost:8091/actuator/health}"
GATEWAY_HEALTH_URL="${GATEWAY_HEALTH_URL:-${FERN_BASE_URL}/actuator/health}"

BOOTSTRAP_USERNAME="${BOOTSTRAP_USERNAME:-bootstrap-admin}"
BOOTSTRAP_PASSWORD="${BOOTSTRAP_PASSWORD:-Admin123!}"
SMOKE_USER_USERNAME="${SMOKE_USER_USERNAME:-smoke-user-${RUN_ID}}"
SMOKE_USER_PASSWORD="${SMOKE_USER_PASSWORD:-Smoke123!}"
SMOKE_OUTSIDER_USERNAME="${SMOKE_OUTSIDER_USERNAME:-smoke-outsider-${RUN_ID}}"
SMOKE_OUTSIDER_PASSWORD="${SMOKE_OUTSIDER_PASSWORD:-Smoke123!}"

SMOKE_ROLE_CODE="${SMOKE_ROLE_CODE:-org_smoke_role_${RUN_ID}}"
SMOKE_REGION_CODE="${SMOKE_REGION_CODE:-SMOKE-HCM-${RUN_ID}}"
SMOKE_OUTLET_CODE="${SMOKE_OUTLET_CODE:-SMOKE-OUTLET-${RUN_ID}}"
SMOKE_OUTSIDER_REGION_CODE="${SMOKE_OUTSIDER_REGION_CODE:-SMOKE-ALT-${RUN_ID}}"
SMOKE_OUTSIDER_OUTLET_CODE="${SMOKE_OUTSIDER_OUTLET_CODE:-SMOKE-ALT-OUTLET-${RUN_ID}}"

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
SKIP_BUILD="${SKIP_BUILD:-0}"
SKIP_MIGRATE="${SKIP_MIGRATE:-0}"
SMOKE_DB_POOL_MAX_SIZE="${SMOKE_DB_POOL_MAX_SIZE:-4}"
SMOKE_DB_POOL_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE:-1}"

declare -a STARTED_SERVICE_PIDS=()
declare -a STARTED_SERVICE_NAMES=()
declare -a PASSED_SCENARIOS=()

HTTP_STATUS=""
HTTP_BODY=""
SMOKE_PHASE="main"

BOOTSTRAP_ACCESS_TOKEN=""
SMOKE_ACCESS_TOKEN=""
SMOKE_OUTSIDER_ACCESS_TOKEN=""

REGION_ID=""
OUTLET_ID=""
OUTSIDER_REGION_ID=""
OUTSIDER_OUTLET_ID=""
ROLE_CODE=""
MAIN_USER_ID=""
OUTSIDER_USER_ID=""
INGREDIENT_ID=""
PRODUCT_ID=""
RECIPE_ID=""
RECIPE_VERSION_ID=""

EMPLOYEE_ID=""
EMPLOYEE_ASSIGNMENT_ID=""
SHIFT_SCHEDULE_ID=""
SHIFT_ASSIGNMENT_ID=""

ADJUSTMENT_ID=""
WASTE_ID=""
STOCK_COUNT_ID=""

POS_SESSION_ID=""
PRIMARY_SALE_ORDER_ID=""
PARTIAL_SALE_ORDER_ID=""
OPEN_SALE_ORDER_ID=""

SUPPLIER_ID=""
PURCHASE_ORDER_ID=""
PURCHASE_ORDER_LINE_ID=""
GOODS_RECEIPT_ID=""
GOODS_RECEIPT_LINE_ID=""
SUPPLIER_INVOICE_ID=""
SUPPLIER_PAYMENT_ID=""

PAYROLL_PERIOD_ID=""
PAYROLL_RUN_ID=""
EMPTY_PAYROLL_PERIOD_ID=""
EMPTY_PAYROLL_RUN_ID=""
PAYROLL_EXPORT_JOB_ID=""

log() {
  printf '[smoke][%s] %s\n' "${SMOKE_PHASE}" "$*"
}

fail() {
  printf '[smoke][%s] %s\n' "${SMOKE_PHASE}" "$*" >&2
  exit 1
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
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

url_encode() {
  python3 -c '
import sys
import urllib.parse

print(urllib.parse.quote(sys.argv[1], safe=""))
' "$1"
}

perform_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local auth_header="${4:-}"
  if [[ $# -gt 4 ]]; then
    shift 4
  else
    set --
  fi

  local response_file
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

  HTTP_STATUS="$(curl "${curl_args[@]}" "${url}")"
  HTTP_BODY="$(cat "${response_file}")"
  rm -f "${response_file}"
}

http_json() {
  perform_request "$@"
  if [[ "${HTTP_STATUS}" -lt 200 || "${HTTP_STATUS}" -ge 300 ]]; then
    printf 'Request failed: %s %s -> %s\n' "$1" "$2" "${HTTP_STATUS}" >&2
    printf '%s\n' "${HTTP_BODY}" >&2
    return 1
  fi
  printf '%s' "${HTTP_BODY}"
}

bootstrap_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  shift 3 || true

  refresh_bootstrap_token
  perform_request "${method}" "${url}" "${body}" "Bearer ${BOOTSTRAP_ACCESS_TOKEN}" "$@"
  if [[ "${HTTP_STATUS}" == "401" && "${HTTP_BODY}" == *"Token is revoked or stale"* ]]; then
    BOOTSTRAP_ACCESS_TOKEN=""
    refresh_bootstrap_token
    perform_request "${method}" "${url}" "${body}" "Bearer ${BOOTSTRAP_ACCESS_TOKEN}" "$@"
  fi
  if [[ "${HTTP_STATUS}" -lt 200 || "${HTTP_STATUS}" -ge 300 ]]; then
    printf 'Bootstrap request failed: %s %s -> %s\n' "${method}" "${url}" "${HTTP_STATUS}" >&2
    printf '%s\n' "${HTTP_BODY}" >&2
    return 1
  fi
  printf '%s' "${HTTP_BODY}"
}

http_expect_status() {
  local expected_status="$1"
  shift
  perform_request "$@"
  if [[ "${HTTP_STATUS}" != "${expected_status}" ]]; then
    fail "Expected HTTP ${expected_status} for $1 $2 but got ${HTTP_STATUS}: ${HTTP_BODY}"
  fi
  printf '%s' "${HTTP_BODY}"
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

assert_json_value() {
  local json="$1"
  local path="$2"
  local expected="$3"
  local actual
  actual="$(printf '%s' "${json}" | json_get "${path}")"
  if [[ "${actual}" != "${expected}" ]]; then
    fail "Expected JSON path ${path} to equal ${expected}, got ${actual}"
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    fail "Expected response to contain '${needle}', got: ${haystack}"
  fi
}

scenario_start() {
  SMOKE_PHASE="$1"
  log "START"
}

scenario_pass() {
  PASSED_SCENARIOS+=("$1")
  log "PASS"
  SMOKE_PHASE="main"
}

login_access_token() {
  local base_url="$1"
  local username="$2"
  local password="$3"
  local attempt

  for attempt in 1 2 3 4 5; do
    perform_request POST "${base_url}/auth/login" "{\"username\":\"${username}\",\"password\":\"${password}\"}" ""
    if [[ "${HTTP_STATUS}" == "200" ]]; then
      printf '%s' "${HTTP_BODY}" | json_get accessToken
      return 0
    fi
    if [[ "${HTTP_STATUS}" == "429" ]]; then
      sleep "${attempt}"
      continue
    fi
    fail "Login failed for ${username}: ${HTTP_STATUS} ${HTTP_BODY}"
  done

  fail "Login rate-limited for ${username} after multiple attempts"
}

docker_psql_scalar() {
  local database="$1"
  local sql="$2"
  docker exec "${FERN_POSTGRES_CONTAINER}" psql -U "${FERN_DB_USERNAME:-fern}" -d "${database}" -Atqc "${sql}"
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

  fail "Timed out waiting for ${name} at ${url}"
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

  fail "Timed out waiting for container ${container_name} to become healthy"
}

flush_local_redis_state() {
  docker exec "${FERN_REDIS_CONTAINER}" redis-cli -a "${FERN_REDIS_PASSWORD}" FLUSHALL >/dev/null
}

wait_for_sql_count_eq() {
  local database="$1"
  local sql="$2"
  local expected="$3"
  local description="$4"
  local retries="${5:-60}"

  for _ in $(seq 1 "${retries}"); do
    local actual
    actual="$(docker_psql_scalar "${database}" "${sql}" | tr -d '[:space:]')"
    if [[ "${actual}" == "${expected}" ]]; then
      return 0
    fi
    sleep 2
  done

  fail "Timed out waiting for ${description} to equal ${expected}"
}

wait_for_sql_count_ge() {
  local database="$1"
  local sql="$2"
  local expected_min="$3"
  local description="$4"
  local retries="${5:-60}"

  for _ in $(seq 1 "${retries}"); do
    local actual
    actual="$(docker_psql_scalar "${database}" "${sql}" | tr -d '[:space:]')"
    if [[ -n "${actual}" && "${actual}" -ge "${expected_min}" ]]; then
      return 0
    fi
    sleep 2
  done

  fail "Timed out waiting for ${description} to reach ${expected_min}"
}

cleanup() {
  local exit_code=$?

  local idx
  for ((idx=${#STARTED_SERVICE_PIDS[@]}-1; idx>=0; idx--)); do
    local pid="${STARTED_SERVICE_PIDS[$idx]}"
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill "${pid}" >/dev/null 2>&1 || true
      wait "${pid}" 2>/dev/null || true
    fi
  done

  if [[ "${KEEP_INFRA_UP}" != "1" ]]; then
    (cd "${ROOT_DIR}" && docker compose down >/dev/null 2>&1) || true
  fi

  exit "${exit_code}"
}

register_started_service() {
  local name="$1"
  local pid="$2"
  STARTED_SERVICE_NAMES+=("${name}")
  STARTED_SERVICE_PIDS+=("${pid}")
}

start_service() {
  local name="$1"
  local pom_path="$2"
  local health_url="$3"
  shift 3

  log "Starting ${name}"
  (
    cd "${ROOT_DIR}" &&
    env \
      FERN_DB_USERNAME="${FERN_DB_USERNAME}" \
      FERN_DB_PASSWORD="${FERN_DB_PASSWORD}" \
      FERN_REDIS_PASSWORD="${FERN_REDIS_PASSWORD}" \
      FERN_JWT_SECRET="${FERN_JWT_SECRET}" \
      "$@" ./mvnw -q -f "${pom_path}" spring-boot:run -Dspring-boot.run.fork=false >"${LOG_DIR}/${name}.log" 2>&1
  ) &
  local pid=$!
  register_started_service "${name}" "${pid}"
  wait_for_http "${name}" "${health_url}"
}

start_local_infrastructure() {
  require_cmd docker
  require_cmd curl
  require_cmd python3

  if [[ "${SKIP_INFRA_BOOTSTRAP}" != "1" ]]; then
    log "Starting local infrastructure"
    (cd "${ROOT_DIR}" && docker compose up -d postgres redis kafka >/dev/null)
  else
    log "Using existing local infrastructure"
  fi

  wait_for_container_health "${FERN_POSTGRES_CONTAINER}"
  wait_for_container_health "${FERN_REDIS_CONTAINER}"
  wait_for_container_health "${FERN_KAFKA_CONTAINER}" 90
  flush_local_redis_state
}

build_runnable_modules() {
  if [[ "${SKIP_BUILD}" == "1" ]]; then
    log "Skipping build"
    return
  fi

  log "Building runnable modules for smoke flow"
  (
    cd "${ROOT_DIR}" &&
    ./mvnw -q -pl services/iam-service,services/org-service,services/catalog-service,services/pos-service,services/inventory-service,services/procurement-service,services/hr-service,services/report-service,services/finance-service,services/audit-service,services/api-gateway -am install -DskipTests >"${LOG_DIR}/build.log" 2>&1
  )
}

apply_database_migrations() {
  if [[ "${SKIP_MIGRATE}" == "1" ]]; then
    log "Skipping migrations"
    return
  fi

  log "Applying database migrations for smoke flow"
  (
    cd "${ROOT_DIR}" &&
    ./scripts/migrate-platform.sh all >"${LOG_DIR}/migrate.log" 2>&1
  )
}

start_application_stack() {
  start_service "iam-service" "services/iam-service/pom.xml" "${IAM_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}"
  start_service "org-service" "services/org-service/pom.xml" "${ORG_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}"
  start_service "catalog-service" "services/catalog-service/pom.xml" "${CATALOG_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
    FERN_OUTBOX_PUBLISH_DELAY_MS=1000
  start_service "inventory-service" "services/inventory-service/pom.xml" "${INVENTORY_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}"
  start_service "pos-service" "services/pos-service/pom.xml" "${POS_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
    FERN_OUTBOX_PUBLISH_DELAY_MS=1000
  start_service "procurement-service" "services/procurement-service/pom.xml" "${PROCUREMENT_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
    FERN_DATASOURCE_MAX_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    FERN_DATASOURCE_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
    FERN_OUTBOX_PUBLISH_DELAY_MS=1000
  start_service "hr-service" "services/hr-service/pom.xml" "${HR_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
    FERN_DATASOURCE_MAX_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    FERN_DATASOURCE_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
    FERN_OUTBOX_PUBLISH_DELAY_MS=1000
  start_service "finance-service" "services/finance-service/pom.xml" "${FINANCE_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}" \
    FERN_DATASOURCE_MAX_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    FERN_DATASOURCE_MIN_IDLE="${SMOKE_DB_POOL_MIN_IDLE}"
  start_service "report-service" "services/report-service/pom.xml" "${REPORT_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}"
  start_service "audit-service" "services/audit-service/pom.xml" "${AUDIT_HEALTH_URL}" \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SMOKE_DB_POOL_MAX_SIZE}" \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SMOKE_DB_POOL_MIN_IDLE}"
  start_service "api-gateway" "services/api-gateway/pom.xml" "${GATEWAY_HEALTH_URL}"
}

refresh_bootstrap_token() {
  if [[ -z "${BOOTSTRAP_ACCESS_TOKEN}" ]]; then
    BOOTSTRAP_ACCESS_TOKEN="$(login_access_token "${FERN_IAM_BASE_URL}" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
  fi
}

refresh_smoke_access_token() {
  SMOKE_ACCESS_TOKEN="$(login_access_token "${FERN_IAM_BASE_URL}" "${SMOKE_USER_USERNAME}" "${SMOKE_USER_PASSWORD}")"
}

refresh_outsider_access_token() {
  SMOKE_OUTSIDER_ACCESS_TOKEN="$(login_access_token "${FERN_IAM_BASE_URL}" "${SMOKE_OUTSIDER_USERNAME}" "${SMOKE_OUTSIDER_PASSWORD}")"
}

create_region() {
  local code="$1"
  local name="$2"
  local region_response
  region_response="$(bootstrap_json POST "${FERN_BASE_URL}/regions" "{\"code\":\"${code}\",\"parentRegionId\":1,\"currencyCode\":\"VND\",\"name\":\"${name}\",\"timezoneName\":\"Asia/Ho_Chi_Minh\"}")" || return 1
  printf '%s' "${region_response}" | json_get id
}

create_outlet() {
  local region_id="$1"
  local code="$2"
  local name="$3"
  local outlet_response
  outlet_response="$(bootstrap_json POST "${FERN_BASE_URL}/outlets" "{\"regionId\":${region_id},\"code\":\"${code}\",\"name\":\"${name}\",\"status\":\"ACTIVE\",\"openedAt\":\"${SMOKE_OPENED_AT}\"}")" || return 1
  printf '%s' "${outlet_response}" | json_get id
}

create_scoped_user() {
  local username="$1"
  local password="$2"
  local full_name="$3"
  local region_id="$4"
  local outlet_id="$5"

  local user_response
  user_response="$(bootstrap_json POST "${FERN_BASE_URL}/users" "{\"username\":\"${username}\",\"password\":\"${password}\",\"fullName\":\"${full_name}\",\"status\":\"ACTIVE\"}")" || return 1
  local user_id
  user_id="$(printf '%s' "${user_response}" | json_get id)"

  bootstrap_json POST "${FERN_BASE_URL}/users/${user_id}/roles" "{\"roleCodes\":[\"${ROLE_CODE}\",\"outlet_manager\",\"regional_finance\",\"finance\",\"hr\"]}" >/dev/null || return 1

  bootstrap_json POST "${FERN_BASE_URL}/users/${user_id}/scopes" "{\"regionIds\":[${region_id}],\"outletIds\":[${outlet_id}]}" >/dev/null || return 1

  printf '%s' "${user_id}"
}

summarize_smoke_success() {
  SMOKE_PHASE="summary"
  log "Completed successfully"
  log "Scenarios passed: ${PASSED_SCENARIOS[*]}"
  log "Region ID: ${REGION_ID}"
  log "Outlet ID: ${OUTLET_ID}"
  log "POS session ID: ${POS_SESSION_ID}"
  log "Goods receipt ID: ${GOODS_RECEIPT_ID}"
  log "Payroll run ID: ${PAYROLL_RUN_ID}"
}
