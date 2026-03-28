if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_inventory_outlet() {
  scenario_start "inventory_outlet"

  local adjustment_response
  local waste_response
  local stock_count_response
  local inventory_balance_response
  local balance_qty_on_hand

  adjustment_response="$(http_json POST "${FERN_BASE_URL}/stock-adjustments" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"ingredientId\":${INGREDIENT_ID},\"adjustmentDirection\":\"IN\",\"qty\":50.0000,\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"reason\":\"BOOTSTRAP\",\"note\":\"Seed opening stock\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  ADJUSTMENT_ID="$(printf '%s' "${adjustment_response}" | json_get id)"
  http_json POST "${FERN_BASE_URL}/stock-adjustments/${ADJUSTMENT_ID}/post" "" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-adjustment-post-${RUN_ID}" >/dev/null

  waste_response="$(http_json POST "${FERN_BASE_URL}/waste-records" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"ingredientId\":${INGREDIENT_ID},\"qty\":2.0000,\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"reason\":\"SPILL\",\"note\":\"Smoke waste\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  WASTE_ID="$(printf '%s' "${waste_response}" | json_get id)"
  http_json POST "${FERN_BASE_URL}/waste-records/${WASTE_ID}/post" "" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-waste-post-${RUN_ID}" >/dev/null

  stock_count_response="$(http_json POST "${FERN_BASE_URL}/stock-count-sessions" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"countDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"ingredientIds\":[${INGREDIENT_ID}],\"note\":\"Smoke stock count\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  STOCK_COUNT_ID="$(printf '%s' "${stock_count_response}" | json_get id)"
  http_json POST "${FERN_BASE_URL}/stock-count-sessions/${STOCK_COUNT_ID}/start" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  http_json PUT "${FERN_BASE_URL}/stock-count-sessions/${STOCK_COUNT_ID}/lines" "{\"lines\":[{\"ingredientId\":${INGREDIENT_ID},\"actualQty\":48.0000,\"note\":\"Count confirmed\"}]}" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  http_json POST "${FERN_BASE_URL}/stock-count-sessions/${STOCK_COUNT_ID}/post" "" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-count-post-${RUN_ID}" >/dev/null

  inventory_balance_response="$(http_json GET "${FERN_BASE_URL}/stock-balances?outletId=${OUTLET_ID}&ingredientId=${INGREDIENT_ID}" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  balance_qty_on_hand="$(printf '%s' "${inventory_balance_response}" | json_get items.0.qtyOnHand)"
  if ! decimal_equals "48.0000" "${balance_qty_on_hand}"; then
    fail "Expected seeded inventory qty_on_hand 48.0000, got ${balance_qty_on_hand}"
  fi

  wait_for_sql_count_eq "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM inventory.inventory_transaction WHERE outlet_id = ${OUTLET_ID} AND ingredient_id = ${INGREDIENT_ID} AND txn_type IN ('STOCK_ADJUSTMENT_IN', 'WASTE_OUT');" "2" "inventory transaction rows"

  scenario_pass "UC-INV-01/UC-INV-02/UC-INV-03/UC-INV-04"
}
