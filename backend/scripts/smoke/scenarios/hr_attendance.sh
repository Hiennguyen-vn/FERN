if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_hr_attendance() {
  scenario_start "hr_attendance"

  local employee_response
  employee_response="$(bootstrap_json POST "${FERN_BASE_URL}/employees" "{\"employeeCode\":\"EMP-${RUN_ID}\",\"fullName\":\"Smoke Employee ${RUN_ID}\",\"status\":\"ACTIVE\",\"hiredAt\":\"${SMOKE_EFFECTIVE_FROM}\"}")"
  EMPLOYEE_ID="$(printf '%s' "${employee_response}" | json_get id)"

  bootstrap_json POST "${FERN_BASE_URL}/employee-contracts" "{\"employeeId\":${EMPLOYEE_ID},\"employmentType\":\"FULL_TIME\",\"salaryType\":\"MONTHLY\",\"baseSalary\":12000000.00,\"regionId\":${REGION_ID},\"taxCode\":\"TAX-${RUN_ID}\",\"contractStatus\":\"ACTIVE\",\"startDate\":\"${SMOKE_EFFECTIVE_FROM}\"}" >/dev/null

  local assignment_response
  local schedule_response
  local shift_assignment_response
  assignment_response="$(http_json POST "${FERN_BASE_URL}/employee-assignments" "{\"employeeId\":${EMPLOYEE_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"positionTitle\":\"Barista\",\"startDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"primaryAssignment\":true,\"status\":\"ACTIVE\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  EMPLOYEE_ASSIGNMENT_ID="$(printf '%s' "${assignment_response}" | json_get id)"

  schedule_response="$(http_json POST "${FERN_BASE_URL}/shift-schedules" "{\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"shiftDate\":\"${SMOKE_EFFECTIVE_FROM}\",\"shiftName\":\"Morning Smoke\",\"startTime\":\"08:00:00\",\"endTime\":\"16:00:00\",\"status\":\"SCHEDULED\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  SHIFT_SCHEDULE_ID="$(printf '%s' "${schedule_response}" | json_get id)"

  shift_assignment_response="$(http_json POST "${FERN_BASE_URL}/shift-assignments" "{\"shiftScheduleId\":${SHIFT_SCHEDULE_ID},\"employeeId\":${EMPLOYEE_ID},\"assignedRole\":\"STAFF\",\"note\":\"SRS attendance shift\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  SHIFT_ASSIGNMENT_ID="$(printf '%s' "${shift_assignment_response}" | json_get id)"

  http_expect_status 400 POST "${FERN_BASE_URL}/attendance-events" "{\"employeeId\":${EMPLOYEE_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"shiftAssignmentId\":${SHIFT_ASSIGNMENT_ID},\"eventType\":\"CLOCK_IN\",\"eventTime\":\"${SMOKE_EFFECTIVE_FROM}T08:00:00Z\",\"sourceSystem\":\"SMOKE\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  assert_json_value "${HTTP_BODY}" "code" "bad_request"

  http_json POST "${FERN_BASE_URL}/attendance-events" "{\"employeeId\":${EMPLOYEE_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"shiftAssignmentId\":${SHIFT_ASSIGNMENT_ID},\"eventType\":\"CLOCK_IN\",\"eventTime\":\"${SMOKE_EFFECTIVE_FROM}T08:00:00Z\",\"sourceSystem\":\"SMOKE\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: attendance-clock-in-${RUN_ID}" >/dev/null
  http_json POST "${FERN_BASE_URL}/attendance-events" "{\"employeeId\":${EMPLOYEE_ID},\"regionId\":${REGION_ID},\"outletId\":${OUTLET_ID},\"shiftAssignmentId\":${SHIFT_ASSIGNMENT_ID},\"eventType\":\"CLOCK_OUT\",\"eventTime\":\"${SMOKE_EFFECTIVE_FROM}T17:00:00Z\",\"sourceSystem\":\"SMOKE\"}" "Bearer ${SMOKE_ACCESS_TOKEN}" "Idempotency-Key: attendance-clock-out-${RUN_ID}" >/dev/null

  local outsider_approval
  outsider_approval="$(http_expect_status 403 POST "${FERN_BASE_URL}/attendance-approvals/${SHIFT_ASSIGNMENT_ID}/approve" "{\"comments\":\"Out of scope\"}" "Bearer ${SMOKE_OUTSIDER_ACCESS_TOKEN}")"
  assert_contains "${outsider_approval}" "forbidden"

  local approval_response
  approval_response="$(http_json POST "${FERN_BASE_URL}/attendance-approvals/${SHIFT_ASSIGNMENT_ID}/approve" "{\"comments\":\"Smoke attendance approved\"}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${approval_response}" "status" "APPROVED"

  local attendance_list
  attendance_list="$(http_json GET "${FERN_BASE_URL}/attendance-events?outletId=${OUTLET_ID}&shiftAssignmentId=${SHIFT_ASSIGNMENT_ID}&fromDate=${SMOKE_EFFECTIVE_FROM}&toDate=${SMOKE_EFFECTIVE_FROM}&page=0&size=10&sort=asc" "" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  assert_json_value "${attendance_list}" "items.0.eventType" "CLOCK_IN"
  assert_json_value "${attendance_list}" "items.1.eventType" "CLOCK_OUT"

  wait_for_sql_count_eq "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM hr.attendance_approval WHERE shift_assignment_id = ${SHIFT_ASSIGNMENT_ID} AND status = 'APPROVED';" "1" "approved attendance row"
  wait_for_sql_count_eq "${FERN_OPERATIONAL_DB}" "SELECT COUNT(*) FROM hr.outbox_event WHERE event_type = 'attendance.approved' AND aggregate_id = '${SHIFT_ASSIGNMENT_ID}';" "1" "attendance outbox event"

  scenario_pass "UC-HR-03/UC-HR-04"
}
