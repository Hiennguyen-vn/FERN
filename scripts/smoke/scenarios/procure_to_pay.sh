if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_procure_to_pay() {
  scenario_start "procure_to_pay"

  local inactive_supplier_response
  inactive_supplier_response="$(http_json POST "${FERN_BASE_URL}/suppliers" "{\"supplierCode\":\"${SMOKE_SUPPLIER_CODE}-INACTIVE\",\"name\":\"Inactive Supplier ${RUN_ID}\",\"email\":\"inactive-${RUN_ID}@example.com\",\"phone\":\"0900123000\",\"address\":\"Smoke Address\",\"defaultRegionId\":${REGION_ID},\"status\":\"INACTIVE\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  local inactive_supplier_id
  inactive_supplier_id="$(printf '%s' "${inactive_supplier_response}" | json_get id)"

  http_expect_status 409 POST "${FERN_BASE_URL}/purchase-orders" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"supplierId\":${inactive_supplier_id},\"orderDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"expectedDeliveryDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"lines\":[{\"ingredientId\":${INGREDIENT_ID},\"uomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"qtyOrdered\":5.0000,\"expectedUnitPrice\":12500.00,\"taxPercent\":10.00}]}" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null

  local supplier_response
  supplier_response="$(http_json POST "${FERN_BASE_URL}/suppliers" "{\"supplierCode\":\"${SMOKE_SUPPLIER_CODE}\",\"name\":\"${SMOKE_SUPPLIER_NAME}\",\"email\":\"supplier-${RUN_ID}@example.com\",\"phone\":\"0900123456\",\"address\":\"Smoke Address\",\"defaultRegionId\":${REGION_ID},\"status\":\"INACTIVE\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  SUPPLIER_ID="$(printf '%s' "${supplier_response}" | json_get id)"
  http_json POST "${FERN_BASE_URL}/suppliers/${SUPPLIER_ID}/activate" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null

  local purchase_order_response
  purchase_order_response="$(http_json POST "${FERN_BASE_URL}/purchase-orders" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"supplierId\":${SUPPLIER_ID},\"orderDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"expectedDeliveryDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"note\":\"Smoke purchase order\",\"lines\":[{\"ingredientId\":${INGREDIENT_ID},\"uomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"qtyOrdered\":5.0000,\"expectedUnitPrice\":12500.00,\"taxPercent\":10.00}]}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  PURCHASE_ORDER_ID="$(printf '%s' "${purchase_order_response}" | json_get id)"
  PURCHASE_ORDER_LINE_ID="$(printf '%s' "${purchase_order_response}" | json_get lines.0.id)"

  http_json POST "${FERN_BASE_URL}/purchase-orders/${PURCHASE_ORDER_ID}/submit" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  http_json POST "${FERN_BASE_URL}/purchase-orders/${PURCHASE_ORDER_ID}/approve" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  local issued_purchase_order_response
  issued_purchase_order_response="$(http_json POST "${FERN_BASE_URL}/purchase-orders/${PURCHASE_ORDER_ID}/issue" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  PURCHASE_ORDER_LINE_ID="$(printf '%s' "${issued_purchase_order_response}" | json_get lines.0.id)"

  local goods_receipt_response
  goods_receipt_response="$(http_json POST "${FERN_BASE_URL}/goods-receipts" "{\"purchaseOrderId\":${PURCHASE_ORDER_ID},\"receiptTime\":\"${SMOKE_EFFECTIVE_FROM}T10:00:00Z\",\"businessDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"supplierLotNumber\":\"LOT-${RUN_ID}\",\"note\":\"Smoke goods receipt\",\"lines\":[{\"purchaseOrderLineId\":${PURCHASE_ORDER_LINE_ID},\"ingredientId\":${INGREDIENT_ID},\"uomCode\":\"${SMOKE_BASE_UOM_CODE}\",\"qtyReceived\":3.0000,\"unitCost\":12500.00}]}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  GOODS_RECEIPT_ID="$(printf '%s' "${goods_receipt_response}" | json_get id)"
  http_json POST "${FERN_BASE_URL}/goods-receipts/${GOODS_RECEIPT_ID}/receive" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  local posted_goods_receipt_response
  posted_goods_receipt_response="$(http_json POST "${FERN_BASE_URL}/goods-receipts/${GOODS_RECEIPT_ID}/post" "" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-gr-post-${RUN_ID}")"
  GOODS_RECEIPT_LINE_ID="$(printf '%s' "${posted_goods_receipt_response}" | json_get lines.0.id)"

  http_expect_status 409 POST "${FERN_BASE_URL}/goods-receipts/${GOODS_RECEIPT_ID}/post" "" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-gr-post-repeat-${RUN_ID}" >/dev/null

  local supplier_invoice_response
  supplier_invoice_response="$(http_json POST "${FERN_BASE_URL}/supplier-invoices" "{\"supplierId\":${SUPPLIER_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"currencyCode\":\"VND\",\"invoiceNumber\":\"${SMOKE_SUPPLIER_INVOICE_NUMBER}\",\"invoiceDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"lines\":[{\"lineType\":\"STOCK\",\"goodsReceiptLineId\":${GOODS_RECEIPT_LINE_ID},\"description\":\"Smoke ingredient delivery\",\"qtyInvoiced\":3.0000,\"unitPrice\":12500.00,\"taxPercent\":10.00,\"taxAmount\":3750.00,\"lineTotal\":41250.00}]}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  SUPPLIER_INVOICE_ID="$(printf '%s' "${supplier_invoice_response}" | json_get id)"
  http_json POST "${FERN_BASE_URL}/supplier-invoices/${SUPPLIER_INVOICE_ID}/approve" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null

  local supplier_payment_response
  local supplier_payment_replay_response
  local supplier_payment_conflict
  supplier_payment_response="$(http_json POST "${FERN_BASE_URL}/supplier-payments" "{\"supplierId\":${SUPPLIER_ID},\"currencyCode\":\"VND\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":41250.00,\"paymentTime\":\"${SMOKE_EFFECTIVE_FROM}T12:00:00Z\",\"transactionRef\":\"PAY-${RUN_ID}\",\"invoiceAllocations\":[{\"supplierInvoiceId\":${SUPPLIER_INVOICE_ID},\"allocatedAmount\":41250.00,\"note\":\"Smoke settlement\"}]}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-supplier-payment-${RUN_ID}")"
  SUPPLIER_PAYMENT_ID="$(printf '%s' "${supplier_payment_response}" | json_get id)"
  supplier_payment_replay_response="$(http_json POST "${FERN_BASE_URL}/supplier-payments" "{\"supplierId\":${SUPPLIER_ID},\"currencyCode\":\"VND\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":41250.00,\"paymentTime\":\"${SMOKE_EFFECTIVE_FROM}T12:00:00Z\",\"transactionRef\":\"PAY-${RUN_ID}\",\"invoiceAllocations\":[{\"supplierInvoiceId\":${SUPPLIER_INVOICE_ID},\"allocatedAmount\":41250.00,\"note\":\"Smoke settlement\"}]}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-supplier-payment-${RUN_ID}")"
  assert_json_value "${supplier_payment_replay_response}" "id" "${SUPPLIER_PAYMENT_ID}"

  supplier_payment_conflict="$(http_expect_status 409 POST "${FERN_BASE_URL}/supplier-payments" "{\"supplierId\":${SUPPLIER_ID},\"currencyCode\":\"VND\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":41250.00,\"paymentTime\":\"${SMOKE_EFFECTIVE_FROM}T12:00:00Z\",\"transactionRef\":\"PAY-${RUN_ID}-MISMATCH\",\"invoiceAllocations\":[{\"supplierInvoiceId\":${SUPPLIER_INVOICE_ID},\"allocatedAmount\":41250.00,\"note\":\"Smoke settlement mismatch\"}]}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-supplier-payment-${RUN_ID}")"
  assert_contains "${supplier_payment_conflict}" "Idempotency-Key cannot be reused with a different supplier payment request"

  wait_for_sql_count_eq "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM inventory.inventory_transaction WHERE outlet_id = ${OUTLET_ID} AND ingredient_id = ${INGREDIENT_ID} AND txn_type = 'PURCHASE_IN' AND source_reference_id = '${GOODS_RECEIPT_ID}';" "1" "purchase receipt inventory transaction"
  wait_for_sql_count_eq "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM procurement.supplier_payment WHERE idempotency_key = 'smoke-supplier-payment-${RUN_ID}';" "1" "supplier payment idempotency row"
  wait_for_sql_count_eq "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM procurement.supplier_payment_allocation WHERE supplier_payment_id = ${SUPPLIER_PAYMENT_ID};" "1" "supplier payment allocation row"
  wait_for_sql_count_eq "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM finance.expense_inventory_purchase WHERE goods_receipt_id = ${GOODS_RECEIPT_ID};" "1" "finance purchase expense row"
  wait_for_sql_count_eq "${FERN_MASTER_DB}" "SELECT COUNT(*) FROM finance_projection.accounting_posting_projection WHERE reference_id = '${SUPPLIER_PAYMENT_ID}';" "1" "finance posting projection row"
  wait_for_sql_count_ge "${FERN_MASTER_DB}" "SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot WHERE snapshot_type = 'SUPPLIER_PAYMENT' AND snapshot_value = 41250.00;" "1" "finance reconciliation snapshot"

  local inventory_balance_response
  local balance_qty_on_hand
  inventory_balance_response="$(http_json GET "${FERN_BASE_URL}/stock-balances?outletId=${OUTLET_ID}&ingredientId=${INGREDIENT_ID}" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  balance_qty_on_hand="$(printf '%s' "${inventory_balance_response}" | json_get items.0.qtyOnHand)"
  if ! decimal_equals "31.0000" "${balance_qty_on_hand}"; then
    fail "Expected post-procurement inventory qty_on_hand 31.0000, got ${balance_qty_on_hand}"
  fi

  local purchase_order_status_response
  purchase_order_status_response="$(http_json GET "${FERN_BASE_URL}/purchase-orders/${PURCHASE_ORDER_ID}" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${purchase_order_status_response}" "status" "PARTIALLY_RECEIVED"

  scenario_pass "UC-PROC-01/UC-PROC-02/UC-PROC-03/UC-PROC-04/UC-PROC-05"
}
