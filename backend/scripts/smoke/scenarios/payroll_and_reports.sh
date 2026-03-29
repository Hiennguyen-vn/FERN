if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_payroll_and_reports() {
  scenario_start "payroll_and_reports"

  local empty_period_response
  local empty_run_response
  local payroll_period_response
  local payroll_run_response
  local payroll_paid_response
  local payroll_summary_response
  local payroll_run_report_response
  local payroll_export_response
  local payroll_export_status
  local payroll_export_download_status
  local empty_total_amount

  empty_period_response="$(http_json POST "${FERN_BASE_URL}/payroll-periods" "{\"regionId\":${OUTSIDER_REGION_ID},\"name\":\"Empty Payroll ${RUN_ID}\",\"startDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"endDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"payDate\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${SMOKE_OUTSIDER_ACCESS_TOKEN}")"
  EMPTY_PAYROLL_PERIOD_ID="$(printf '%s' "${empty_period_response}" | json_get id)"
  empty_run_response="$(http_json POST "${FERN_BASE_URL}/payroll-runs" "{\"payrollPeriodId\":${EMPTY_PAYROLL_PERIOD_ID},\"runDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"note\":\"Empty payroll draft\"}" "Bearer ${SMOKE_OUTSIDER_ACCESS_TOKEN}")"
  EMPTY_PAYROLL_RUN_ID="$(printf '%s' "${empty_run_response}" | json_get id)"
  empty_total_amount="$(printf '%s' "${empty_run_response}" | json_get totalAmount)"
  if ! decimal_equals "0.00" "${empty_total_amount}"; then
    fail "Expected empty payroll total 0.00, got ${empty_total_amount}"
  fi
  assert_json_value "${empty_run_response}" "employees" "[]"

  payroll_period_response="$(http_json POST "${FERN_BASE_URL}/payroll-periods" "{\"regionId\":${REGION_ID},\"name\":\"Smoke Payroll ${RUN_ID}\",\"startDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"endDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"payDate\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  PAYROLL_PERIOD_ID="$(printf '%s' "${payroll_period_response}" | json_get id)"
  payroll_run_response="$(http_json POST "${FERN_BASE_URL}/payroll-runs" "{\"payrollPeriodId\":${PAYROLL_PERIOD_ID},\"runDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"note\":\"Smoke payroll draft\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  PAYROLL_RUN_ID="$(printf '%s' "${payroll_run_response}" | json_get id)"

  http_expect_status 400 POST "${FERN_BASE_URL}/payroll-runs/${PAYROLL_RUN_ID}/mark-paid" "{\"paymentReference\":\"PREMATURE-${RUN_ID}\",\"note\":\"Should fail\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null

  http_json POST "${FERN_BASE_URL}/payroll-runs/${PAYROLL_RUN_ID}/submit" "{\"note\":\"Smoke submit\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  http_json POST "${FERN_BASE_URL}/payroll-runs/${PAYROLL_RUN_ID}/approve" "{\"note\":\"Smoke approve\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  payroll_paid_response="$(http_json POST "${FERN_BASE_URL}/payroll-runs/${PAYROLL_RUN_ID}/mark-paid" "{\"paymentReference\":\"PAYROLL-${RUN_ID}\",\"note\":\"Smoke payroll payment\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${payroll_paid_response}" "status" "PAID"

  wait_for_sql_count_ge "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM finance.expense_payroll WHERE payroll_run_id = ${PAYROLL_RUN_ID};" "1" "payroll expense rows"
  wait_for_sql_count_ge "${FERN_MASTER_DB}" "SELECT COUNT(*) FROM report.payroll_fact WHERE payroll_run_id = ${PAYROLL_RUN_ID};" "1" "report payroll facts"
  wait_for_sql_count_ge "${FERN_MASTER_DB}" "SELECT COUNT(*) FROM report.expense_fact WHERE payroll_run_id = ${PAYROLL_RUN_ID};" "1" "report expense facts"

  payroll_summary_response="$(http_json GET "${FERN_BASE_URL}/reports/payroll/summary?regionId=${REGION_ID}&fromDate=${SMOKE_EFFECTIVE_FROM}&toDate=${SMOKE_EFFECTIVE_FROM}" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_contains "${payroll_summary_response}" "\"regionId\":${REGION_ID}"

  payroll_run_report_response="$(http_json GET "${FERN_BASE_URL}/reports/payroll/runs/${PAYROLL_RUN_ID}" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${payroll_run_report_response}" "payrollRunId" "${PAYROLL_RUN_ID}"

  payroll_export_response="$(http_json POST "${FERN_BASE_URL}/reports/payroll/export" "{\"regionId\":${REGION_ID},\"fromDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"toDate\":\"${SMOKE_EFFECTIVE_FROM}\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: smoke-payroll-export-${RUN_ID}")"
  PAYROLL_EXPORT_JOB_ID="$(printf '%s' "${payroll_export_response}" | json_get exportJobId)"
  payroll_export_status="$(printf '%s' "${payroll_export_response}" | json_get status)"

  for _ in $(seq 1 60); do
    if [[ "${payroll_export_status}" == "COMPLETED" || "${payroll_export_status}" == "FAILED" ]]; then
      break
    fi
    sleep 2
    payroll_export_response="$(http_json GET "${FERN_BASE_URL}/reports/exports/${PAYROLL_EXPORT_JOB_ID}" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
    payroll_export_status="$(printf '%s' "${payroll_export_response}" | json_get status)"
  done
  if [[ "${payroll_export_status}" != "COMPLETED" ]]; then
    fail "Expected payroll export status COMPLETED, got ${payroll_export_status}"
  fi

  http_json GET "${FERN_BASE_URL}/reports/exports/${PAYROLL_EXPORT_JOB_ID}/preview" "" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  payroll_export_download_status="$(http_status GET "${FERN_BASE_URL}/reports/exports/${PAYROLL_EXPORT_JOB_ID}/download" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  if [[ "${payroll_export_download_status}" != "200" ]]; then
    fail "Expected payroll export download status 200, got ${payroll_export_download_status}"
  fi

  scenario_pass "UC-FIN-01/UC-FIN-02/UC-FIN-03"
}
