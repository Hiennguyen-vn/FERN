#!/usr/bin/env bash
# =============================================================================
# seed-demo.sh — FERN Full-System Demo Seed
# =============================================================================
# Creates a realistic, persona-driven staging dataset via live API calls.
# Idempotent: resolves existing entity IDs via live browse/list APIs when API
# 409 conflicts occur.
#
# Prerequisites:
#   1. Full stack running (services + infrastructure)
#   2. Migrations applied
#   3. curl, python3 available
#
# Usage:
#   ./scripts/seed-demo.sh
#
# Environment overrides:
#   FERN_BASE_URL=http://localhost:8080
#   DEMO_PASSWORD=Demo123!
#   BUSINESS_DATE=2026-04-01
# =============================================================================

set -euo pipefail

FERN_BASE_URL="${FERN_BASE_URL:-http://localhost:8080}"
BOOTSTRAP_USERNAME="${BOOTSTRAP_USERNAME:-bootstrap-admin}"
BOOTSTRAP_PASSWORD="${BOOTSTRAP_PASSWORD:-Admin123!}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo123!}"
BUSINESS_DATE="${BUSINESS_DATE:-$(date +%F)}"
PAYROLL_MONTH_START="${PAYROLL_MONTH_START:-$(date +%Y-%m-01)}"
PAYROLL_MONTH_END="${PAYROLL_MONTH_END:-${BUSINESS_DATE}}"
BOOTSTRAP_TOKEN_FILE="${TMPDIR:-/tmp}/fern-seed-bootstrap-token.$$"

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
log() { printf '[seed-demo] %s\n' "$*"; }
fail() { printf '[seed-demo] ERROR: %s\n' "$*" >&2; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Missing: $1"; }

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
require_cmd curl
require_cmd python3

cleanup_seed() {
  rm -f "${BOOTSTRAP_TOKEN_FILE}"
}

trap cleanup_seed EXIT
curl -fsS "${FERN_BASE_URL}/actuator/health" >/dev/null 2>&1 \
  || fail "Gateway not reachable at ${FERN_BASE_URL}. Start the stack first."

log "======================================================================"
log "FERN Demo Seed — ${BUSINESS_DATE}"
log "Gateway: ${FERN_BASE_URL}"
log "======================================================================"

# ---------------------------------------------------------------------------
# Browse/list helpers — used for idempotent ID resolution via live APIs
# ---------------------------------------------------------------------------
urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$1"
}

collection_find_field_value() {
  local body="$1"
  local out_field="$2"
  shift 2 || true
  printf '%s' "${body}" | python3 -c '
import json, sys
out_field = sys.argv[1]
pairs = sys.argv[2:]
assert len(pairs) % 2 == 0, "expected field/value pairs"
data = json.load(sys.stdin)
items = data.get("items", data) if isinstance(data, dict) else data
if not isinstance(items, list):
    items = []
for item in items:
    matched = True
    for index in range(0, len(pairs), 2):
        field = pairs[index]
        expected = pairs[index + 1]
        value = item.get(field)
        if value is None or str(value) != expected:
            matched = False
            break
    if matched:
        value = item.get(out_field)
        if value is not None:
            print(value)
            break
' "$out_field" "$@"
}

collection_count_matches() {
  local body="$1"
  shift || true
  printf '%s' "${body}" | python3 -c '
import json, sys
pairs = sys.argv[1:]
assert len(pairs) % 2 == 0, "expected field/value pairs"
data = json.load(sys.stdin)
items = data.get("items", data) if isinstance(data, dict) else data
if not isinstance(items, list):
    items = []
count = 0
for item in items:
    matched = True
    for index in range(0, len(pairs), 2):
        field = pairs[index]
        expected = pairs[index + 1]
        value = item.get(field)
        if value is None or str(value) != expected:
            matched = False
            break
    if matched:
        count += 1
print(count)
' "$@"
}

json_path_value() {
  local body="$1"
  local path="$2"
  printf '%s' "${body}" | python3 -c '
import json, sys
data = json.load(sys.stdin)
value = data
for segment in sys.argv[1].split("."):
    if isinstance(value, list):
        value = value[int(segment)]
    else:
        value = value.get(segment)
    if value is None:
        break
if value is not None:
    print(value)
' "$path"
}

bootstrap_get() {
  local path="$1"
  shift || true
  bootstrap_request GET "${path}" "" "$@"
  [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]] \
    || fail "HTTP ${HTTP_STATUS} for GET ${path}: ${HTTP_BODY}"
  printf '%s' "${HTTP_BODY}"
}

find_region_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/regions?search=$(urlencode "${code}")&page=0&size=100")"
  collection_find_field_value "${body}" id code "${code}"
}

find_outlet_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/outlets?search=$(urlencode "${code}")&page=0&size=100")"
  collection_find_field_value "${body}" id code "${code}"
}

find_user_id_by_username() {
  local username="$1"
  local body
  body="$(bootstrap_get "/users?search=$(urlencode "${username}")&page=0&size=100")"
  collection_find_field_value "${body}" id username "${username}"
}

find_ingredient_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/ingredients?limit=200")"
  collection_find_field_value "${body}" id code "${code}"
}

uom_exists_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/units-of-measure")"
  collection_count_matches "${body}" code "${code}"
}

find_product_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/products?limit=200")"
  collection_find_field_value "${body}" id code "${code}"
}

find_recipe_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/recipes")"
  collection_find_field_value "${body}" id recipeCode "${code}"
}

find_supplier_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/suppliers")"
  collection_find_field_value "${body}" id supplierCode "${code}"
}

find_employee_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/employees?search=$(urlencode "${code}")&page=0&size=100")"
  collection_find_field_value "${body}" id employeeCode "${code}"
}

find_purchase_order_id_by_note() {
  local note="$1"
  local body
  body="$(bootstrap_get "/purchase-orders?outletId=${OUTLET_D1}&supplierId=${SUPPLIER_ID}&limit=200")"
  collection_find_field_value "${body}" id note "${note}"
}

find_purchase_order_line_id() {
  local po_id="$1"
  local body
  body="$(bootstrap_get "/purchase-orders/${po_id}")"
  json_path_value "${body}" "lines.0.id"
}

find_goods_receipt_id_by_note() {
  local po_id="$1"
  local note="$2"
  local body
  body="$(bootstrap_get "/goods-receipts?purchaseOrderId=${po_id}&limit=100")"
  collection_find_field_value "${body}" id note "${note}"
}

find_goods_receipt_line_id() {
  local gr_id="$1"
  local body
  body="$(bootstrap_get "/goods-receipts/${gr_id}")"
  json_path_value "${body}" "lines.0.id"
}

