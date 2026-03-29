if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_setup_shared() {
  scenario_start "setup_shared"

  REGION_ID="$(create_region "${SMOKE_REGION_CODE}" "Smoke Region ${RUN_ID}")"
  OUTLET_ID="$(create_outlet "${REGION_ID}" "${SMOKE_OUTLET_CODE}" "Smoke Outlet ${RUN_ID}")"
  OUTSIDER_REGION_ID="$(create_region "${SMOKE_OUTSIDER_REGION_CODE}" "Smoke Outsider Region ${RUN_ID}")"
  OUTSIDER_OUTLET_ID="$(create_outlet "${OUTSIDER_REGION_ID}" "${SMOKE_OUTSIDER_OUTLET_CODE}" "Smoke Outsider Outlet ${RUN_ID}")"

  bootstrap_json POST "${FERN_BASE_URL}/ingredient-categories" "{\"code\":\"${SMOKE_INGREDIENT_CATEGORY_CODE}\",\"name\":\"Smoke Ingredients ${RUN_ID}\",\"description\":\"Smoke ingredient category\",\"active\":true}" >/dev/null
  bootstrap_json POST "${FERN_BASE_URL}/product-categories" "{\"code\":\"${SMOKE_PRODUCT_CATEGORY_CODE}\",\"name\":\"Smoke Products ${RUN_ID}\",\"description\":\"Smoke product category\",\"active\":true}" >/dev/null
  bootstrap_json POST "${FERN_BASE_URL}/units-of-measure" "{\"code\":\"${SMOKE_BASE_UOM_CODE}\",\"name\":\"Smoke Gram ${RUN_ID}\",\"symbol\":\"g\"}" >/dev/null
  bootstrap_json POST "${FERN_BASE_URL}/units-of-measure" "{\"code\":\"${SMOKE_YIELD_UOM_CODE}\",\"name\":\"Smoke Cup ${RUN_ID}\",\"symbol\":\"cup\"}" >/dev/null

  local ingredient_response
  local product_response
  local recipe_response
  local recipe_version_response
  ingredient_response="$(bootstrap_json POST "${FERN_BASE_URL}/ingredients" "{\"code\":\"${SMOKE_INGREDIENT_CODE}\",\"name\":\"Smoke Coffee ${RUN_ID}\",\"categoryCode\":\"${SMOKE_INGREDIENT_CATEGORY_CODE}\",\"baseUomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"status\":\"ACTIVE\"}")"
  INGREDIENT_ID="$(printf '%s' "${ingredient_response}" | json_get id)"

  product_response="$(bootstrap_json POST "${FERN_BASE_URL}/products" "{\"code\":\"${SMOKE_PRODUCT_CODE}\",\"name\":\"Smoke Latte ${RUN_ID}\",\"categoryCode\":\"${SMOKE_PRODUCT_CATEGORY_CODE}\",\"status\":\"ACTIVE\",\"description\":\"Smoke test product\"}")"
  PRODUCT_ID="$(printf '%s' "${product_response}" | json_get id)"

  recipe_response="$(bootstrap_json POST "${FERN_BASE_URL}/recipes" "{\"productId\":${PRODUCT_ID},\"recipeCode\":\"${SMOKE_RECIPE_CODE}\",\"description\":\"Smoke test recipe\"}")"
  RECIPE_ID="$(printf '%s' "${recipe_response}" | json_get id)"

  recipe_version_response="$(bootstrap_json POST "${FERN_BASE_URL}/recipe-versions" "{\"recipeId\":${RECIPE_ID},\"versionNo\":\"v1\",\"yieldQty\":1.0000,\"yieldUomCode\":\"${SMOKE_YIELD_UOM_CODE}\",\"status\":\"ACTIVE\",\"effectiveFrom\":\"${SMOKE_EFFECTIVE_FROM}\",\"ingredients\":[{\"ingredientId\":${INGREDIENT_ID},\"uomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"qty\":10.0000,\"sortOrder\":1}]}" )"
  RECIPE_VERSION_ID="$(printf '%s' "${recipe_version_response}" | json_get id)"

  bootstrap_json POST "${FERN_BASE_URL}/tax-rates" "{\"productId\":${PRODUCT_ID},\"taxPercent\":10.00,\"effectiveFrom\":\"${SMOKE_EFFECTIVE_FROM}\"}" >/dev/null
  bootstrap_json POST "${FERN_BASE_URL}/product-prices" "{\"productId\":${PRODUCT_ID},\"scopeType\":\"GLOBAL\",\"priceType\":\"RETAIL\",\"currencyCode\":\"VND\",\"priceValue\":55000.00,\"effectiveFrom\":\"${SMOKE_EFFECTIVE_FROM}\"}" >/dev/null
  bootstrap_json PUT "${FERN_BASE_URL}/product-availability" "{\"productId\":${PRODUCT_ID},\"outletId\":${OUTLET_ID},\"available\":true}" >/dev/null
  bootstrap_json PUT "${FERN_BASE_URL}/product-availability" "{\"productId\":${PRODUCT_ID},\"outletId\":${OUTSIDER_OUTLET_ID},\"available\":true}" >/dev/null

  local role_response
  role_response="$(bootstrap_json POST "${FERN_BASE_URL}/roles" "{\"code\":\"${SMOKE_ROLE_CODE}\",\"name\":\"Smoke Role\",\"permissionCodes\":[\"org.region.read\",\"org.outlet.read\"]}")"
  ROLE_CODE="$(printf '%s' "${role_response}" | json_get code)"

  MAIN_USER_ID="$(create_scoped_user "${SMOKE_USER_USERNAME}" "${SMOKE_USER_PASSWORD}" "Smoke User" "${REGION_ID}" "${OUTLET_ID}")"
  OUTSIDER_USER_ID="$(create_scoped_user "${SMOKE_OUTSIDER_USERNAME}" "${SMOKE_OUTSIDER_PASSWORD}" "Smoke Outsider" "${OUTSIDER_REGION_ID}" "${OUTSIDER_OUTLET_ID}")"

  scenario_pass "setup_shared"
}
