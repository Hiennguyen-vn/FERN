if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_pos_order_to_cash() {
  scenario_start "pos_order_to_cash"

  local pos_session_response
  local order_response
  local updated_order_response
  local updated_total_amount
  local payment_response
  local complete_response
  local cancel_response
  local inventory_transactions
  local sale_usage_count
  local inventory_balance_response
  local balance_qty_on_hand
  local closed_session_response
  local reconciled_session_response

  pos_session_response="$(http_json POST "${FERN_BASE_URL}/pos-sessions" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"currencyCode\":\"VND\",\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"note\":\"Smoke POS session\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  POS_SESSION_ID="$(printf '%s' "${pos_session_response}" | json_get id)"

  order_response="$(http_json POST "${FERN_BASE_URL}/sale-orders" "{\"posSessionId\":${POS_SESSION_ID},\"orderType\":\"DINE_IN\",\"note\":\"Smoke main order\",\"lines\":[{\"productId\":${PRODUCT_ID},\"qty\":1.0000,\"note\":\"Initial line\"}]}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  PRIMARY_SALE_ORDER_ID="$(printf '%s' "${order_response}" | json_get id)"

  updated_order_response="$(http_json PATCH "${FERN_BASE_URL}/sale-orders/${PRIMARY_SALE_ORDER_ID}" "{\"note\":\"Smoke updated order\",\"lines\":[{\"productId\":${PRODUCT_ID},\"qty\":1.0000,\"note\":\"Updated line\"}]}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  updated_total_amount="$(printf '%s' "${updated_order_response}" | json_get totalAmount)"
  if [[ "${updated_total_amount}" != "60500.00" && "${updated_total_amount}" != "60500.0" && "${updated_total_amount}" != "60500" ]]; then
    fail "Expected updated sale order total 60500.00, got ${updated_total_amount}"
  fi

  http_json POST "${FERN_BASE_URL}/sale-orders/${PRIMARY_SALE_ORDER_ID}/payments" "{\"paymentMethod\":\"CASH\",\"amount\":30000.00}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-pay-cash-${RUN_ID}" >/dev/null
  payment_response="$(http_json POST "${FERN_BASE_URL}/sale-orders/${PRIMARY_SALE_ORDER_ID}/payments" "{\"paymentMethod\":\"CARD\",\"amount\":30500.00,\"transactionRef\":\"SMOKE-TXN-${RUN_ID}\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-pay-card-${RUN_ID}")"
  assert_json_value "${payment_response}" "paymentStatus" "PAID"

  complete_response="$(http_json POST "${FERN_BASE_URL}/sale-orders/${PRIMARY_SALE_ORDER_ID}/complete" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${complete_response}" "status" "COMPLETED"

  http_expect_status 409 POST "${FERN_BASE_URL}/sale-orders/${PRIMARY_SALE_ORDER_ID}/cancel" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null

  local partial_order_response
  partial_order_response="$(http_json POST "${FERN_BASE_URL}/sale-orders" "{\"posSessionId\":${POS_SESSION_ID},\"orderType\":\"TAKEAWAY\",\"note\":\"Partial pay order\",\"lines\":[{\"productId\":${PRODUCT_ID},\"qty\":1.0000}]}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  PARTIAL_SALE_ORDER_ID="$(printf '%s' "${partial_order_response}" | json_get id)"

  http_json POST "${FERN_BASE_URL}/sale-orders/${PARTIAL_SALE_ORDER_ID}/payments" "{\"paymentMethod\":\"CASH\",\"amount\":10000.00}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-partial-pay-${RUN_ID}" >/dev/null
  http_expect_status 409 POST "${FERN_BASE_URL}/sale-orders/${PARTIAL_SALE_ORDER_ID}/complete" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  http_json POST "${FERN_BASE_URL}/sale-orders/${PARTIAL_SALE_ORDER_ID}/payments" "{\"paymentMethod\":\"CARD\",\"amount\":50500.00,\"transactionRef\":\"SMOKE-PARTIAL-${RUN_ID}\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-partial-pay-rest-${RUN_ID}" >/dev/null
  http_json POST "${FERN_BASE_URL}/sale-orders/${PARTIAL_SALE_ORDER_ID}/complete" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null

  local open_order_response
  open_order_response="$(http_json POST "${FERN_BASE_URL}/sale-orders" "{\"posSessionId\":${POS_SESSION_ID},\"orderType\":\"TAKEAWAY\",\"note\":\"Open order for close check\",\"lines\":[{\"productId\":${PRODUCT_ID},\"qty\":1.0000}]}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  OPEN_SALE_ORDER_ID="$(printf '%s' "${open_order_response}" | json_get id)"
  http_expect_status 409 POST "${FERN_BASE_URL}/pos-sessions/${POS_SESSION_ID}/close" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  cancel_response="$(http_json POST "${FERN_BASE_URL}/sale-orders/${OPEN_SALE_ORDER_ID}/cancel" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${cancel_response}" "status" "CANCELLED"

  for _ in $(seq 1 60); do
    inventory_transactions="$(http_json GET "${FERN_BASE_URL}/inventory-transactions?outletId=${OUTLET_ID}&ingredientId=${INGREDIENT_ID}&txnType=SALE_USAGE" "" "Bearer ${SMOKE_ACCESS_TOKEN}" 2>/dev/null || true)"
    sale_usage_count="$(printf '%s' "${inventory_transactions}" | python3 -c 'import json,sys
try:
    data=json.load(sys.stdin)
    print(len(data.get("items", [])))
except Exception:
    print(0)
')"
    if [[ "${sale_usage_count}" -ge 2 ]]; then
      break
    fi
    sleep 2
  done
  if [[ "${sale_usage_count:-0}" -lt 2 ]]; then
    fail "Timed out waiting for SALE_USAGE inventory transactions"
  fi

  inventory_balance_response="$(http_json GET "${FERN_BASE_URL}/stock-balances?outletId=${OUTLET_ID}&ingredientId=${INGREDIENT_ID}" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  balance_qty_on_hand="$(printf '%s' "${inventory_balance_response}" | json_get items.0.qtyOnHand)"
  if ! decimal_equals "28.0000" "${balance_qty_on_hand}"; then
    fail "Expected post-sale inventory qty_on_hand 28.0000, got ${balance_qty_on_hand}"
  fi

  closed_session_response="$(http_json POST "${FERN_BASE_URL}/pos-sessions/${POS_SESSION_ID}/close" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${closed_session_response}" "status" "CLOSED"

  reconciled_session_response="$(http_json POST "${FERN_BASE_URL}/pos-sessions/${POS_SESSION_ID}/reconcile" "{\"countedCashAmount\":40000.00,\"note\":\"Smoke reconciliation\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${reconciled_session_response}" "status" "RECONCILED"

  scenario_pass "UC-SAL-01/UC-SAL-02/UC-SAL-03/UC-SAL-04/UC-SAL-05"
}
