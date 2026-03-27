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
KEEP_INFRA_UP="${KEEP_INFRA_UP:-1}"
SKIP_INFRA_BOOTSTRAP="${SKIP_INFRA_BOOTSTRAP:-0}"

IAM_PID=""
ORG_PID=""
CATALOG_PID=""
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

http_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local auth_header="${4:-}"
  local response_file
  local status

  response_file="$(mktemp)"
  if [[ -n "${auth_header}" && -n "${body}" ]]; then
    status="$(curl -sS -o "${response_file}" -w '%{http_code}' -X "${method}" \
      -H 'Content-Type: application/json' \
      -H "Authorization: ${auth_header}" \
      --data "${body}" \
      "${url}")"
  elif [[ -n "${auth_header}" ]]; then
    status="$(curl -sS -o "${response_file}" -w '%{http_code}' -X "${method}" \
      -H "Authorization: ${auth_header}" \
      "${url}")"
  elif [[ -n "${body}" ]]; then
    status="$(curl -sS -o "${response_file}" -w '%{http_code}' -X "${method}" \
      -H 'Content-Type: application/json' \
      --data "${body}" \
      "${url}")"
  else
    status="$(curl -sS -o "${response_file}" -w '%{http_code}' -X "${method}" "${url}")"
  fi

  if [[ "${status}" -lt 200 || "${status}" -ge 300 ]]; then
    printf 'Request failed: %s %s -> %s\n' "${method}" "${url}" "${status}" >&2
    cat "${response_file}" >&2
    rm -f "${response_file}"
    exit 1
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
  ./mvnw -q -pl services/iam-service,services/org-service,services/catalog-service,services/audit-service,services/api-gateway -am install -DskipTests >"${LOG_DIR}/build.log" 2>&1
)

log "Starting iam-service"
(
  cd "${ROOT_DIR}" &&
  ./mvnw -q -f services/iam-service/pom.xml spring-boot:run >"${LOG_DIR}/iam-service.log" 2>&1
) &
IAM_PID=$!
wait_for_http "iam-service" "http://localhost:8081/actuator/health"

log "Starting org-service"
(
  cd "${ROOT_DIR}" &&
  ./mvnw -q -f services/org-service/pom.xml spring-boot:run >"${LOG_DIR}/org-service.log" 2>&1
) &
ORG_PID=$!
wait_for_http "org-service" "http://localhost:8082/actuator/health"

log "Starting catalog-service"
(
  cd "${ROOT_DIR}" &&
  ./mvnw -q -f services/catalog-service/pom.xml spring-boot:run >"${LOG_DIR}/catalog-service.log" 2>&1
) &
CATALOG_PID=$!
wait_for_http "catalog-service" "http://localhost:8085/actuator/health"

log "Starting audit-service"
(
  cd "${ROOT_DIR}" &&
  ./mvnw -q -f services/audit-service/pom.xml spring-boot:run >"${LOG_DIR}/audit-service.log" 2>&1
) &
AUDIT_PID=$!
wait_for_http "audit-service" "http://localhost:8084/actuator/health"

log "Starting api-gateway"
(
  cd "${ROOT_DIR}" &&
  ./mvnw -q -f services/api-gateway/pom.xml spring-boot:run >"${LOG_DIR}/api-gateway.log" 2>&1
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

log "Resolving catalog data through gateway"
menu_response="$(http_json GET "http://localhost:8080/internal/catalog/menu?outletId=${outlet_id}&at=${SMOKE_EFFECTIVE_FROM}" "" "Bearer ${bootstrap_access_token}")"
menu_product_id="$(printf '%s' "${menu_response}" | json_get items.0.productId)"
if [[ "${menu_product_id}" != "${product_id}" ]]; then
  printf 'Expected menu product %s, got %s\n' "${product_id}" "${menu_product_id}" >&2
  exit 1
fi

price_response="$(http_json GET "http://localhost:8080/internal/catalog/price-resolution?productId=${product_id}&outletId=${outlet_id}&at=${SMOKE_EFFECTIVE_FROM}" "" "Bearer ${bootstrap_access_token}")"
price_scope_type="$(printf '%s' "${price_response}" | json_get scopeType)"
if [[ "${price_scope_type}" != "GLOBAL" ]]; then
  printf 'Expected GLOBAL price scope, got %s\n' "${price_scope_type}" >&2
  exit 1
fi

recipe_resolution_response="$(http_json GET "http://localhost:8080/internal/catalog/recipe-resolution?productId=${product_id}&at=${SMOKE_EFFECTIVE_FROM}" "" "Bearer ${bootstrap_access_token}")"
resolved_recipe_version_id="$(printf '%s' "${recipe_resolution_response}" | json_get recipeVersionId)"
if [[ "${resolved_recipe_version_id}" != "${recipe_version_id}" ]]; then
  printf 'Expected recipe version %s, got %s\n' "${recipe_version_id}" "${resolved_recipe_version_id}" >&2
  exit 1
fi

log "Creating smoke role and user through gateway"
bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
role_response="$(http_json POST "http://localhost:8080/roles" "{\"code\":\"${SMOKE_ROLE_CODE}\",\"name\":\"Smoke Role\",\"permissionCodes\":[\"org.region.read\",\"org.outlet.read\"]}" "Bearer ${bootstrap_access_token}")"
role_code="$(printf '%s' "${role_response}" | json_get code)"

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
user_response="$(http_json POST "http://localhost:8080/users" "{\"username\":\"${SMOKE_USER_USERNAME}\",\"password\":\"${SMOKE_USER_PASSWORD}\",\"fullName\":\"Smoke User\",\"status\":\"ACTIVE\"}" "Bearer ${bootstrap_access_token}")"
user_id="$(printf '%s' "${user_response}" | json_get id)"

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
http_json POST "http://localhost:8080/users/${user_id}/roles" "{\"roleCodes\":[\"${role_code}\"]}" "Bearer ${bootstrap_access_token}" >/dev/null

bootstrap_access_token="$(login_access_token "http://localhost:8080" "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}")"
http_json POST "http://localhost:8080/users/${user_id}/scopes" "{\"regionIds\":[${region_id}],\"outletIds\":[${outlet_id}]}" "Bearer ${bootstrap_access_token}" >/dev/null

log "Logging in as smoke user through gateway"
smoke_access_token="$(login_access_token "http://localhost:8080" "${SMOKE_USER_USERNAME}" "${SMOKE_USER_PASSWORD}")"

log "Calling Org API through gateway"
http_json GET "http://localhost:8080/regions/${region_id}" "" "Bearer ${smoke_access_token}" >/dev/null

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
log "Service logs: ${LOG_DIR}"