find_supplier_invoice_id_by_number() {
  local invoice_number="$1"
  local body
  body="$(bootstrap_get "/supplier-invoices?supplierId=${SUPPLIER_ID}&outletId=${OUTLET_D1}&limit=100")"
  collection_find_field_value "${body}" id invoiceNumber "${invoice_number}"
}

find_supplier_payment_id_by_txn_ref() {
  local txn_ref="$1"
  local body
  body="$(bootstrap_get "/supplier-payments?supplierId=${SUPPLIER_ID}&limit=100")"
  collection_find_field_value "${body}" id transactionRef "${txn_ref}"
}

employee_has_active_contract() {
  local employee_id="$1"
  local body
  body="$(bootstrap_get "/employee-contracts?employeeId=${employee_id}&page=0&size=50")"
  collection_count_matches "${body}" employeeId "${employee_id}" contractStatus "ACTIVE"
}

employee_has_active_assignment() {
  local employee_id="$1"
  local body
  body="$(bootstrap_get "/employees/${employee_id}/assignments")"
  collection_count_matches "${body}" outletId "${OUTLET_D1}" status "ACTIVE"
}

find_shift_schedule_id() {
  local body
  body="$(bootstrap_get "/shift-schedules?outletId=${OUTLET_D1}&fromDate=${BUSINESS_DATE}&toDate=${BUSINESS_DATE}&limit=50")"
  collection_find_field_value "${body}" id shiftName "Morning Demo Shift"
}

find_shift_assignment_id() {
  local shift_schedule_id="$1"
  local employee_id="$2"
  local body
  body="$(bootstrap_get "/shift-assignments?shiftScheduleId=${shift_schedule_id}&limit=50")"
  collection_find_field_value "${body}" id employeeId "${employee_id}"
}

find_payroll_period_id_by_name() {
  local region_id="$1"
  local name="$2"
  local body
  body="$(bootstrap_get "/payroll-periods?regionId=${region_id}")"
  collection_find_field_value "${body}" id name "${name}"
}

find_payroll_run_id() {
  local region_id="$1"
  local payroll_period_id="$2"
  local status="${3:-}"
  local body
  body="$(bootstrap_get "/payroll-runs?regionId=${region_id}&limit=100")"
  if [[ -n "${status}" ]]; then
    collection_find_field_value "${body}" id payrollPeriodId "${payroll_period_id}" status "${status}"
  else
    collection_find_field_value "${body}" id payrollPeriodId "${payroll_period_id}"
  fi
}

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------
HTTP_STATUS=""
HTTP_BODY=""

perform_request() {
  local method="$1"; local url="$2"; local body="${3:-}"; local auth="${4:-}"
  shift 4 || true
  local f; f="$(mktemp)"
  local -a args=(-sS -o "${f}" -w '%{http_code}' -X "${method}")
  [[ -n "${auth}" ]] && args+=(-H "Authorization: ${auth}")
  [[ -n "${body}" ]] && args+=(-H 'Content-Type: application/json' --data "${body}")
  while [[ $# -gt 0 ]]; do args+=(-H "$1"); shift; done
  HTTP_STATUS="$(curl "${args[@]}" "${url}")"
  HTTP_BODY="$(cat "${f}")"
  rm -f "${f}"
}

http_ok() {
  perform_request "$@"
  [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]] \
    || fail "HTTP ${HTTP_STATUS} for $1 $2: ${HTTP_BODY}"
  printf '%s' "${HTTP_BODY}"
}

http_expect() {
  # Like http_ok but accepts a second allowed status (e.g. 409)
  local expected_extra="$1"; shift
  perform_request "$@"
  if [[ "${HTTP_STATUS}" != "${expected_extra}" ]]; then
    [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]] \
      || fail "HTTP ${HTTP_STATUS} for $1 $2: ${HTTP_BODY}"
  fi
  printf '%s' "${HTTP_BODY}"
}

json_id() {
  printf '%s' "$1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

json_field() {
  local field="$1"; local value="$2"
  printf '%s' "${value}" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['${field}'])"
}

# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------
BOOTSTRAP_TOKEN=""
refresh_bootstrap_token() {
  if [[ -n "${BOOTSTRAP_TOKEN}" ]]; then
    return
  fi
  if [[ -f "${BOOTSTRAP_TOKEN_FILE}" ]]; then
    BOOTSTRAP_TOKEN="$(cat "${BOOTSTRAP_TOKEN_FILE}")"
    if [[ -n "${BOOTSTRAP_TOKEN}" ]]; then
      return
    fi
  fi
  local attempt=1
  local max_attempts=6
  while (( attempt <= max_attempts )); do
    perform_request POST "${FERN_BASE_URL}/auth/login" \
      "{\"username\":\"${BOOTSTRAP_USERNAME}\",\"password\":\"${BOOTSTRAP_PASSWORD}\"}" ""
    if [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]; then
      BOOTSTRAP_TOKEN="$(json_field accessToken "${HTTP_BODY}")"
      printf '%s' "${BOOTSTRAP_TOKEN}" > "${BOOTSTRAP_TOKEN_FILE}"
      return
    fi
    if [[ "${HTTP_STATUS}" == "429" ]]; then
      sleep $(( attempt ))
      attempt=$(( attempt + 1 ))
      continue
    fi
    fail "HTTP ${HTTP_STATUS} for POST ${FERN_BASE_URL}/auth/login: ${HTTP_BODY}"
  done
  fail "HTTP 429 for POST ${FERN_BASE_URL}/auth/login after ${max_attempts} attempts: ${HTTP_BODY}"
}

bootstrap_request() {
  local method="$1"; local path="$2"; local body="${3:-}"
  shift 3 || true
  local attempt=1
  local max_attempts=6
  while (( attempt <= max_attempts )); do
    refresh_bootstrap_token
    perform_request "${method}" "${FERN_BASE_URL}${path}" "${body}" "Bearer ${BOOTSTRAP_TOKEN}" "$@"
    if [[ "${HTTP_STATUS}" == "401" ]]; then
      BOOTSTRAP_TOKEN=""
      rm -f "${BOOTSTRAP_TOKEN_FILE}"
      attempt=$(( attempt + 1 ))
      continue
    fi
    if [[ "${HTTP_STATUS}" == "429" ]]; then
      sleep $(( attempt ))
      attempt=$(( attempt + 1 ))
      continue
    fi
    return
  done
}

# Create-or-resolve: attempts POST, on 409 or 5xx with existing DB row, resolves ID via psql
create_or_resolve() {
  local resolver_func="$1"; local code="$2"; local method="$3"; local path="$4"; local body="$5"
  shift 5 || true
  bootstrap_request "${method}" "${path}" "${body}" "$@"
  if [[ "${HTTP_STATUS}" == "409" || "${HTTP_STATUS}" -ge 500 ]]; then
    local existing_id
    existing_id="$(${resolver_func} "${code}")"
    if [[ -n "${existing_id}" ]]; then
      printf '%s' "${existing_id}"
      return
    fi
    fail "HTTP ${HTTP_STATUS} for ${method} ${path} and no existing entity resolved from live APIs: ${HTTP_BODY}"
  fi
  [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]] \
    || fail "HTTP ${HTTP_STATUS} for ${method} ${path}: ${HTTP_BODY}"
  json_id "${HTTP_BODY}"
}

