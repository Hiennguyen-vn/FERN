#!/usr/bin/env bash
# =============================================================================
# seed-demo.sh — FERN Full-System Demo Seed
# =============================================================================
# Creates a realistic, persona-driven staging dataset via live API calls.
# Idempotent: resolves existing entity IDs via psql when API 409 conflicts occur.
#
# Prerequisites:
#   1. Full stack running (services + infrastructure containers)
#   2. Migrations applied
#   3. docker, curl, python3 available
#
# Usage:
#   ./scripts/seed-demo.sh
#
# Environment overrides:
#   FERN_BASE_URL=http://localhost:8080
#   FERN_IAM_BASE_URL=http://localhost:8081
#   FERN_POSTGRES_CONTAINER=fern-postgres
#   DEMO_PASSWORD=Demo123!
#   BUSINESS_DATE=2026-04-01
# =============================================================================

set -euo pipefail

FERN_BASE_URL="${FERN_BASE_URL:-http://localhost:8080}"
FERN_IAM_BASE_URL="${FERN_IAM_BASE_URL:-http://localhost:8081}"
FERN_POSTGRES_CONTAINER="${FERN_POSTGRES_CONTAINER:-fern-postgres}"
FERN_DB_USERNAME="${FERN_DB_USERNAME:-fern}"
FERN_MASTER_DB="${FERN_MASTER_DB:-fern_master}"
FERN_OPERATIONAL_DB="${FERN_OPERATIONAL_DB:-fern_operational}"
BOOTSTRAP_USERNAME="${BOOTSTRAP_USERNAME:-bootstrap-admin}"
BOOTSTRAP_PASSWORD="${BOOTSTRAP_PASSWORD:-Admin123!}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo123!}"
BUSINESS_DATE="${BUSINESS_DATE:-$(date +%F)}"
PAYROLL_MONTH_START="${PAYROLL_MONTH_START:-$(date +%Y-%m-01)}"
PAYROLL_MONTH_END="${PAYROLL_MONTH_END:-${BUSINESS_DATE}}"

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
require_cmd docker

curl -fsS "${FERN_IAM_BASE_URL}/actuator/health" >/dev/null 2>&1 \
  || fail "IAM not reachable at ${FERN_IAM_BASE_URL}. Start the stack first."
curl -fsS "${FERN_BASE_URL}/actuator/health" >/dev/null 2>&1 \
  || fail "Gateway not reachable at ${FERN_BASE_URL}. Start the stack first."

log "======================================================================"
log "FERN Demo Seed — ${BUSINESS_DATE}"
log "Gateway: ${FERN_BASE_URL}  IAM: ${FERN_IAM_BASE_URL}"
log "======================================================================"

# ---------------------------------------------------------------------------
# Database helpers — used for idempotent ID resolution (list endpoints are
# unreliable through the gateway for org/iam entities)
# ---------------------------------------------------------------------------
psql_scalar() {
  local db="$1"
  local sql="$2"
  docker exec "${FERN_POSTGRES_CONTAINER}" psql -U "${FERN_DB_USERNAME}" -d "${db}" \
    -Atqc "${sql}" 2>/dev/null | tr -d '[:space:]'
}

