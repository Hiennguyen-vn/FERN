#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "${SCRIPT_DIR}/common.sh"

require_cmd curl
require_cmd jq

OUTLET_COUNT="${LOAD_OUTLET_COUNT:-60}"
TERMINALS_PER_OUTLET="${LOAD_TERMINALS_PER_OUTLET:-4}"
REGION_CODE="LOAD-REGION-${LOAD_RUN_ID}"
REGION_NAME="Load Region ${LOAD_RUN_ID}"
ROLE_CODE="load_role_${LOAD_RUN_ID}"
INGREDIENT_CATEGORY_CODE="LIC${LOAD_RUN_ID}"
PRODUCT_CATEGORY_CODE="LPC${LOAD_RUN_ID}"
BASE_UOM_CODE="LG${LOAD_RUN_ID}"
YIELD_UOM_CODE="LC${LOAD_RUN_ID}"
INGREDIENT_CODE="LING${LOAD_RUN_ID}"
PRODUCT_CODE="LPROD${LOAD_RUN_ID}"
RECIPE_CODE="LRCP${LOAD_RUN_ID}"
SUPPLIER_CODE="LSUP${LOAD_RUN_ID}"
BUSINESS_DATE="${LOAD_BUSINESS_DATE:-$(date +%F)}"

mkdir -p "$(dirname "${LOAD_SEED_FILE}")"

TOKEN="$(bootstrap_token)"

log "Creating load-test region"
REGION_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/regions" "{\"code\":\"${REGION_CODE}\",\"parentRegionId\":1,\"currencyCode\":\"VND\",\"name\":\"${REGION_NAME}\",\"timezoneName\":\"Asia/Ho_Chi_Minh\"}" | jq -r '.id')"