# Simpler: POST with bootstrap token, return full body
bootstrap_post() {
  local path="$1"; local body="$2"
  shift 2 || true
  bootstrap_request POST "${path}" "${body}" "$@"
  [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]] \
    || fail "HTTP ${HTTP_STATUS} for POST ${path}: ${HTTP_BODY}"
  printf '%s' "${HTTP_BODY}"
}

# POST with user token, fail on non-2xx
user_post() {
  local token="$1"; local path="$2"; local body="${3:-}"
  shift 3 || true
  http_ok POST "${FERN_BASE_URL}${path}" "${body}" "Bearer ${token}" "$@"
}

# Like user_post but accepts 409 gracefully (returns body either way)
user_post_ok409() {
  local token="$1"; local path="$2"; local body="${3:-}"
  shift 3 || true
  perform_request POST "${FERN_BASE_URL}${path}" "${body}" "Bearer ${token}" "$@"
  [[ "${HTTP_STATUS}" != "409" && ("${HTTP_STATUS}" -lt 200 || "${HTTP_STATUS}" -ge 300) ]] \
    && fail "HTTP ${HTTP_STATUS} for POST ${path}: ${HTTP_BODY}"
  printf '%s' "${HTTP_BODY}"
}

get_user_token() {
  local username="$1"; local password="$2"
  local attempt=1
  local max_attempts=6
  while (( attempt <= max_attempts )); do
    perform_request POST "${FERN_BASE_URL}/auth/login" \
      "{\"username\":\"${username}\",\"password\":\"${password}\"}" ""
    if [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]; then
      json_field accessToken "${HTTP_BODY}"
      return
    fi
    if [[ "${HTTP_STATUS}" == "429" ]]; then
      sleep $(( attempt ))
      attempt=$(( attempt + 1 ))
      continue
    fi
    fail "HTTP ${HTTP_STATUS} for POST ${FERN_BASE_URL}/auth/login: ${HTTP_BODY}"
  done
  fail "HTTP 429 for POST ${FERN_BASE_URL}/auth/login after ${max_attempts} attempts: ${HTTP_BODY}"
}

# ===========================================================================
# 1. ORGANIZATION
# ===========================================================================
log ""
log "=== [1/9] ORGANIZATION ==="

REGION_ID="$(create_or_resolve find_region_id_by_code DEMO-HCM POST "/regions" \
  "{\"code\":\"DEMO-HCM\",\"parentRegionId\":1,\"currencyCode\":\"VND\",\"name\":\"Ho Chi Minh City Demo\",\"timezoneName\":\"Asia/Ho_Chi_Minh\"}")"
log "Region DEMO-HCM → id=${REGION_ID}"

OUTLET_D1="$(create_or_resolve find_outlet_id_by_code DEMO-HCM-DIST1 POST "/outlets" \
  "{\"regionId\":${REGION_ID},\"code\":\"DEMO-HCM-DIST1\",\"name\":\"District 1 Demo Outlet\",\"status\":\"ACTIVE\",\"openedAt\":\"${BUSINESS_DATE}\"}")"
log "Outlet DEMO-HCM-DIST1 → id=${OUTLET_D1}"

OUTLET_D3="$(create_or_resolve find_outlet_id_by_code DEMO-HCM-DIST3 POST "/outlets" \
  "{\"regionId\":${REGION_ID},\"code\":\"DEMO-HCM-DIST3\",\"name\":\"District 3 Demo Outlet\",\"status\":\"ACTIVE\",\"openedAt\":\"${BUSINESS_DATE}\"}")"
log "Outlet DEMO-HCM-DIST3 → id=${OUTLET_D3}"

# ===========================================================================
# 2. IAM — Demo Users
# ===========================================================================
log ""
log "=== [2/9] IAM USERS ==="

create_demo_user() {
  local username="$1"; local full_name="$2"; local roles_json="$3"
  local scope_json="${4:-}"

  refresh_bootstrap_token

  # Create user — 409 means already exists
  bootstrap_request POST "/users" \
    "{\"username\":\"${username}\",\"password\":\"${DEMO_PASSWORD}\",\"fullName\":\"${full_name}\",\"status\":\"ACTIVE\"}"

  local user_id
  if [[ "${HTTP_STATUS}" == "409" ]]; then
    user_id="$(find_user_id_by_username "${username}")"
    [[ -n "${user_id}" ]] || fail "409 for user ${username} but not in DB"
    log "  ${username} already exists → id=${user_id}" >&2
  elif [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]; then
    user_id="$(json_id "${HTTP_BODY}")"
    log "  Created ${username} → id=${user_id}" >&2
  else
    fail "Failed to create ${username}: ${HTTP_STATUS} ${HTTP_BODY}"
  fi

  # Assign roles (idempotent — 409 is fine)
  bootstrap_request POST "/users/${user_id}/roles" \
    "{\"roleCodes\":${roles_json}}" >/dev/null 2>&1 || true

  # Assign scope
  if [[ -n "${scope_json}" ]]; then
    bootstrap_request POST "/users/${user_id}/scopes" \
      "${scope_json}" >/dev/null 2>&1 || true
  fi

  printf '%s' "${user_id}"
}

CASHIER_ID="$(create_demo_user "demo-cashier" "Demo Cashier" \
  '["staff"]' \
  "{\"regionIds\":[],\"outletIds\":[${OUTLET_D1}]}")"

OUTLET_MGR_ID="$(create_demo_user "demo-outlet-mgr" "Demo Outlet Manager" \
  '["outlet_manager"]' \
  "{\"regionIds\":[],\"outletIds\":[${OUTLET_D1}]}")"

REGION_MGR_ID="$(create_demo_user "demo-region-mgr" "Demo Region Manager" \
  '["outlet_manager"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[]}")"

REG_FINANCE_ID="$(create_demo_user "demo-reg-finance" "Demo Regional Finance" \
  '["regional_finance"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[]}")"

HR_ID="$(create_demo_user "demo-hr" "Demo HR Operator" \
  '["hr"]' \
  "{\"systemScope\":true}")"

FINANCE_ID="$(create_demo_user "demo-finance" "Demo Finance Operator" \
  '["finance"]' \
  "{\"systemScope\":true}")"

PM_ID="$(create_demo_user "demo-product-mgr" "Demo Product Manager" \
  '["product_manager"]' \
  "{\"systemScope\":true}")"

SYSADMIN_ID="$(create_demo_user "demo-sysadmin" "Demo System Admin" \
  '["system_admin"]' \
  "{\"systemScope\":true}")"