db_region_id()    { psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM org.region WHERE code='$1' LIMIT 1"; }
db_outlet_id()    { psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM org.outlet WHERE code='$1' LIMIT 1"; }
db_user_id()      { psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM iam.user_account WHERE username='$1' LIMIT 1"; }
db_ingredient_id(){ psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM catalog.ingredient WHERE code='$1' LIMIT 1"; }
db_uom_exists()   { psql_scalar "${FERN_MASTER_DB}" "SELECT COUNT(*) FROM catalog.unit_of_measure WHERE code='$1'"; }
db_product_id()   { psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM catalog.product WHERE code='$1' LIMIT 1"; }
db_recipe_id()    { psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM catalog.recipe WHERE recipe_code='$1' LIMIT 1"; }
db_supplier_id()  { psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM procurement_master.supplier WHERE supplier_code='$1' LIMIT 1"; }
db_employee_id()  { psql_scalar "${FERN_MASTER_DB}" "SELECT id FROM hr_master.employee_profile WHERE employee_code='$1' LIMIT 1"; }

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
  # Always re-fetch — token TTL is 15 min and seeds can run longer
  local resp
  resp="$(http_ok POST "${FERN_IAM_BASE_URL}/auth/login" \
    "{\"username\":\"${BOOTSTRAP_USERNAME}\",\"password\":\"${BOOTSTRAP_PASSWORD}\"}" "")"
  BOOTSTRAP_TOKEN="$(json_field accessToken "${resp}")"
}

# Create-or-resolve: attempts POST, on 409 or 5xx with existing DB row, resolves ID via psql
create_or_resolve() {
  local db_func="$1"; local code="$2"; local method="$3"; local path="$4"; local body="$5"
  shift 5 || true
  refresh_bootstrap_token
  perform_request "${method}" "${FERN_BASE_URL}${path}" "${body}" "Bearer ${BOOTSTRAP_TOKEN}" "$@"
  if [[ "${HTTP_STATUS}" == "409" || "${HTTP_STATUS}" -ge 500 ]]; then
    local existing_id
    existing_id="$(${db_func} "${code}")"
    if [[ -n "${existing_id}" ]]; then
      printf '%s' "${existing_id}"
      return
    fi
    fail "HTTP ${HTTP_STATUS} for ${method} ${path} and no existing entity in DB: ${HTTP_BODY}"
  fi
  [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]] \
    || fail "HTTP ${HTTP_STATUS} for ${method} ${path}: ${HTTP_BODY}"
  json_id "${HTTP_BODY}"
}

# Simpler: POST with bootstrap token, return full body
bootstrap_post() {
  local path="$1"; local body="$2"
  shift 2 || true
  refresh_bootstrap_token
  http_ok POST "${FERN_BASE_URL}${path}" "${body}" "Bearer ${BOOTSTRAP_TOKEN}" "$@"
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
  local resp
  resp="$(http_ok POST "${FERN_IAM_BASE_URL}/auth/login" \
    "{\"username\":\"${username}\",\"password\":\"${password}\"}" "")"
  json_field accessToken "${resp}"
}

# ===========================================================================
# 1. ORGANIZATION
# ===========================================================================
log ""
log "=== [1/9] ORGANIZATION ==="

REGION_ID="$(create_or_resolve db_region_id DEMO-HCM POST "/regions" \
  "{\"code\":\"DEMO-HCM\",\"parentRegionId\":1,\"currencyCode\":\"VND\",\"name\":\"Ho Chi Minh City Demo\",\"timezoneName\":\"Asia/Ho_Chi_Minh\"}")"
log "Region DEMO-HCM → id=${REGION_ID}"

OUTLET_D1="$(create_or_resolve db_outlet_id DEMO-HCM-DIST1 POST "/outlets" \
  "{\"regionId\":${REGION_ID},\"code\":\"DEMO-HCM-DIST1\",\"name\":\"District 1 Demo Outlet\",\"status\":\"ACTIVE\",\"openedAt\":\"${BUSINESS_DATE}\"}")"
log "Outlet DEMO-HCM-DIST1 → id=${OUTLET_D1}"

OUTLET_D3="$(create_or_resolve db_outlet_id DEMO-HCM-DIST3 POST "/outlets" \
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
  perform_request POST "${FERN_BASE_URL}/users" \
    "{\"username\":\"${username}\",\"password\":\"${DEMO_PASSWORD}\",\"fullName\":\"${full_name}\",\"status\":\"ACTIVE\"}" \
    "Bearer ${BOOTSTRAP_TOKEN}"

  local user_id
  if [[ "${HTTP_STATUS}" == "409" ]]; then
    user_id="$(db_user_id "${username}")"
    [[ -n "${user_id}" ]] || fail "409 for user ${username} but not in DB"
    log "  ${username} already exists → id=${user_id}"
  elif [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]; then
    user_id="$(json_id "${HTTP_BODY}")"
    log "  Created ${username} → id=${user_id}"
  else
    fail "Failed to create ${username}: ${HTTP_STATUS} ${HTTP_BODY}"
  fi

  # Assign roles (idempotent — 409 is fine)
  perform_request POST "${FERN_BASE_URL}/users/${user_id}/roles" \
    "{\"roleCodes\":${roles_json}}" "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true

  # Assign scope
  if [[ -n "${scope_json}" ]]; then
    perform_request POST "${FERN_BASE_URL}/users/${user_id}/scopes" \
      "${scope_json}" "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true
  fi

  printf '%s' "${user_id}"
}

CASHIER_ID="$(create_demo_user "demo-cashier" "Demo Cashier" \
  '["staff"]' \
  "{\"regionIds\":[],\"outletIds\":[${OUTLET_D1}]}")"

OUTLET_MGR_ID="$(create_demo_user "demo-outlet-mgr" "Demo Outlet Manager" \
  '["outlet_manager"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[${OUTLET_D1}]}")"

REGION_MGR_ID="$(create_demo_user "demo-region-mgr" "Demo Region Manager" \
  '["outlet_manager","regional_finance"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[]}")"

REG_FINANCE_ID="$(create_demo_user "demo-reg-finance" "Demo Regional Finance" \
  '["regional_finance"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[]}")"

HR_ID="$(create_demo_user "demo-hr" "Demo HR Operator" \
  '["hr"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[]}")"

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
  cnt="$(db_uom_exists "${code}")"
  if [[ "${cnt}" == "0" || -z "${cnt}" ]]; then
    perform_request POST "${FERN_BASE_URL}/units-of-measure" \
      "{\"code\":\"${code}\",\"name\":\"${name}\",\"symbol\":\"${symbol}\"}" \
      "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true
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
perform_request POST "${FERN_BASE_URL}/ingredient-categories" \
  '{"code":"RAW-INGREDIENT","name":"Raw Ingredients","description":"Base raw materials for beverages","active":true}' \
  "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true