log "Creating shared catalog fixtures"
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/ingredient-categories" "{\"code\":\"${INGREDIENT_CATEGORY_CODE}\",\"name\":\"Load Ingredients ${LOAD_RUN_ID}\",\"description\":\"Load harness ingredients\",\"active\":true}" >/dev/null
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/product-categories" "{\"code\":\"${PRODUCT_CATEGORY_CODE}\",\"name\":\"Load Products ${LOAD_RUN_ID}\",\"description\":\"Load harness products\",\"active\":true}" >/dev/null
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/units-of-measure" "{\"code\":\"${BASE_UOM_CODE}\",\"name\":\"Load Gram ${LOAD_RUN_ID}\",\"symbol\":\"g\"}" >/dev/null
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/units-of-measure" "{\"code\":\"${YIELD_UOM_CODE}\",\"name\":\"Load Cup ${LOAD_RUN_ID}\",\"symbol\":\"cup\"}" >/dev/null

INGREDIENT_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/ingredients" "{\"code\":\"${INGREDIENT_CODE}\",\"name\":\"Load Coffee ${LOAD_RUN_ID}\",\"categoryCode\":\"${INGREDIENT_CATEGORY_CODE}\",\"baseUomCode\":\"${BASE_UOM_CODE}\",\"status\":\"ACTIVE\"}" | jq -r '.id')"
PRODUCT_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/products" "{\"code\":\"${PRODUCT_CODE}\",\"name\":\"Load Latte ${LOAD_RUN_ID}\",\"categoryCode\":\"${PRODUCT_CATEGORY_CODE}\",\"status\":\"ACTIVE\",\"description\":\"Load test product\"}" | jq -r '.id')"
RECIPE_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/recipes" "{\"productId\":${PRODUCT_ID},\"recipeCode\":\"${RECIPE_CODE}\",\"description\":\"Load recipe\"}" | jq -r '.id')"
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/recipe-versions" "{\"recipeId\":${RECIPE_ID},\"versionNo\":\"v1\",\"yieldQty\":1.0000,\"yieldUomCode\":\"${YIELD_UOM_CODE}\",\"status\":\"ACTIVE\",\"effectiveFrom\":\"${BUSINESS_DATE}\",\"ingredients\":[{\"ingredientId\":${INGREDIENT_ID},\"uomCode\":\"${BASE_UOM_CODE}\",\"qty\":10.0000,\"sortOrder\":1}]}" >/dev/null
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/tax-rates" "{\"productId\":${PRODUCT_ID},\"taxPercent\":10.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" >/dev/null
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/product-prices" "{\"productId\":${PRODUCT_ID},\"scopeType\":\"GLOBAL\",\"priceType\":\"RETAIL\",\"currencyCode\":\"VND\",\"priceValue\":55000.00,\"effectiveFrom\":\"${BUSINESS_DATE}\"}" >/dev/null

log "Creating supplier and roles"
SUPPLIER_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/suppliers" "{\"supplierCode\":\"${SUPPLIER_CODE}\",\"name\":\"Load Supplier ${LOAD_RUN_ID}\",\"email\":\"load-${LOAD_RUN_ID}@example.com\",\"phone\":\"0900000000\",\"address\":\"Load staging address\",\"defaultRegionId\":${REGION_ID},\"status\":\"INACTIVE\"}" | jq -r '.id')"
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/suppliers/${SUPPLIER_ID}/activate" "" >/dev/null
api_json "${TOKEN}" POST "${LOAD_BASE_URL}/roles" "{\"code\":\"${ROLE_CODE}\",\"name\":\"Load Harness Role\",\"permissionCodes\":[\"org.region.read\",\"org.outlet.read\"]}" >/dev/null

OUTLETS_JSON='[]'

for outlet_index in $(seq 1 "${OUTLET_COUNT}"); do
  OUTLET_CODE="LOAD-OUTLET-${LOAD_RUN_ID}-${outlet_index}"
  OUTLET_NAME="Load Outlet ${LOAD_RUN_ID} ${outlet_index}"
  log "Creating outlet ${outlet_index}/${OUTLET_COUNT}"
  OUTLET_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/outlets" "{\"regionId\":${REGION_ID},\"code\":\"${OUTLET_CODE}\",\"name\":\"${OUTLET_NAME}\",\"status\":\"ACTIVE\",\"openedAt\":\"${BUSINESS_DATE}\"}" | jq -r '.id')"
  api_json "${TOKEN}" PUT "${LOAD_BASE_URL}/product-availability" "{\"productId\":${PRODUCT_ID},\"outletId\":${OUTLET_ID},\"available\":true}" >/dev/null

  USERS_JSON='[]'
  for terminal_index in $(seq 1 "${TERMINALS_PER_OUTLET}"); do
    USERNAME="load-user-${LOAD_RUN_ID}-${outlet_index}-${terminal_index}"
    USER_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/users" "{\"username\":\"${USERNAME}\",\"password\":\"${LOAD_USER_PASSWORD}\",\"fullName\":\"Load User ${outlet_index}-${terminal_index}\",\"status\":\"ACTIVE\"}" | jq -r '.id')"
    api_json "${TOKEN}" POST "${LOAD_BASE_URL}/users/${USER_ID}/roles" "{\"roleCodes\":[\"${ROLE_CODE}\",\"outlet_manager\",\"regional_finance\",\"finance\",\"hr\"]}" >/dev/null
    api_json "${TOKEN}" POST "${LOAD_BASE_URL}/users/${USER_ID}/scopes" "{\"regionIds\":[${REGION_ID}],\"outletIds\":[${OUTLET_ID}]}" >/dev/null
    USERS_JSON="$(jq -c \
      --arg username "${USERNAME}" \
      --arg password "${LOAD_USER_PASSWORD}" \
      --argjson user_id "${USER_ID}" \
      '. + [{"user_id": $user_id, "username": $username, "password": $password}]' \
      <<< "${USERS_JSON}")"
  done

  ADJUSTMENT_ID="$(api_json "${TOKEN}" POST "${LOAD_BASE_URL}/stock-adjustments" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"ingredientId\":${INGREDIENT_ID},\"adjustmentDirection\":\"IN\",\"qty\":100.0000,\"businessDate\":\"${BUSINESS_DATE}\",\"reason\":\"LOAD_SEED\",\"note\":\"Load seed stock\"}" | jq -r '.id')"
  api_json "${TOKEN}" POST "${LOAD_BASE_URL}/stock-adjustments/${ADJUSTMENT_ID}/post" "" "Idempotency-Key: load-seed-adjustment-${LOAD_RUN_ID}-${outlet_index}" >/dev/null

  OUTLETS_JSON="$(jq -c \
    --argjson outlet_id "${OUTLET_ID}" \
    --argjson region_id "${REGION_ID}" \
    --arg code "${OUTLET_CODE}" \
    --arg name "${OUTLET_NAME}" \
    --argjson terminals "${TERMINALS_PER_OUTLET}" \
    --argjson users "${USERS_JSON}" \
    '. + [{"outlet_id": $outlet_id, "region_id": $region_id, "code": $code, "name": $name, "currency_code": "VND", "terminals": $terminals, "users": $users}]' \
    <<< "${OUTLETS_JSON}")"
done

jq -n \
  --arg run_id "${LOAD_RUN_ID}" \
  --arg business_date "${BUSINESS_DATE}" \
  --argjson region_id "${REGION_ID}" \
  --arg region_code "${REGION_CODE}" \
  --argjson ingredient_id "${INGREDIENT_ID}" \
  --argjson product_id "${PRODUCT_ID}" \
  --argjson supplier_id "${SUPPLIER_ID}" \
  --arg base_uom_code "${BASE_UOM_CODE}" \
  --arg yield_uom_code "${YIELD_UOM_CODE}" \
  --argjson outlets "${OUTLETS_JSON}" \
  '{
    generated: true,
    run_id: $run_id,
    business_date: $business_date,
    region: {
      id: $region_id,
      code: $region_code
    },
    catalog: {
      ingredient_id: $ingredient_id,
      product_id: $product_id,
      supplier_id: $supplier_id,
      base_uom_code: $base_uom_code,
      yield_uom_code: $yield_uom_code,
      expected_total_amount: 60500
    },
    outlets: $outlets
  }' > "${LOAD_SEED_FILE}"

log "Generated fixture file at ${LOAD_SEED_FILE}"