AUDIT_ID="$(create_demo_user "demo-audit" "Demo Audit Reviewer" \
  '["system_admin"]' \
  "{\"systemScope\":true}")"

READONLY_ID="$(create_demo_user "demo-readonly" "Demo Read-Only Edge" \
  '["regional_finance"]' \
  "{\"regionIds\":[],\"outletIds\":[${OUTLET_D3}]}")"

log "IAM users done."

# Acquire tokens for operational users
log "Acquiring operational tokens..."
OUTLET_MGR_TOKEN="$(get_user_token "demo-outlet-mgr" "${DEMO_PASSWORD}")"
REG_FINANCE_TOKEN="$(get_user_token "demo-reg-finance" "${DEMO_PASSWORD}")"
HR_TOKEN="$(get_user_token "demo-hr" "${DEMO_PASSWORD}")"
FINANCE_TOKEN="$(get_user_token "demo-finance" "${DEMO_PASSWORD}")"

# ===========================================================================
# 3. CATALOG
# ===========================================================================
log ""
log "=== [3/9] CATALOG ==="

# UOM — check existence via psql, create if missing
create_uom_if_missing() {
  local code="$1"; local name="$2"; local symbol="$3"
  local cnt
  cnt="$(uom_exists_by_code "${code}")"
  if [[ "${cnt}" == "0" || -z "${cnt}" ]]; then
    bootstrap_request POST "/units-of-measure" \
      "{\"code\":\"${code}\",\"name\":\"${name}\",\"symbol\":\"${symbol}\"}" >/dev/null 2>&1 || true
    log "  UOM ${code} created"
  else
    log "  UOM ${code} already exists"
  fi
}
refresh_bootstrap_token
create_uom_if_missing "GRAM" "Gram" "g"
create_uom_if_missing "ML" "Millilitre" "ml"
create_uom_if_missing "CUP" "Cup" "cup"

# Ingredient category
bootstrap_request POST "/ingredient-categories" \
  '{"code":"RAW-INGREDIENT","name":"Raw Ingredients","description":"Base raw materials for beverages","active":true}' >/dev/null 2>&1 || true

# Product categories
bootstrap_request POST "/product-categories" \
  '{"code":"HOT-DRINKS","name":"Hot Drinks","description":"Hot beverage menu","active":true}' >/dev/null 2>&1 || true
bootstrap_request POST "/product-categories" \
  '{"code":"COLD-DRINKS","name":"Cold Drinks","description":"Iced beverage menu","active":true}' >/dev/null 2>&1 || true

# Ingredients
ING_COFFEE="$(create_or_resolve find_ingredient_id_by_code COFFEE-ARABICA POST "/ingredients" \
  '{"code":"COFFEE-ARABICA","name":"Arabica Coffee Beans","categoryCode":"RAW-INGREDIENT","baseUomCode":"GRAM","status":"ACTIVE"}')"
log "Ingredient COFFEE-ARABICA → id=${ING_COFFEE}"

ING_SUGAR="$(create_or_resolve find_ingredient_id_by_code SUGAR-WHITE POST "/ingredients" \
  '{"code":"SUGAR-WHITE","name":"White Sugar","categoryCode":"RAW-INGREDIENT","baseUomCode":"GRAM","status":"ACTIVE"}')"
log "Ingredient SUGAR-WHITE → id=${ING_SUGAR}"

ING_MILK="$(create_or_resolve find_ingredient_id_by_code MILK-FULL POST "/ingredients" \
  '{"code":"MILK-FULL","name":"Full Cream Milk","categoryCode":"RAW-INGREDIENT","baseUomCode":"ML","status":"ACTIVE"}')"
log "Ingredient MILK-FULL → id=${ING_MILK}"

# Products
PROD_LATTE="$(create_or_resolve find_product_id_by_code LATTE-HOT POST "/products" \
  '{"code":"LATTE-HOT","name":"Hot Latte","categoryCode":"HOT-DRINKS","status":"ACTIVE","description":"Classic hot latte with steamed milk"}')"
log "Product LATTE-HOT → id=${PROD_LATTE}"

PROD_AMERICANO="$(create_or_resolve find_product_id_by_code AMERICANO-ICE POST "/products" \
  '{"code":"AMERICANO-ICE","name":"Iced Americano","categoryCode":"COLD-DRINKS","status":"ACTIVE","description":"Chilled americano with ice"}')"
log "Product AMERICANO-ICE → id=${PROD_AMERICANO}"

# Recipes (only create if not existing)
RECIPE_LATTE="$(find_recipe_id_by_code RCP-LATTE-V1)"
if [[ -z "${RECIPE_LATTE}" ]]; then
  RECIPE_LATTE_RESP="$(bootstrap_post "/recipes" \
    "{\"productId\":${PROD_LATTE},\"recipeCode\":\"RCP-LATTE-V1\",\"description\":\"Standard hot latte recipe\"}")"
  RECIPE_LATTE="$(json_id "${RECIPE_LATTE_RESP}")"
  bootstrap_post "/recipe-versions" \
    "{\"recipeId\":${RECIPE_LATTE},\"versionNo\":\"v1\",\"yieldQty\":1.0,\"yieldUomCode\":\"CUP\",\"status\":\"ACTIVE\",\"effectiveFrom\":\"${BUSINESS_DATE}\",\"ingredients\":[{\"ingredientId\":${ING_COFFEE},\"uomCode\":\"GRAM\",\"qty\":18.0,\"sortOrder\":1},{\"ingredientId\":${ING_MILK},\"uomCode\":\"ML\",\"qty\":180.0,\"sortOrder\":2},{\"ingredientId\":${ING_SUGAR},\"uomCode\":\"GRAM\",\"qty\":10.0,\"sortOrder\":3}]}" >/dev/null
  log "Recipe RCP-LATTE-V1 → id=${RECIPE_LATTE}"
else
  log "Recipe RCP-LATTE-V1 already exists → id=${RECIPE_LATTE}"
fi

RECIPE_AMERICANO="$(find_recipe_id_by_code RCP-AMERICANO-V1)"
if [[ -z "${RECIPE_AMERICANO}" ]]; then
  RECIPE_AMERICANO_RESP="$(bootstrap_post "/recipes" \
    "{\"productId\":${PROD_AMERICANO},\"recipeCode\":\"RCP-AMERICANO-V1\",\"description\":\"Standard iced americano recipe\"}")"
  RECIPE_AMERICANO="$(json_id "${RECIPE_AMERICANO_RESP}")"
  bootstrap_post "/recipe-versions" \
    "{\"recipeId\":${RECIPE_AMERICANO},\"versionNo\":\"v1\",\"yieldQty\":1.0,\"yieldUomCode\":\"CUP\",\"status\":\"ACTIVE\",\"effectiveFrom\":\"${BUSINESS_DATE}\",\"ingredients\":[{\"ingredientId\":${ING_COFFEE},\"uomCode\":\"GRAM\",\"qty\":22.0,\"sortOrder\":1}]}" >/dev/null
  log "Recipe RCP-AMERICANO-V1 → id=${RECIPE_AMERICANO}"