# Product categories
perform_request POST "${FERN_BASE_URL}/product-categories" \
  '{"code":"HOT-DRINKS","name":"Hot Drinks","description":"Hot beverage menu","active":true}' \
  "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true
perform_request POST "${FERN_BASE_URL}/product-categories" \
  '{"code":"COLD-DRINKS","name":"Cold Drinks","description":"Iced beverage menu","active":true}' \
  "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true

# Ingredients
ING_COFFEE="$(create_or_resolve db_ingredient_id COFFEE-ARABICA POST "/ingredients" \
  '{"code":"COFFEE-ARABICA","name":"Arabica Coffee Beans","categoryCode":"RAW-INGREDIENT","baseUomCode":"GRAM","status":"ACTIVE"}')"
log "Ingredient COFFEE-ARABICA → id=${ING_COFFEE}"

ING_SUGAR="$(create_or_resolve db_ingredient_id SUGAR-WHITE POST "/ingredients" \
  '{"code":"SUGAR-WHITE","name":"White Sugar","categoryCode":"RAW-INGREDIENT","baseUomCode":"GRAM","status":"ACTIVE"}')"
log "Ingredient SUGAR-WHITE → id=${ING_SUGAR}"

ING_MILK="$(create_or_resolve db_ingredient_id MILK-FULL POST "/ingredients" \
  '{"code":"MILK-FULL","name":"Full Cream Milk","categoryCode":"RAW-INGREDIENT","baseUomCode":"ML","status":"ACTIVE"}')"
log "Ingredient MILK-FULL → id=${ING_MILK}"

# Products
PROD_LATTE="$(create_or_resolve db_product_id LATTE-HOT POST "/products" \
  '{"code":"LATTE-HOT","name":"Hot Latte","categoryCode":"HOT-DRINKS","status":"ACTIVE","description":"Classic hot latte with steamed milk"}')"
log "Product LATTE-HOT → id=${PROD_LATTE}"

PROD_AMERICANO="$(create_or_resolve db_product_id AMERICANO-ICE POST "/products" \
  '{"code":"AMERICANO-ICE","name":"Iced Americano","categoryCode":"COLD-DRINKS","status":"ACTIVE","description":"Chilled americano with ice"}')"
log "Product AMERICANO-ICE → id=${PROD_AMERICANO}"

# Recipes (only create if not existing)
RECIPE_LATTE="$(db_recipe_id RCP-LATTE-V1)"
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

RECIPE_AMERICANO="$(db_recipe_id RCP-AMERICANO-V1)"
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
  perform_request POST "${FERN_BASE_URL}/tax-rates" \
    "{\"productId\":${prod_id},\"taxPercent\":10.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" \
    "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true
done

# Product prices — only post if not existing (409 allowed)
perform_request POST "${FERN_BASE_URL}/product-prices" \
  "{\"productId\":${PROD_LATTE},\"scopeType\":\"GLOBAL\",\"priceType\":\"RETAIL\",\"currencyCode\":\"VND\",\"priceValue\":65000.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" \
  "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true
perform_request POST "${FERN_BASE_URL}/product-prices" \
  "{\"productId\":${PROD_AMERICANO},\"scopeType\":\"GLOBAL\",\"priceType\":\"RETAIL\",\"currencyCode\":\"VND\",\"priceValue\":55000.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" \
  "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true

