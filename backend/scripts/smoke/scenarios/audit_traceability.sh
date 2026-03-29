if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_audit_traceability() {
  scenario_start "audit_traceability"

  local endpoint
  for endpoint in \
    "/attendance-approvals/${SHIFT_ASSIGNMENT_ID}/approve" \
    "/sale-orders/${PRIMARY_SALE_ORDER_ID}/complete" \
    "/goods-receipts/${GOODS_RECEIPT_ID}/post" \
    "/supplier-payments" \
    "/payroll-runs/${PAYROLL_RUN_ID}/approve" \
    "/payroll-runs/${PAYROLL_RUN_ID}/mark-paid"
  do
    local trace_found="0"
    local encoded_endpoint
    encoded_endpoint="$(url_encode "${endpoint}")"
    for _ in $(seq 1 60); do
      local trace_response
      trace_response="$(bootstrap_json GET "${FERN_BASE_URL}/audit/request-traces?sourceService=api-gateway&endpoint=${encoded_endpoint}&statusCode=200&limit=10" "" 2>/dev/null || true)"
      trace_found="$(printf '%s' "${trace_response}" | python3 -c 'import json,sys
try:
    data=json.load(sys.stdin)
    print(1 if data.get("items") else 0)
except Exception:
    print(0)
')"
      if [[ "${trace_found}" == "1" ]]; then
        break
      fi
      sleep 2
    done
    if [[ "${trace_found}" != "1" ]]; then
      fail "Timed out waiting for audit request trace for endpoint ${endpoint}"
    fi
  done

  scenario_pass "audit_traceability"
}