else
  log "Recipe RCP-AMERICANO-V1 already exists → id=${RECIPE_AMERICANO}"
fi

# Tax rates and prices (ignore 409/duplicates)
for prod_id in "${PROD_LATTE}" "${PROD_AMERICANO}"; do
  bootstrap_request POST "/tax-rates" \
    "{\"productId\":${prod_id},\"taxPercent\":10.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" >/dev/null 2>&1 || true
done

# Product prices — only post if not existing (409 allowed)
bootstrap_request POST "/product-prices" \
  "{\"productId\":${PROD_LATTE},\"scopeType\":\"GLOBAL\",\"priceType\":\"RETAIL\",\"currencyCode\":\"VND\",\"priceValue\":65000.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" >/dev/null 2>&1 || true
bootstrap_request POST "/product-prices" \
  "{\"productId\":${PROD_AMERICANO},\"scopeType\":\"GLOBAL\",\"priceType\":\"RETAIL\",\"currencyCode\":\"VND\",\"priceValue\":55000.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" >/dev/null 2>&1 || true

# Availability — PUT is idempotent by design
for prod_id in "${PROD_LATTE}" "${PROD_AMERICANO}"; do
  for outlet_id in "${OUTLET_D1}" "${OUTLET_D3}"; do
    bootstrap_request PUT "/product-availability" \
      "{\"productId\":${prod_id},\"outletId\":${outlet_id},\"available\":true}" >/dev/null 2>&1 || true
  done
done
log "Catalog seeded."

# ===========================================================================
# 4. INVENTORY — Initial stock
# ===========================================================================
log ""
log "=== [4/9] INVENTORY ==="

post_adj_if_missing() {
  local token="$1"; local ingredient_id="$2"; local idem_key="$3"
  local qty="$4"; local reason="$5"; local note="$6"
  local body
  body="$(bootstrap_get "/stock-balances?outletId=${OUTLET_D1}&ingredientId=${ingredient_id}&page=0&size=20")"
  local has_balance
  has_balance="$(printf '%s' "${body}" | python3 -c '
import json, sys
data = json.load(sys.stdin)
items = data.get("items", data) if isinstance(data, dict) else data
items = items if isinstance(items, list) else []
for item in items:
    qty = item.get("qtyOnHand")
    if qty is not None and float(qty) > 0:
        print("1")
        break
else:
    print("0")
')"
  if [[ "${has_balance}" == "1" ]]; then
    log "  Adjustment ${idem_key} already posted, skipping"
    return
  fi
  local adj_resp
  adj_resp="$(user_post "${token}" "/stock-adjustments" \
    "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"ingredientId\":${ingredient_id},\"adjustmentDirection\":\"IN\",\"qty\":${qty},\"businessDate\":\"${BUSINESS_DATE}\",\"reason\":\"${reason}\",\"note\":\"${note}\"}")"
  local adj_id
  adj_id="$(json_id "${adj_resp}")"
  user_post_ok409 "${token}" "/stock-adjustments/${adj_id}/post" "" \
    "Idempotency-Key: ${idem_key}" >/dev/null
  log "  Adj ${idem_key} posted → id=${adj_id}"
}

post_adj_if_missing "${OUTLET_MGR_TOKEN}" "${ING_COFFEE}" "demo-adj-coffee-d1" \
  "500.000" "OPENING_BALANCE" "Demo seed: opening coffee stock"
post_adj_if_missing "${OUTLET_MGR_TOKEN}" "${ING_MILK}"  "demo-adj-milk-d1"   \
  "5000.000" "OPENING_BALANCE" "Demo seed: opening milk stock"
post_adj_if_missing "${OUTLET_MGR_TOKEN}" "${ING_SUGAR}" "demo-adj-sugar-d1"  \
  "2000.000" "OPENING_BALANCE" "Demo seed: opening sugar stock"
log "Inventory seeded."

# ===========================================================================
# 5. PROCUREMENT
# ===========================================================================
log ""
log "=== [5/9] PROCUREMENT ==="

# Supplier
SUPPLIER_ID="$(create_or_resolve find_supplier_id_by_code DEMO-SUP-001 POST "/suppliers" \
  "{\"supplierCode\":\"DEMO-SUP-001\",\"name\":\"Fresh Brew Supplies Ltd\",\"email\":\"demo-supplier@freshbrew.vn\",\"phone\":\"0283001234\",\"address\":\"12 Nguyen Hue, District 1, HCMC\",\"defaultRegionId\":${REGION_ID},\"status\":\"INACTIVE\"}" \
  )"
# Activate (use bootstrap token — outlet_manager lacks procurement.supplier.write)
bootstrap_request POST "/suppliers/${SUPPLIER_ID}/activate" "" >/dev/null 2>&1 || true
log "Supplier DEMO-SUP-001 → id=${SUPPLIER_ID} (ACTIVE)"

# PO-DEMO-001 — ISSUED (terminal, tests ReadonlyBanner)
PO1_ID="$(find_purchase_order_id_by_note "Demo seed: standard restocking order")"
if [[ -z "${PO1_ID}" ]]; then
  PO1_RESP="$(user_post "${OUTLET_MGR_TOKEN}" "/purchase-orders" \
    "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"supplierId\":${SUPPLIER_ID},\"orderDate\":\"${BUSINESS_DATE}\",\"expectedDeliveryDate\":\"${BUSINESS_DATE}\",\"note\":\"Demo seed: standard restocking order\",\"lines\":[{\"ingredientId\":${ING_COFFEE},\"uomCode\":\"GRAM\",\"qtyOrdered\":1000.00,\"expectedUnitPrice\":350.00,\"taxPercent\":10.00},{\"ingredientId\":${ING_MILK},\"uomCode\":\"ML\",\"qtyOrdered\":10000.00,\"expectedUnitPrice\":25.00,\"taxPercent\":10.00}]}")"
  PO1_ID="$(json_id "${PO1_RESP}")"
  PO1_LINE1_ID="$(printf '%s' "${PO1_RESP}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["lines"][0]["id"])')"
  user_post "${OUTLET_MGR_TOKEN}" "/purchase-orders/${PO1_ID}/submit" "" >/dev/null
  # PO approval requires regional_finance permission
  user_post "${REG_FINANCE_TOKEN}" "/purchase-orders/${PO1_ID}/approve" "" >/dev/null
  PO1_ISSUED="$(user_post "${OUTLET_MGR_TOKEN}" "/purchase-orders/${PO1_ID}/issue" "")"
  PO1_LINE1_ID="$(printf '%s' "${PO1_ISSUED}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["lines"][0]["id"])')"
  log "PO-DEMO-001 → id=${PO1_ID} (ISSUED)"