# Availability — PUT is idempotent by design
for prod_id in "${PROD_LATTE}" "${PROD_AMERICANO}"; do
  for outlet_id in "${OUTLET_D1}" "${OUTLET_D3}"; do
    perform_request PUT "${FERN_BASE_URL}/product-availability" \
      "{\"productId\":${prod_id},\"outletId\":${outlet_id},\"available\":true}" \
      "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true
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
  # Check if already posted (table is stock_adjustment; use note as idempotency marker)
  local cnt
  cnt="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
    "SELECT COUNT(*) FROM inventory.stock_adjustment WHERE outlet_id=${OUTLET_D1} AND ingredient_id=${ingredient_id} AND note='${note}' AND status='POSTED'")"
  if [[ "${cnt}" -ge 1 ]]; then
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
SUPPLIER_ID="$(create_or_resolve db_supplier_id DEMO-SUP-001 POST "/suppliers" \
  "{\"supplierCode\":\"DEMO-SUP-001\",\"name\":\"Fresh Brew Supplies Ltd\",\"email\":\"demo-supplier@freshbrew.vn\",\"phone\":\"0283001234\",\"address\":\"12 Nguyen Hue, District 1, HCMC\",\"defaultRegionId\":${REGION_ID},\"status\":\"INACTIVE\"}" \
  )"
# Activate (use bootstrap token — outlet_manager lacks procurement.supplier.write)
perform_request POST "${FERN_BASE_URL}/suppliers/${SUPPLIER_ID}/activate" "" \
  "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1 || true
log "Supplier DEMO-SUP-001 → id=${SUPPLIER_ID} (ACTIVE)"

# PO-DEMO-001 — ISSUED (terminal, tests ReadonlyBanner)
PO1_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM procurement.purchase_order WHERE note='Demo seed: standard restocking order' LIMIT 1")"
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
  PO1_LINE1_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
    "SELECT id FROM procurement.purchase_order_line WHERE purchase_order_id=${PO1_ID} ORDER BY line_number LIMIT 1")"
  log "PO-DEMO-001 already exists → id=${PO1_ID}"
fi

# PO-DEMO-002 — SUBMITTED (in-queue, tests approval flow)
PO2_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM procurement.purchase_order WHERE note='Demo seed: pending approval restock' LIMIT 1")"
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
GR_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM procurement.goods_receipt WHERE purchase_order_id=${PO1_ID} AND note='Demo seed: received shipment' LIMIT 1")"
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
  GR_LINE1_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
    "SELECT id FROM procurement.goods_receipt_line WHERE goods_receipt_id=${GR_ID} ORDER BY id LIMIT 1")"
  log "GR-DEMO already exists → id=${GR_ID}"
fi

# Supplier invoice
INV_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM procurement.supplier_invoice WHERE invoice_number='INV-DEMO-2026-001' LIMIT 1")"
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
PAY_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM procurement.supplier_payment WHERE idempotency_key='demo-supplier-payment-${INV_ID}' LIMIT 1")"
if [[ -z "${PAY_ID}" ]]; then
  # Supplier payment requires finance role (procurement.payment.record)
  PAY_RESP="$(user_post_ok409 "${FINANCE_TOKEN}" "/supplier-payments" \
    "{\"supplierId\":${SUPPLIER_ID},\"currencyCode\":\"VND\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":385000.00,\"paymentTime\":\"${BUSINESS_DATE}T14:00:00Z\",\"transactionRef\":\"TXN-DEMO-2026-001\",\"invoiceAllocations\":[{\"supplierInvoiceId\":${INV_ID},\"allocatedAmount\":385000.00,\"note\":\"Full settlement demo\"}]}" \
    "Idempotency-Key: demo-supplier-payment-${INV_ID}")"
  PAY_ID="$(json_id "${PAY_RESP}" 2>/dev/null || psql_scalar "${FERN_OPERATIONAL_DB}" "SELECT id FROM procurement.supplier_payment WHERE transaction_ref='TXN-DEMO-2026-001' LIMIT 1")"
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
  existing_id="$(db_employee_id "${code}")"
  if [[ -n "${existing_id}" ]]; then
    log "  Employee ${code} already exists → id=${existing_id}"
    printf '%s' "${existing_id}"
    return
  fi
  local resp
  resp="$(bootstrap_post "/employees" \
    "{\"employeeCode\":\"${code}\",\"fullName\":\"${name}\",\"status\":\"ACTIVE\",\"hiredAt\":\"${BUSINESS_DATE}\",\"dob\":\"${dob}\",\"gender\":\"${gender}\",\"email\":\"${email}\",\"phone\":\"${phone}\"}")"
  local eid; eid="$(json_id "${resp}")"
  log "  Employee ${code} → id=${eid}"
  printf '%s' "${eid}"
}