else
  PO1_LINE1_ID="$(find_purchase_order_line_id "${PO1_ID}")"
  log "PO-DEMO-001 already exists → id=${PO1_ID}"
fi

# PO-DEMO-002 — SUBMITTED (in-queue, tests approval flow)
PO2_ID="$(find_purchase_order_id_by_note "Demo seed: pending approval restock")"
if [[ -z "${PO2_ID}" ]]; then
  PO2_RESP="$(user_post "${OUTLET_MGR_TOKEN}" "/purchase-orders" \
    "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"supplierId\":${SUPPLIER_ID},\"orderDate\":\"${BUSINESS_DATE}\",\"expectedDeliveryDate\":\"${BUSINESS_DATE}\",\"note\":\"Demo seed: pending approval restock\",\"lines\":[{\"ingredientId\":${ING_SUGAR},\"uomCode\":\"GRAM\",\"qtyOrdered\":5000.00,\"expectedUnitPrice\":8.00,\"taxPercent\":10.00}]}")"
  PO2_ID="$(json_id "${PO2_RESP}")"
  user_post "${OUTLET_MGR_TOKEN}" "/purchase-orders/${PO2_ID}/submit" "" >/dev/null
  log "PO-DEMO-002 → id=${PO2_ID} (SUBMITTED)"
else
  log "PO-DEMO-002 already exists → id=${PO2_ID}"
fi

# Goods Receipt — POSTED
GR_ID="$(find_goods_receipt_id_by_note "${PO1_ID}" "Demo seed: received shipment")"
GR_LINE1_ID=""
if [[ -z "${GR_ID}" ]]; then
  GR_RESP="$(user_post "${OUTLET_MGR_TOKEN}" "/goods-receipts" \
    "{\"purchaseOrderId\":${PO1_ID},\"receiptTime\":\"${BUSINESS_DATE}T09:00:00Z\",\"businessDate\":\"${BUSINESS_DATE}\",\"supplierLotNumber\":\"LOT-DEMO-2026\",\"note\":\"Demo seed: received shipment\",\"lines\":[{\"purchaseOrderLineId\":${PO1_LINE1_ID},\"ingredientId\":${ING_COFFEE},\"uomCode\":\"GRAM\",\"qtyReceived\":1000.00,\"unitCost\":350.00}]}")"
  GR_ID="$(json_id "${GR_RESP}")"
  GR_LINE1_ID="$(printf '%s' "${GR_RESP}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["lines"][0]["id"])')"
  user_post "${OUTLET_MGR_TOKEN}" "/goods-receipts/${GR_ID}/receive" "" >/dev/null
  user_post_ok409 "${OUTLET_MGR_TOKEN}" "/goods-receipts/${GR_ID}/post" "" \
    "Idempotency-Key: demo-gr-post-${GR_ID}" >/dev/null
  log "GR-DEMO → id=${GR_ID} (POSTED)"
else
  GR_LINE1_ID="$(find_goods_receipt_line_id "${GR_ID}")"
  log "GR-DEMO already exists → id=${GR_ID}"
fi

# Supplier invoice
INV_ID="$(find_supplier_invoice_id_by_number "INV-DEMO-2026-001")"
if [[ -z "${INV_ID}" ]]; then
  # Invoice creation and approval require regional_finance (procurement.invoice.review)
  INV_RESP="$(user_post "${REG_FINANCE_TOKEN}" "/supplier-invoices" \
    "{\"supplierId\":${SUPPLIER_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"currencyCode\":\"VND\",\"invoiceNumber\":\"INV-DEMO-2026-001\",\"invoiceDate\":\"${BUSINESS_DATE}\",\"lines\":[{\"lineType\":\"STOCK\",\"goodsReceiptLineId\":${GR_LINE1_ID},\"description\":\"Arabica coffee beans delivery\",\"qtyInvoiced\":1000.00,\"unitPrice\":350.00,\"taxPercent\":10.00,\"taxAmount\":35000.00,\"lineTotal\":385000.00}]}")"
  INV_ID="$(json_id "${INV_RESP}")"
  # Invoice approval requires regional_finance permission
  user_post "${REG_FINANCE_TOKEN}" "/supplier-invoices/${INV_ID}/approve" "" >/dev/null
  log "Invoice INV-DEMO-2026-001 → id=${INV_ID} (APPROVED)"
else
  log "Invoice INV-DEMO-2026-001 already exists → id=${INV_ID}"
fi

# Supplier payment
PAY_ID="$(find_supplier_payment_id_by_txn_ref "TXN-DEMO-2026-001")"
if [[ -z "${PAY_ID}" ]]; then
  # Supplier payment requires finance role (procurement.payment.record)
  PAY_RESP="$(user_post_ok409 "${FINANCE_TOKEN}" "/supplier-payments" \
    "{\"supplierId\":${SUPPLIER_ID},\"currencyCode\":\"VND\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":385000.00,\"paymentTime\":\"${BUSINESS_DATE}T14:00:00Z\",\"transactionRef\":\"TXN-DEMO-2026-001\",\"invoiceAllocations\":[{\"supplierInvoiceId\":${INV_ID},\"allocatedAmount\":385000.00,\"note\":\"Full settlement demo\"}]}" \
    "Idempotency-Key: demo-supplier-payment-${INV_ID}")"
  PAY_ID="$(json_id "${PAY_RESP}" 2>/dev/null || find_supplier_payment_id_by_txn_ref "TXN-DEMO-2026-001")"
  log "Supplier payment → id=${PAY_ID}"
else
  log "Supplier payment already exists → id=${PAY_ID}"
fi

# ===========================================================================
# 6. HR
# ===========================================================================
log ""
log "=== [6/9] HR ==="

create_employee() {
  local code="$1"; local name="$2"; local dob="$3"; local gender="$4"
  local email="$5"; local phone="$6"
  local existing_id
  existing_id="$(find_employee_id_by_code "${code}")"
  if [[ -n "${existing_id}" ]]; then
    log "  Employee ${code} already exists → id=${existing_id}" >&2
    printf '%s' "${existing_id}"
    return
  fi
  local resp
  resp="$(bootstrap_post "/employees" \
    "{\"employeeCode\":\"${code}\",\"fullName\":\"${name}\",\"status\":\"ACTIVE\",\"hiredAt\":\"${BUSINESS_DATE}\",\"dob\":\"${dob}\",\"gender\":\"${gender}\",\"email\":\"${email}\",\"phone\":\"${phone}\"}")"
  local eid; eid="$(json_id "${resp}")"
  log "  Employee ${code} → id=${eid}" >&2
  printf '%s' "${eid}"
}

EMP1_ID="$(create_employee "EMP-DEMO-001" "Nguyen Thi Lan" "1995-06-15" "FEMALE" "lan.nguyen@demo.fern" "0901234567")"
EMP2_ID="$(create_employee "EMP-DEMO-002" "Tran Van Minh" "1998-03-22" "MALE" "minh.tran@demo.fern" "0907654321")"
EMP3_ID="$(create_employee "EMP-DEMO-003" "Le Thi Mai" "2000-11-08" "FEMALE" "mai.le@demo.fern" "0912345678")"

# Contracts (in fern_master.hr_master)
for emp_id in "${EMP1_ID}" "${EMP2_ID}" "${EMP3_ID}"; do
  cnt="$(employee_has_active_contract "${emp_id}")"
  if [[ "${cnt}" == "0" || -z "${cnt}" ]]; then
    if [[ "${emp_id}" == "${EMP1_ID}" ]]; then
      bootstrap_post "/employee-contracts" \
        "{\"employeeId\":${emp_id},\"employmentType\":\"FULL_TIME\",\"salaryType\":\"MONTHLY\",\"baseSalary\":15000000.00,\"regionId\":${REGION_ID},\"taxCode\":\"MST-DEMO-001\",\"contractStatus\":\"ACTIVE\",\"startDate\":\"${BUSINESS_DATE}\"}" >/dev/null
    elif [[ "${emp_id}" == "${EMP2_ID}" ]]; then
      bootstrap_post "/employee-contracts" \
        "{\"employeeId\":${emp_id},\"employmentType\":\"PART_TIME\",\"salaryType\":\"HOURLY\",\"baseSalary\":80000.00,\"regionId\":${REGION_ID},\"taxCode\":\"MST-DEMO-002\",\"contractStatus\":\"ACTIVE\",\"startDate\":\"${BUSINESS_DATE}\"}" >/dev/null
    else
      bootstrap_post "/employee-contracts" \
        "{\"employeeId\":${emp_id},\"employmentType\":\"FULL_TIME\",\"salaryType\":\"MONTHLY\",\"baseSalary\":13500000.00,\"regionId\":${REGION_ID},\"taxCode\":\"MST-DEMO-003\",\"contractStatus\":\"ACTIVE\",\"startDate\":\"${BUSINESS_DATE}\"}" >/dev/null
    fi
    log "  Contract for emp_id=${emp_id} created"
  else
    log "  Contract for emp_id=${emp_id} already exists"
  fi
done

# Assignments (in fern_operational.hr)
for emp_id in "${EMP1_ID}" "${EMP2_ID}" "${EMP3_ID}"; do
  cnt="$(employee_has_active_assignment "${emp_id}")"
  if [[ "${cnt}" == "0" || -z "${cnt}" ]]; then
    local_pos="Barista"
    [[ "${emp_id}" == "${EMP2_ID}" ]] && local_pos="Cashier"
    user_post "${HR_TOKEN}" "/employee-assignments" \
      "{\"employeeId\":${emp_id},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"positionTitle\":\"${local_pos}\",\"startDate\":\"${BUSINESS_DATE}\",\"primaryAssignment\":true,\"status\":\"ACTIVE\"}" >/dev/null
    log "  Assignment for emp_id=${emp_id} created"
  else
    log "  Assignment for emp_id=${emp_id} already exists"
  fi
done

# Shift + attendance (only once)
SHIFT_SCHED_ID="$(find_shift_schedule_id)"
if [[ -z "${SHIFT_SCHED_ID}" ]]; then
  SCHED_RESP="$(user_post "${OUTLET_MGR_TOKEN}" "/shift-schedules" \
    "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"shiftDate\":\"${BUSINESS_DATE}\",\"shiftName\":\"Morning Demo Shift\",\"startTime\":\"07:00:00\",\"endTime\":\"15:00:00\",\"status\":\"SCHEDULED\"}")"
  SHIFT_SCHED_ID="$(json_id "${SCHED_RESP}")"
  log "Shift schedule → id=${SHIFT_SCHED_ID}"
else
  log "Shift schedule already exists → id=${SHIFT_SCHED_ID}"
fi

SHIFT_ASSIGN_ID="$(find_shift_assignment_id "${SHIFT_SCHED_ID}" "${EMP1_ID}")"
if [[ -z "${SHIFT_ASSIGN_ID}" ]]; then
  SA_RESP="$(user_post "${OUTLET_MGR_TOKEN}" "/shift-assignments" \
    "{\"shiftScheduleId\":${SHIFT_SCHED_ID},\"employeeId\":${EMP1_ID},\"assignedRole\":\"STAFF\",\"note\":\"Demo shift assignment\"}")"
  SHIFT_ASSIGN_ID="$(json_id "${SA_RESP}")"
  log "Shift assignment → id=${SHIFT_ASSIGN_ID}"

  user_post_ok409 "${OUTLET_MGR_TOKEN}" "/attendance-events" \
    "{\"employeeId\":${EMP1_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"shiftAssignmentId\":${SHIFT_ASSIGN_ID},\"eventType\":\"CLOCK_IN\",\"eventTime\":\"${BUSINESS_DATE}T07:02:00Z\",\"sourceSystem\":\"DEMO_SEED\"}" \
    "Idempotency-Key: demo-clock-in-${SHIFT_ASSIGN_ID}" >/dev/null
  user_post_ok409 "${OUTLET_MGR_TOKEN}" "/attendance-events" \
    "{\"employeeId\":${EMP1_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"shiftAssignmentId\":${SHIFT_ASSIGN_ID},\"eventType\":\"CLOCK_OUT\",\"eventTime\":\"${BUSINESS_DATE}T15:10:00Z\",\"sourceSystem\":\"DEMO_SEED\"}" \
    "Idempotency-Key: demo-clock-out-${SHIFT_ASSIGN_ID}" >/dev/null
  APPROVE_RESP="$(user_post "${OUTLET_MGR_TOKEN}" "/attendance-approvals/${SHIFT_ASSIGN_ID}/approve" \
    '{"comments":"Demo seed attendance approved"}')"
  log "Attendance approved → status=$(json_field status "${APPROVE_RESP}")"
else
  log "Shift assignment already exists → id=${SHIFT_ASSIGN_ID}"
fi

# ===========================================================================
# 7. FINANCE / PAYROLL
# ===========================================================================
log ""
log "=== [7/9] FINANCE / PAYROLL ==="