EMP1_ID="$(create_employee "EMP-DEMO-001" "Nguyen Thi Lan" "1995-06-15" "FEMALE" "lan.nguyen@demo.fern" "0901234567")"
EMP2_ID="$(create_employee "EMP-DEMO-002" "Tran Van Minh" "1998-03-22" "MALE" "minh.tran@demo.fern" "0907654321")"
EMP3_ID="$(create_employee "EMP-DEMO-003" "Le Thi Mai" "2000-11-08" "FEMALE" "mai.le@demo.fern" "0912345678")"

# Contracts (in fern_master.hr_master)
for emp_id in "${EMP1_ID}" "${EMP2_ID}" "${EMP3_ID}"; do
  cnt="$(psql_scalar "${FERN_MASTER_DB}" \
    "SELECT COUNT(*) FROM hr_master.employee_contract WHERE employee_id=${emp_id} AND contract_status='ACTIVE'")"
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
  cnt="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
    "SELECT COUNT(*) FROM hr.employee_assignment WHERE employee_id=${emp_id} AND outlet_id=${OUTLET_D1} AND status='ACTIVE'")"
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
SHIFT_SCHED_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM hr.shift_schedule WHERE outlet_id=${OUTLET_D1} AND shift_name='Morning Demo Shift' LIMIT 1")"
if [[ -z "${SHIFT_SCHED_ID}" ]]; then
  SCHED_RESP="$(user_post "${OUTLET_MGR_TOKEN}" "/shift-schedules" \
    "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_D1},\"shiftDate\":\"${BUSINESS_DATE}\",\"shiftName\":\"Morning Demo Shift\",\"startTime\":\"07:00:00\",\"endTime\":\"15:00:00\",\"status\":\"SCHEDULED\"}")"
  SHIFT_SCHED_ID="$(json_id "${SCHED_RESP}")"
  log "Shift schedule → id=${SHIFT_SCHED_ID}"
else
  log "Shift schedule already exists → id=${SHIFT_SCHED_ID}"
fi

SHIFT_ASSIGN_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM hr.shift_assignment WHERE shift_schedule_id=${SHIFT_SCHED_ID} AND employee_id=${EMP1_ID} LIMIT 1")"
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

PAYROLL_PERIOD_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM finance.payroll_period WHERE name='Demo Payroll March 2026' AND region_id=${REGION_ID} LIMIT 1")"
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
  perform_request POST "${FERN_BASE_URL}/payroll-runs/${PAYROLL_RUN1_ID}/approve" '{"note":"Demo approve"}' "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1
  perform_request POST "${FERN_BASE_URL}/payroll-runs/${PAYROLL_RUN1_ID}/mark-paid" '{"paymentReference":"PAY-DEMO-2026-03","note":"Demo payroll settled"}' "Bearer ${BOOTSTRAP_TOKEN}" >/dev/null 2>&1
  log "Payroll run 1 → id=${PAYROLL_RUN1_ID} (PAID)"
else
  PAYROLL_RUN1_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
    "SELECT id FROM finance.payroll_run WHERE payroll_period_id=${PAYROLL_PERIOD_ID} AND status='PAID' LIMIT 1")"
  log "Payroll period (March) already exists → id=${PAYROLL_PERIOD_ID}, run=${PAYROLL_RUN1_ID}"
fi

PAYROLL_PERIOD2_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
  "SELECT id FROM finance.payroll_period WHERE name='Demo Payroll April 2026' AND region_id=${REGION_ID} LIMIT 1")"
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
  PAYROLL_RUN2_ID="$(psql_scalar "${FERN_OPERATIONAL_DB}" \
    "SELECT id FROM finance.payroll_run WHERE payroll_period_id=${PAYROLL_PERIOD2_ID} LIMIT 1")"
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
  demo-outlet-mgr    outlet_manager      DIST1(${OUTLET_D1}) + region
  demo-region-mgr    outlet_manager      HCM region(${REGION_ID})
                     regional_finance
  demo-reg-finance   regional_finance    HCM region(${REGION_ID})
  demo-hr            hr                  HCM region(${REGION_ID})
  demo-finance       finance             SYSTEM
  demo-product-mgr   product_manager     SYSTEM
  demo-sysadmin      system_admin        SYSTEM
  demo-audit         system_admin        SYSTEM
  demo-readonly      regional_finance    DIST3(${OUTLET_D3}) only

Frontend: http://localhost:3000
======================================================================
EOF