PAYROLL_PERIOD_ID="$(find_payroll_period_id_by_name "${REGION_ID}" "Demo Payroll March 2026")"
PAYROLL_RUN1_ID=""
if [[ -z "${PAYROLL_PERIOD_ID}" ]]; then
  PP1_RESP="$(user_post "${HR_TOKEN}" "/payroll-periods" \
    "{\"regionId\":${REGION_ID},\"name\":\"Demo Payroll March 2026\",\"startDate\":\"${PAYROLL_MONTH_START}\",\"endDate\":\"${PAYROLL_MONTH_END}\",\"payDate\":\"${PAYROLL_MONTH_END}\"}")"
  PAYROLL_PERIOD_ID="$(json_id "${PP1_RESP}")"
  log "Payroll period (March) → id=${PAYROLL_PERIOD_ID}"

  PR1_RESP="$(user_post "${HR_TOKEN}" "/payroll-runs" \
    "{\"payrollPeriodId\":${PAYROLL_PERIOD_ID},\"runDate\":\"${PAYROLL_MONTH_END}\",\"note\":\"Demo paid run\"}")"
  PAYROLL_RUN1_ID="$(json_id "${PR1_RESP}")"
  user_post "${HR_TOKEN}" "/payroll-runs/${PAYROLL_RUN1_ID}/submit" '{"note":"Demo submit"}' >/dev/null
  # approve + mark-paid: use bootstrap token (finance scope may not cover region)
  refresh_bootstrap_token
  bootstrap_request POST "/payroll-runs/${PAYROLL_RUN1_ID}/approve" '{"note":"Demo approve"}' >/dev/null 2>&1
  bootstrap_request POST "/payroll-runs/${PAYROLL_RUN1_ID}/mark-paid" '{"paymentReference":"PAY-DEMO-2026-03","note":"Demo payroll settled"}' >/dev/null 2>&1
  log "Payroll run 1 → id=${PAYROLL_RUN1_ID} (PAID)"
else
  PAYROLL_RUN1_ID="$(find_payroll_run_id "${REGION_ID}" "${PAYROLL_PERIOD_ID}" "PAID")"
  log "Payroll period (March) already exists → id=${PAYROLL_PERIOD_ID}, run=${PAYROLL_RUN1_ID}"
fi

PAYROLL_PERIOD2_ID="$(find_payroll_period_id_by_name "${REGION_ID}" "Demo Payroll April 2026")"
PAYROLL_RUN2_ID=""
if [[ -z "${PAYROLL_PERIOD2_ID}" ]]; then
  PP2_RESP="$(user_post "${HR_TOKEN}" "/payroll-periods" \
    "{\"regionId\":${REGION_ID},\"name\":\"Demo Payroll April 2026\",\"startDate\":\"2026-04-02\",\"endDate\":\"2026-04-30\",\"payDate\":\"2026-04-30\"}")"
  PAYROLL_PERIOD2_ID="$(json_id "${PP2_RESP}")"
  PR2_RESP="$(user_post "${HR_TOKEN}" "/payroll-runs" \
    "{\"payrollPeriodId\":${PAYROLL_PERIOD2_ID},\"runDate\":\"2026-04-30\",\"note\":\"Demo awaiting approval run\"}")"
  PAYROLL_RUN2_ID="$(json_id "${PR2_RESP}")"
  user_post "${HR_TOKEN}" "/payroll-runs/${PAYROLL_RUN2_ID}/submit" \
    '{"note":"Demo submit — awaiting finance approval"}' >/dev/null
  log "Payroll run 2 → id=${PAYROLL_RUN2_ID} (SUBMITTED)"
else
  PAYROLL_RUN2_ID="$(find_payroll_run_id "${REGION_ID}" "${PAYROLL_PERIOD2_ID}")"
  log "Payroll period (April) already exists → id=${PAYROLL_PERIOD2_ID}, run=${PAYROLL_RUN2_ID}"
fi

# ===========================================================================
# 8. REPORTS — Export job
# ===========================================================================
log ""
log "=== [8/9] REPORTS ==="
EXPORT_JOB_ID=""
EXPORT_RESP="$(user_post_ok409 "${FINANCE_TOKEN}" "/reports/payroll/export" \
  "{\"regionId\":${REGION_ID},\"fromDate\":\"${PAYROLL_MONTH_START}\",\"toDate\":\"${PAYROLL_MONTH_END}\"}" \
  "Idempotency-Key: demo-payroll-export-seed-${PAYROLL_PERIOD_ID:-0}")"
EXPORT_JOB_ID="$(json_field exportJobId "${EXPORT_RESP}" 2>/dev/null || echo "n/a")"
log "Export job → id=${EXPORT_JOB_ID}"

# ===========================================================================
# 9. SUMMARY
# ===========================================================================
log ""
cat <<EOF
======================================================================
FERN DEMO SEED COMPLETE
======================================================================

Organization
  Region:  DEMO-HCM       id=${REGION_ID}
  Outlet:  DEMO-HCM-DIST1 id=${OUTLET_D1}
  Outlet:  DEMO-HCM-DIST3 id=${OUTLET_D3}

Catalog
  COFFEE-ARABICA id=${ING_COFFEE} | SUGAR-WHITE id=${ING_SUGAR} | MILK-FULL id=${ING_MILK}
  LATTE-HOT id=${PROD_LATTE} | AMERICANO-ICE id=${PROD_AMERICANO}

Procurement
  Supplier DEMO-SUP-001 id=${SUPPLIER_ID}
  PO ISSUED   id=${PO1_ID} | PO SUBMITTED id=${PO2_ID}
  GR POSTED   id=${GR_ID}  | Invoice APPROVED id=${INV_ID}
  Payment     id=${PAY_ID}

HR
  Employees: ${EMP1_ID}, ${EMP2_ID}, ${EMP3_ID}
  Shift schedule ${SHIFT_SCHED_ID} | Assignment (APPROVED) ${SHIFT_ASSIGN_ID}

Finance / Payroll
  March period ${PAYROLL_PERIOD_ID} | Run PAID ${PAYROLL_RUN1_ID}
  April period ${PAYROLL_PERIOD2_ID} | Run SUBMITTED ${PAYROLL_RUN2_ID}

Reports
  Export job ${EXPORT_JOB_ID}

----------------------------------------------------------------------
DEMO ACCOUNTS  (all password: ${DEMO_PASSWORD})
----------------------------------------------------------------------
  demo-cashier       staff               DIST1(${OUTLET_D1})
  demo-outlet-mgr    outlet_manager      DIST1(${OUTLET_D1}) only
  demo-region-mgr    outlet_manager      HCM region(${REGION_ID})
  demo-reg-finance   regional_finance    HCM region(${REGION_ID})
  demo-hr            hr                  SYSTEM
  demo-finance       finance             SYSTEM
  demo-product-mgr   product_manager     SYSTEM
  demo-sysadmin      system_admin        SYSTEM
  demo-audit         system_admin(*)     SYSTEM
  demo-readonly      regional_finance    DIST3(${OUTLET_D3}) only

  (*) fallback until a dedicated audit-only role is published in IAM.

Frontend: http://localhost:3000
======================================================================
EOF
