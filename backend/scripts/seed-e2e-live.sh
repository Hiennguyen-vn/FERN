#!/usr/bin/env bash
# FERN live API seed: org + iam + catalog + hr + inventory + procurement + finance + pos + reports
# Public API only. No SQL. Deterministic and rerunnable.
#
# Notes:
# - Attendance approval in the current public HR API derives PRESENT/LATE/ABSENT/PENDING.
#   LEAVE cannot be created without a dedicated public endpoint, so this seed uses ABSENT
#   where the spec asked for LEAVE.
# - The repo does not expose a standalone "region_manager" role code. This script models
#   region manager accounts as a composite of outlet_manager + regional_finance scoped to
#   the target region subtree, matching the codebase permission seed.

set -euo pipefail
IFS=$'\n\t'

FERN_BASE_URL="${FERN_BASE_URL:-}"
BOOTSTRAP_USERNAME="${BOOTSTRAP_USERNAME:-}"
BOOTSTRAP_PASSWORD="${BOOTSTRAP_PASSWORD:-}"
BUSINESS_DATE="${BUSINESS_DATE:-}"
HISTORY_DAYS="${HISTORY_DAYS:-30}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo123!}"
FERN_CURRENCY="${FERN_CURRENCY:-VND}"
FERN_TIMEZONE="${FERN_TIMEZONE:-Asia/Ho_Chi_Minh}"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/fern-seed.XXXXXX")"
HTTP_STATUS=""
HTTP_BODY=""
CREATED_TOTAL=0
REUSED_TOTAL=0
VERIFIED_TOTAL=0

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

log() {
  printf '[seed-e2e] %s\n' "$*"
}

warn() {
  printf '[seed-e2e] WARN: %s\n' "$*" >&2
}

fail() {
  printf '[seed-e2e] ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "Environment variable ${name} is required"
}

slugify() {
  printf '%s' "$1" | tr '[:upper:]_' '[:lower:]-'
}

urlencode() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote
print(quote(sys.argv[1]))
PY
}

json_get() {
  local body="$1"
  local filter="$2"
  jq -r "${filter}" <<<"${body}"
}

json_count() {
  local body="$1"
  local filter="$2"
  jq -r "${filter}" <<<"${body}"
}

map_put() {
  local map_json="$1"
  local key="$2"
  local value="$3"
  jq -c --arg k "${key}" --arg v "${value}" '. + {($k): $v}' <<<"${map_json}"
}

map_get() {
  local map_json="$1"
  local key="$2"
  jq -r --arg k "${key}" '.[$k] // empty' <<<"${map_json}"
}

as_json_id_array() {
  local map_json="$1"
  local codes_json="$2"
  jq -nc --argjson map "${map_json}" --argjson codes "${codes_json}" '
    [ $codes[] | ($map[.] // empty) | select(length > 0) | tonumber ]
  '
}

decimal_mul() {
  python3 - "$1" "$2" <<'PY'
import sys
from decimal import Decimal, ROUND_HALF_UP
a = Decimal(sys.argv[1])
b = Decimal(sys.argv[2])
print((a * b).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
PY
}

decimal_tax() {
  python3 - "$1" "$2" <<'PY'
import sys
from decimal import Decimal, ROUND_HALF_UP
subtotal = Decimal(sys.argv[1])
tax_percent = Decimal(sys.argv[2])
print((subtotal * (tax_percent / Decimal("100"))).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
PY
}

decimal_add() {
  python3 - "$1" "$2" <<'PY'
import sys
from decimal import Decimal, ROUND_HALF_UP
left = Decimal(sys.argv[1])
right = Decimal(sys.argv[2])
print((left + right).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
PY
}

count_json_items() {
  local body="$1"
  if jq -e 'type == "array"' >/dev/null 2>&1 <<<"${body}"; then
    jq -r 'length' <<<"${body}"
  else
    jq -r '.items | length' <<<"${body}"
  fi
}

phone_for_key() {
  python3 - "$1" "$2" <<'PY'
import hashlib
import sys

prefix = sys.argv[1]
key = sys.argv[2].encode("utf-8")
digits = ''.join(str(int(ch, 16) % 10) for ch in hashlib.sha1(key).hexdigest())[:7]
print(prefix + digits)
PY
}

build_date_meta() {
  python3 - "${BUSINESS_DATE}" "${HISTORY_DAYS}" <<'PY'
import json
import sys
from datetime import date, timedelta

business_date = date.fromisoformat(sys.argv[1])
history_days = int(sys.argv[2])
history_start = business_date - timedelta(days=history_days - 1)
initial_stock_date = history_start
waste_date = business_date - timedelta(days=1)
stock_count_date = business_date - timedelta(days=2)
current_month_start = business_date.replace(day=1)
previous_month_end = current_month_start - timedelta(days=1)
previous_month_start = previous_month_end.replace(day=1)

dates = []
cursor = history_start
while cursor <= business_date:
    dates.append(cursor.isoformat())
    cursor += timedelta(days=1)

print(json.dumps({
    "history_start": history_start.isoformat(),
    "initial_stock_date": initial_stock_date.isoformat(),
    "waste_date": waste_date.isoformat(),
    "stock_count_date": stock_count_date.isoformat(),
    "current_month_start": current_month_start.isoformat(),
    "previous_month_start": previous_month_start.isoformat(),
    "previous_month_end": previous_month_end.isoformat(),
    "previous_pay_date": (previous_month_end + timedelta(days=5)).isoformat(),
    "current_period_end": business_date.isoformat(),
    "current_pay_date": (business_date + timedelta(days=5)).isoformat(),
    "previous_year_month": previous_month_start.strftime("%Y-%m"),
    "current_year_month": current_month_start.strftime("%Y-%m"),
    "dates": dates
}))
PY
}

to_utc_instant() {
  python3 - "$1" "$2" "${FERN_TIMEZONE}" <<'PY'
import sys
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
local_date = sys.argv[1]
local_time = sys.argv[2]
tz = ZoneInfo(sys.argv[3])
dt = datetime.fromisoformat(f"{local_date}T{local_time}").replace(tzinfo=tz)
print(dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"))
PY
}

name_for_index() {
  local index="$1"
  local families=(Nguyen Tran Le Pham Hoang Vo Bui Dang Do Vu Truong)
  local middles=(Minh Anh Gia Duc Khanh Thu Bao Quang Thanh Phuong)
  local givens=(An Binh Chau Dung Ha Khoa Lan Linh Nam Oanh Phuc Quan Trang Vy Yen)
  local family="${families[$((index % ${#families[@]}))]}"
  local middle="${middles[$(((index / ${#families[@]}) % ${#middles[@]}))]}"
  local given="${givens[$(((index / (${#families[@]} * ${#middles[@]})) % ${#givens[@]}))]}"
  printf '%s %s %s' "${family}" "${middle}" "${given}"
}

gender_for_index() {
  local index="$1"
  if (( index % 2 == 0 )); then
    printf 'FEMALE'
  else
    printf 'MALE'
  fi
}

api_request() {
  local method="$1"
  local path="$2"
  local body="$3"
  local token="$4"
  shift 4 || true

  local response_file="${TMP_DIR}/response.$RANDOM.json"
  local url="${FERN_BASE_URL}${path}"
  local -a curl_args
  curl_args=(-sS -X "${method}" "${url}" -H 'Accept: application/json')

  if [[ -n "${token}" ]]; then
    curl_args+=(-H "Authorization: Bearer ${token}")
  fi

  if [[ "${method}" != "GET" ]]; then
    curl_args+=(-H 'Content-Type: application/json')
  fi

  while [[ $# -gt 0 ]]; do
    curl_args+=(-H "$1")
    shift
  done

  if [[ "${body}" != "__NO_BODY__" ]]; then
    curl_args+=(--data "${body}")
  fi

  HTTP_STATUS="$(curl "${curl_args[@]}" -o "${response_file}" -w '%{http_code}')" || fail "curl failed for ${method} ${url}"
  HTTP_BODY="$(cat "${response_file}")"
  rm -f "${response_file}"
}

http_ok() {
  [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]
}

duplicate_response() {
  local lowered
  lowered="$(printf '%s' "${HTTP_BODY}" | tr '[:upper:]' '[:lower:]')"
  [[ "${HTTP_STATUS}" == "409" ]] || [[ "${lowered}" == *"already exists"* ]] || [[ "${lowered}" == *"duplicate"* ]]
}

api_get() {
  local token="$1"
  local path="$2"
  api_request GET "${path}" "__NO_BODY__" "${token}"
  http_ok || fail "GET ${path} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
  printf '%s' "${HTTP_BODY}"
}

api_get_optional() {
  local token="$1"
  local path="$2"
  api_request GET "${path}" "__NO_BODY__" "${token}"
  if http_ok; then
    printf '%s' "${HTTP_BODY}"
    return 0
  fi
  if [[ "${HTTP_STATUS}" == "404" ]]; then
    printf ''
    return 0
  fi
  fail "GET ${path} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
}

login_token() {
  local username="$1"
  local password="$2"
  local label="$3"
  local attempt=0
  local payload
  payload="$(jq -nc --arg username "${username}" --arg password "${password}" '{username:$username,password:$password}')"
  while (( attempt < 8 )); do
    api_request POST "/auth/login" "${payload}" ""
    if http_ok; then
      local token
      token="$(json_get "${HTTP_BODY}" '.accessToken // empty')"
      [[ -n "${token}" ]] || fail "Login for ${label} returned no accessToken"
      printf '%s' "${token}"
      return 0
    fi
    if [[ "${HTTP_STATUS}" == "429" ]]; then
      local sleep_seconds=$((7 + attempt * 7))
      warn "Login rate-limited for ${label}; sleeping ${sleep_seconds}s before retry"
      sleep "${sleep_seconds}"
      attempt=$((attempt + 1))
      continue
    fi
    fail "Login failed for ${label}: ${HTTP_STATUS} ${HTTP_BODY}"
  done
  fail "Login failed for ${label} after retries"
}

assert_equals() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  [[ "${actual}" == "${expected}" ]] || fail "${label}: expected ${expected}, got ${actual}"
  VERIFIED_TOTAL=$((VERIFIED_TOTAL + 1))
}

assert_gte() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  (( actual >= expected )) || fail "${label}: expected >= ${expected}, got ${actual}"
  VERIFIED_TOTAL=$((VERIFIED_TOTAL + 1))
}

assert_non_empty_json() {
  local body="$1"
  local filter="$2"
  local label="$3"
  local result
  result="$(jq -r "${filter}" <<<"${body}")"
  [[ -n "${result}" && "${result}" != "null" && "${result}" != "0" ]] || fail "${label}: response was empty"
  VERIFIED_TOTAL=$((VERIFIED_TOTAL + 1))
}

retry_until_ok() {
  local token="$1"
  local path="$2"
  local tries="${3:-12}"
  local sleep_seconds="${4:-3}"
  local i=0
  while (( i < tries )); do
    api_request GET "${path}" "__NO_BODY__" "${token}"
    if http_ok; then
      printf '%s' "${HTTP_BODY}"
      return 0
    fi
    sleep "${sleep_seconds}"
    i=$((i + 1))
  done
  fail "GET ${path} did not return 2xx after retries"
}

retry_until_non_empty() {
  local token="$1"
  local path="$2"
  local jq_filter="$3"
  local label="$4"
  local tries="${5:-15}"
  local sleep_seconds="${6:-4}"
  local i=0
  while (( i < tries )); do
    api_request GET "${path}" "__NO_BODY__" "${token}"
    if http_ok; then
      local result
      result="$(jq -r "${jq_filter}" <<<"${HTTP_BODY}")"
      if [[ -n "${result}" && "${result}" != "null" && "${result}" != "0" ]]; then
        VERIFIED_TOTAL=$((VERIFIED_TOTAL + 1))
        printf '%s' "${HTTP_BODY}"
        return 0
      fi
    fi
    sleep "${sleep_seconds}"
    i=$((i + 1))
  done
  fail "${label}: projection did not become non-empty in time"
}

create_or_resolve_entity() {
  local description="$1"
  local path="$2"
  local payload="$3"
  local resolver="$4"
  api_request POST "${path}" "${payload}" "${BOOTSTRAP_TOKEN}"
  if http_ok; then
    CREATED_TOTAL=$((CREATED_TOTAL + 1))
    json_get "${HTTP_BODY}" '.id // empty'
    return 0
  fi
  if duplicate_response; then
    local resolved_id
    resolved_id="$(eval "${resolver}")"
    [[ -n "${resolved_id}" ]] || fail "${description}: duplicate detected but resolver found nothing"
    REUSED_TOTAL=$((REUSED_TOTAL + 1))
    printf '%s' "${resolved_id}"
    return 0
  fi
  fail "${description}: create failed with ${HTTP_STATUS}: ${HTTP_BODY}"
}

create_or_resolve_reference() {
  local description="$1"
  local path="$2"
  local payload="$3"
  local resolver="$4"
  api_request POST "${path}" "${payload}" "${BOOTSTRAP_TOKEN}"
  if http_ok; then
    CREATED_TOTAL=$((CREATED_TOTAL + 1))
    printf '%s' "$(eval "${resolver}")"
    return 0
  fi
  if duplicate_response; then
    local resolved_value
    resolved_value="$(eval "${resolver}")"
    [[ -n "${resolved_value}" ]] || fail "${description}: duplicate detected but resolver found nothing"
    REUSED_TOTAL=$((REUSED_TOTAL + 1))
    printf '%s' "${resolved_value}"
    return 0
  fi
  fail "${description}: create failed with ${HTTP_STATUS}: ${HTTP_BODY}"
}

find_region_id_by_code() {
  local code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/regions?search=$(urlencode "${code}")&page=0&size=200")"
  jq -r --arg code "${code}" '.items[]? | select(.code == $code) | .id' <<<"${body}" | head -n1
}

find_outlet_id_by_code() {
  local code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/outlets?search=$(urlencode "${code}")&page=0&size=200")"
  jq -r --arg code "${code}" '.items[]? | select(.code == $code) | .id' <<<"${body}" | head -n1
}

find_user_id_by_username() {
  local username="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/users?search=$(urlencode "${username}")&page=0&size=200")"
  jq -r --arg username "${username}" '.items[]? | select(.username == $username) | .id' <<<"${body}" | head -n1
}

find_employee_id_by_code() {
  local employee_code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/employees?search=$(urlencode "${employee_code}")&page=0&size=200")"
  jq -r --arg employee_code "${employee_code}" '.items[]? | select(.employeeCode == $employee_code) | .id' <<<"${body}" | head -n1
}

find_contract_id() {
  local employee_id="$1"
  local start_date="$2"
  local salary_type="$3"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/employees/${employee_id}/contracts")"
  jq -r --arg start_date "${start_date}" --arg salary_type "${salary_type}" '
    .[]? | select(.startDate == $start_date and .salaryType == $salary_type) | .id
  ' <<<"${body}" | head -n1
}

find_assignment_id() {
  local employee_id="$1"
  local outlet_id="$2"
  local start_date="$3"
  local position_title="$4"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/employees/${employee_id}/assignments")"
  jq -r --arg outlet_id "${outlet_id}" --arg start_date "${start_date}" --arg position_title "${position_title}" '
    .[]? | select((.outletId|tostring) == $outlet_id and .startDate == $start_date and .positionTitle == $position_title) | .id
  ' <<<"${body}" | head -n1
}

uom_exists_by_code() {
  local code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/units-of-measure")"
  jq -r --arg code "${code}" '[ .[]? | select(.code == $code) ] | length' <<<"${body}"
}

category_exists() {
  local path="$1"
  local code="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "${path}")"
  jq -r --arg code "${code}" '[ .[]? | select(.code == $code) ] | length' <<<"${body}"
}

find_ingredient_id_by_code() {
  local code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/ingredients?limit=500")"
  jq -r --arg code "${code}" '.[]? | select(.code == $code) | .id' <<<"${body}" | head -n1
}

find_product_id_by_code() {
  local code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/products?limit=500")"
  jq -r --arg code "${code}" '.[]? | select(.code == $code) | .id' <<<"${body}" | head -n1
}

find_recipe_id_by_code() {
  local recipe_code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/recipes")"
  jq -r --arg recipe_code "${recipe_code}" '.[]? | select(.recipeCode == $recipe_code) | .id' <<<"${body}" | head -n1
}

find_recipe_version_id() {
  local recipe_id="$1"
  local version_no="$2"
  local effective_from="$3"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/recipe-versions?recipeId=${recipe_id}")"
  jq -r --arg version_no "${version_no}" --arg effective_from "${effective_from}" '
    .[]? | select(.versionNo == $version_no and .effectiveFrom == $effective_from) | .id
  ' <<<"${body}" | head -n1
}

find_tax_rate_id() {
  local product_id="$1"
  local effective_from="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/tax-rates")"
  jq -r --arg product_id "${product_id}" --arg effective_from "${effective_from}" '
    .[]? | select((.productId|tostring) == $product_id and .effectiveFrom == $effective_from) | .id
  ' <<<"${body}" | head -n1
}

find_product_price_id() {
  local product_id="$1"
  local scope_type="$2"
  local scope_id="$3"
  local price_type="$4"
  local effective_from="$5"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/product-prices")"
  jq -r \
    --arg product_id "${product_id}" \
    --arg scope_type "${scope_type}" \
    --arg scope_id "${scope_id}" \
    --arg price_type "${price_type}" \
    --arg effective_from "${effective_from}" '
    .[]?
    | select((.productId|tostring) == $product_id)
    | select(.scopeType == $scope_type)
    | select(((.scopeId|tostring) == $scope_id) or (($scope_id == "null" or $scope_id == "") and (.scopeId == null)))
    | select(.priceType == $price_type and .effectiveFrom == $effective_from)
    | .id
  ' <<<"${body}" | head -n1
}

find_product_availability_value() {
  local product_id="$1"
  local outlet_id="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/product-availability?productId=${product_id}&outletId=${outlet_id}")"
  jq -r '.[0].available // empty' <<<"${body}"
}

find_supplier_id_by_code() {
  local supplier_code="$1"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/suppliers")"
  jq -r --arg supplier_code "${supplier_code}" '.[]? | select(.supplierCode == $supplier_code) | .id' <<<"${body}" | head -n1
}

find_purchase_order_id_by_note() {
  local outlet_id="$1"
  local supplier_id="$2"
  local note="$3"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/purchase-orders?outletId=${outlet_id}&supplierId=${supplier_id}&limit=200")"
  jq -r --arg note "${note}" '.[]? | select(.note == $note) | .id' <<<"${body}" | head -n1
}

find_goods_receipt_id_by_note() {
  local purchase_order_id="$1"
  local note="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/goods-receipts?purchaseOrderId=${purchase_order_id}&limit=200")"
  jq -r --arg note "${note}" '.[]? | select(.note == $note) | .id' <<<"${body}" | head -n1
}

find_supplier_invoice_id_by_number() {
  local supplier_id="$1"
  local outlet_id="$2"
  local invoice_number="$3"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/supplier-invoices?supplierId=${supplier_id}&outletId=${outlet_id}&limit=200")"
  jq -r --arg invoice_number "${invoice_number}" '.[]? | select(.invoiceNumber == $invoice_number) | .id' <<<"${body}" | head -n1
}

find_supplier_payment_id_by_transaction_ref() {
  local supplier_id="$1"
  local transaction_ref="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/supplier-payments?supplierId=${supplier_id}&limit=200")"
  jq -r --arg transaction_ref "${transaction_ref}" '.[]? | select(.transactionRef == $transaction_ref) | .id' <<<"${body}" | head -n1
}

find_shift_schedule_id() {
  local outlet_id="$1"
  local shift_date="$2"
  local shift_name="$3"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/shift-schedules?outletId=${outlet_id}&fromDate=${shift_date}&toDate=${shift_date}&limit=50")"
  jq -r --arg shift_name "${shift_name}" '.[]? | select(.shiftName == $shift_name) | .id' <<<"${body}" | head -n1
}

find_shift_assignment_id() {
  local shift_schedule_id="$1"
  local employee_id="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/shift-assignments?shiftScheduleId=${shift_schedule_id}&limit=100")"
  jq -r --arg employee_id "${employee_id}" '.[]? | select((.employeeId|tostring) == $employee_id) | .id' <<<"${body}" | head -n1
}

get_attendance_approval_status() {
  local shift_assignment_id="$1"
  api_request GET "/attendance-approvals/${shift_assignment_id}" "__NO_BODY__" "${BOOTSTRAP_TOKEN}"
  if http_ok; then
    jq -r '.status // empty' <<<"${HTTP_BODY}"
    return 0
  fi
  if [[ "${HTTP_STATUS}" == "404" ]]; then
    printf ''
    return 0
  fi
  fail "GET /attendance-approvals/${shift_assignment_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
}

find_payroll_period_id_by_name() {
  local region_id="$1"
  local name="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-periods?regionId=${region_id}")"
  jq -r --arg name "${name}" '.[]? | select(.name == $name) | .id' <<<"${body}" | head -n1
}

find_payroll_run_id_by_note() {
  local region_id="$1"
  local note="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs?regionId=${region_id}&limit=200")"
  jq -r --arg note "${note}" '.[]? | select(.note == $note) | .id' <<<"${body}" | head -n1
}

find_pos_session_id() {
  local outlet_id="$1"
  local terminal_id="$2"
  local business_date="$3"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/pos-sessions?outletId=${outlet_id}&terminalId=$(urlencode "${terminal_id}")&businessDate=${business_date}&limit=20")"
  jq -r '.[]? | .id' <<<"${body}" | head -n1
}

find_sale_order_id_by_note() {
  local pos_session_id="$1"
  local note="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/sale-orders?posSessionId=${pos_session_id}&limit=200")"
  jq -r --arg note "${note}" '.[]? | select(.note == $note) | .id' <<<"${body}" | head -n1
}

find_stock_count_session_id_by_note() {
  local outlet_id="$1"
  local note="$2"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/stock-count-sessions?outletId=${outlet_id}&page=0&size=200")"
  jq -r --arg note "${note}" '.items[]? | select(.note == $note) | .id' <<<"${body}" | head -n1
}

inventory_txn_exists() {
  local outlet_id="$1"
  local ingredient_id="$2"
  local txn_type="$3"
  local business_date="$4"
  local source_type="$5"
  local body
  body="$(api_get "${BOOTSTRAP_TOKEN}" "/inventory-transactions?outletId=${outlet_id}&ingredientId=${ingredient_id}&txnType=${txn_type}&from=${business_date}&to=${business_date}&sourceType=${source_type}&page=0&size=5")"
  jq -r '.items | length' <<<"${body}"
}

product_is_flagship_only() {
  local product_code="$1"
  jq -r --arg product_code "${product_code}" '.[] | select(.code == $product_code) | .flagshipOnly' <<<"${PRODUCT_SPECS}"
}

product_has_delivery_price() {
  local product_code="$1"
  jq -r --arg product_code "${product_code}" '.[] | select(.code == $product_code) | (.deliveryPrice != null)' <<<"${PRODUCT_SPECS}"
}

product_outlet_is_available() {
  local product_code="$1"
  local outlet_type="$2"
  local flagship_only
  flagship_only="$(product_is_flagship_only "${product_code}")"
  if [[ "${flagship_only}" == "true" && "${outlet_type}" != "FLAGSHIP" ]]; then
    printf 'false'
  else
    printf 'true'
  fi
}

product_codes_for_outlet() {
  local outlet_type="$1"
  jq -r --arg outlet_type "${outlet_type}" '
    .[]
    | select((.flagshipOnly == false) or ($outlet_type == "FLAGSHIP"))
    | .code
  ' <<<"${PRODUCT_SPECS}"
}

build_repeated_outlet_ids_query() {
  local region_code="$1"
  local result=""
  while IFS=$'\t' read -r outlet_code outlet_region_code; do
    [[ "${outlet_region_code}" == "${region_code}" ]] || continue
    local outlet_id
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    result="${result}outletIds=${outlet_id}&"
  done < <(jq -r '.[] | [.outletCode, .regionCode] | @tsv' <<<"${OUTLET_SPECS}")
  printf '%s' "${result%&}"
}

verify_endpoint_with_token() {
  local token="$1"
  local path="$2"
  local label="$3"
  api_request GET "${path}" "__NO_BODY__" "${token}"
  http_ok || fail "${label}: ${HTTP_STATUS} ${HTTP_BODY}"
  VERIFIED_TOTAL=$((VERIFIED_TOTAL + 1))
}

BOOTSTRAP_TOKEN=""
DATE_META=""
HISTORY_START=""
INITIAL_STOCK_DATE=""
WASTE_DATE=""
STOCK_COUNT_DATE=""
PREVIOUS_MONTH_START=""
PREVIOUS_MONTH_END=""
PREVIOUS_PAY_DATE=""
CURRENT_MONTH_START=""
CURRENT_PERIOD_END=""
CURRENT_PAY_DATE=""
PREVIOUS_YEAR_MONTH=""
CURRENT_YEAR_MONTH=""
DATE_SERIES_JSON=""
LOCAL_TODAY=""

REGION_IDS='{}'
OUTLET_IDS='{}'
USER_IDS='{}'
EMPLOYEE_IDS='{}'
INGREDIENT_IDS='{}'
PRODUCT_IDS='{}'
RECIPE_IDS='{}'
SUPPLIER_IDS='{}'
PAYROLL_PERIOD_IDS='{}'
PAYROLL_RUN_IDS='{}'
EMPLOYEE_ROSTER='[]'
ACCOUNT_SUMMARY='[]'

REGION_SPECS="$(cat <<'JSON'
[
  {"code":"NORTH","name":"Mien Bac"},
  {"code":"SOUTH","name":"Mien Nam"}
]
JSON
)"

OUTLET_SPECS="$(cat <<'JSON'
[
  {"regionCode":"NORTH","outletCode":"HN_FLAGSHIP","outletName":"Ha Noi Flagship","outletType":"FLAGSHIP","address":"1 Trang Tien, Ha Noi","phone":"02473000001","email":"hn.flagship@seed.fern.local"},
  {"regionCode":"NORTH","outletCode":"HN_STD_01","outletName":"Ha Noi Standard 01","outletType":"STANDARD","address":"16 Kim Ma, Ha Noi","phone":"02473000002","email":"hn.std01@seed.fern.local"},
  {"regionCode":"NORTH","outletCode":"HN_STD_02","outletName":"Ha Noi Standard 02","outletType":"STANDARD","address":"88 Cau Giay, Ha Noi","phone":"02473000003","email":"hn.std02@seed.fern.local"},
  {"regionCode":"SOUTH","outletCode":"HCM_FLAGSHIP","outletName":"Ho Chi Minh Flagship","outletType":"FLAGSHIP","address":"2 Nguyen Hue, Ho Chi Minh","phone":"02873000001","email":"hcm.flagship@seed.fern.local"},
  {"regionCode":"SOUTH","outletCode":"HCM_STD_01","outletName":"Ho Chi Minh Standard 01","outletType":"STANDARD","address":"12 Le Loi, Ho Chi Minh","phone":"02873000002","email":"hcm.std01@seed.fern.local"},
  {"regionCode":"SOUTH","outletCode":"HCM_STD_02","outletName":"Ho Chi Minh Standard 02","outletType":"STANDARD","address":"99 Phan Xich Long, Ho Chi Minh","phone":"02873000003","email":"hcm.std02@seed.fern.local"}
]
JSON
)"

UOM_SPECS="$(cat <<'JSON'
[
  {"code":"GRAM","name":"Gram","symbol":"g"},
  {"code":"ML","name":"Milliliter","symbol":"ml"},
  {"code":"CUP","name":"Cup","symbol":"cup"},
  {"code":"PIECE","name":"Piece","symbol":"pc"},
  {"code":"PORTION","name":"Portion","symbol":"portion"}
]
JSON
)"

INGREDIENT_CATEGORY_SPECS="$(cat <<'JSON'
[
  {"code":"COFFEE_BASE","name":"Coffee Base","description":"Coffee beans and brewing inputs","active":true},
  {"code":"DAIRY_POWDER","name":"Dairy And Powder","description":"Milk, cream, powder and cultured base","active":true},
  {"code":"TEA_SYRUP","name":"Tea And Syrup","description":"Tea leaves, syrup and sweetener","active":true},
  {"code":"PACK_BAKERY","name":"Packaging And Bakery","description":"Packaging, bakery and food prep inputs","active":true}
]
JSON
)"

PRODUCT_CATEGORY_SPECS="$(cat <<'JSON'
[
  {"code":"COFFEE","name":"Coffee","description":"Coffee classics","active":true},
  {"code":"TEA","name":"Tea","description":"Tea menu","active":true},
  {"code":"SIGNATURE","name":"Signature","description":"Signature beverages","active":true},
  {"code":"BAKERY","name":"Bakery","description":"Bakery and snack items","active":true},
  {"code":"FRESH_BAR","name":"Fresh Bar","description":"Fresh and yogurt drinks","active":true}
]
JSON
)"

INGREDIENT_SPECS="$(cat <<'JSON'
[
  {"code":"ING_ARABICA_BEANS","name":"Arabica Beans","categoryCode":"COFFEE_BASE","baseUomCode":"GRAM","minStockLevel":5000,"maxStockLevel":40000,"status":"ACTIVE"},
  {"code":"ING_ROBUSTA_BEANS","name":"Robusta Beans","categoryCode":"COFFEE_BASE","baseUomCode":"GRAM","minStockLevel":6000,"maxStockLevel":45000,"status":"ACTIVE"},
  {"code":"ING_FILTER_WATER","name":"Filter Water","categoryCode":"COFFEE_BASE","baseUomCode":"ML","minStockLevel":20000,"maxStockLevel":160000,"status":"ACTIVE"},
  {"code":"ING_FRESH_MILK","name":"Fresh Milk","categoryCode":"DAIRY_POWDER","baseUomCode":"ML","minStockLevel":12000,"maxStockLevel":90000,"status":"ACTIVE"},
  {"code":"ING_CONDENSED_MILK","name":"Condensed Milk","categoryCode":"DAIRY_POWDER","baseUomCode":"ML","minStockLevel":4000,"maxStockLevel":30000,"status":"ACTIVE"},
  {"code":"ING_WHIPPING_CREAM","name":"Whipping Cream","categoryCode":"DAIRY_POWDER","baseUomCode":"ML","minStockLevel":3000,"maxStockLevel":20000,"status":"ACTIVE"},
  {"code":"ING_MATCHA_POWDER","name":"Matcha Powder","categoryCode":"DAIRY_POWDER","baseUomCode":"GRAM","minStockLevel":1000,"maxStockLevel":9000,"status":"ACTIVE"},
  {"code":"ING_CHOCOLATE_POWDER","name":"Chocolate Powder","categoryCode":"DAIRY_POWDER","baseUomCode":"GRAM","minStockLevel":1000,"maxStockLevel":8000,"status":"ACTIVE"},
  {"code":"ING_BLACK_TEA","name":"Black Tea","categoryCode":"TEA_SYRUP","baseUomCode":"GRAM","minStockLevel":1200,"maxStockLevel":10000,"status":"ACTIVE"},
  {"code":"ING_OOLONG_TEA","name":"Oolong Tea","categoryCode":"TEA_SYRUP","baseUomCode":"GRAM","minStockLevel":1200,"maxStockLevel":10000,"status":"ACTIVE"},
  {"code":"ING_PEACH_SYRUP","name":"Peach Syrup","categoryCode":"TEA_SYRUP","baseUomCode":"ML","minStockLevel":2000,"maxStockLevel":16000,"status":"ACTIVE"},
  {"code":"ING_LEMON_SYRUP","name":"Lemon Syrup","categoryCode":"TEA_SYRUP","baseUomCode":"ML","minStockLevel":2000,"maxStockLevel":16000,"status":"ACTIVE"},
  {"code":"ING_CARAMEL_SYRUP","name":"Caramel Syrup","categoryCode":"TEA_SYRUP","baseUomCode":"ML","minStockLevel":2000,"maxStockLevel":16000,"status":"ACTIVE"},
  {"code":"ING_SUGAR","name":"Sugar","categoryCode":"TEA_SYRUP","baseUomCode":"GRAM","minStockLevel":3000,"maxStockLevel":24000,"status":"ACTIVE"},
  {"code":"ING_ICE","name":"Ice","categoryCode":"TEA_SYRUP","baseUomCode":"GRAM","minStockLevel":20000,"maxStockLevel":100000,"status":"ACTIVE"},
  {"code":"ING_SEA_SALT","name":"Sea Salt","categoryCode":"TEA_SYRUP","baseUomCode":"GRAM","minStockLevel":300,"maxStockLevel":4000,"status":"ACTIVE"},
  {"code":"ING_ORANGE_JUICE_BASE","name":"Orange Juice Base","categoryCode":"TEA_SYRUP","baseUomCode":"ML","minStockLevel":3000,"maxStockLevel":25000,"status":"ACTIVE"},
  {"code":"ING_YOGURT_BASE","name":"Yogurt Base","categoryCode":"DAIRY_POWDER","baseUomCode":"ML","minStockLevel":3000,"maxStockLevel":25000,"status":"ACTIVE"},
  {"code":"ING_CUP_16OZ","name":"Cup 16Oz","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":300,"maxStockLevel":3000,"status":"ACTIVE"},
  {"code":"ING_LID_16OZ","name":"Lid 16Oz","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":300,"maxStockLevel":3000,"status":"ACTIVE"},
  {"code":"ING_STRAW","name":"Straw","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":300,"maxStockLevel":3000,"status":"ACTIVE"},
  {"code":"ING_CROISSANT_ITEM","name":"Croissant Item","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":40,"maxStockLevel":400,"status":"ACTIVE"},
  {"code":"ING_MUFFIN_ITEM","name":"Muffin Item","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":40,"maxStockLevel":400,"status":"ACTIVE"},
  {"code":"ING_TIRAMISU_SLICE_ITEM","name":"Tiramisu Slice Item","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":30,"maxStockLevel":250,"status":"ACTIVE"},
  {"code":"ING_SANDWICH_BREAD","name":"Sandwich Bread","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":60,"maxStockLevel":600,"status":"ACTIVE"},
  {"code":"ING_HAM_SLICE","name":"Ham Slice","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":80,"maxStockLevel":800,"status":"ACTIVE"},
  {"code":"ING_CHEESE_SLICE","name":"Cheese Slice","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":80,"maxStockLevel":800,"status":"ACTIVE"},
  {"code":"ING_BUTTER_PORTION","name":"Butter Portion","categoryCode":"PACK_BAKERY","baseUomCode":"PIECE","minStockLevel":80,"maxStockLevel":800,"status":"ACTIVE"}
]
JSON
)"

PRODUCT_SPECS="$(cat <<'JSON'
[
  {"code":"FB_ESPRESSO","name":"Espresso","categoryCode":"COFFEE","description":"Signature espresso shot","recipeCode":"RCP_FB_ESPRESSO","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"32000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_ARABICA_BEANS","uomCode":"GRAM","qty":"18.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_AMERICANO","name":"Americano","categoryCode":"COFFEE","description":"Americano over water","recipeCode":"RCP_FB_AMERICANO","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"35000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_ARABICA_BEANS","uomCode":"GRAM","qty":"18.00"},{"code":"ING_FILTER_WATER","uomCode":"ML","qty":"180.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_LATTE","name":"Latte","categoryCode":"COFFEE","description":"Latte with fresh milk","recipeCode":"RCP_FB_LATTE","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"52000.00","deliveryPrice":"56000.00","flagshipOnly":false,"ingredients":[{"code":"ING_ARABICA_BEANS","uomCode":"GRAM","qty":"18.00"},{"code":"ING_FRESH_MILK","uomCode":"ML","qty":"180.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_CAPPUCCINO","name":"Cappuccino","categoryCode":"COFFEE","description":"Foamy cappuccino","recipeCode":"RCP_FB_CAPPUCCINO","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"54000.00","deliveryPrice":"58000.00","flagshipOnly":false,"ingredients":[{"code":"ING_ARABICA_BEANS","uomCode":"GRAM","qty":"18.00"},{"code":"ING_FRESH_MILK","uomCode":"ML","qty":"150.00"},{"code":"ING_WHIPPING_CREAM","uomCode":"ML","qty":"20.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_MOCHA","name":"Mocha","categoryCode":"COFFEE","description":"Mocha with chocolate","recipeCode":"RCP_FB_MOCHA","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"59000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_ARABICA_BEANS","uomCode":"GRAM","qty":"18.00"},{"code":"ING_FRESH_MILK","uomCode":"ML","qty":"170.00"},{"code":"ING_CHOCOLATE_POWDER","uomCode":"GRAM","qty":"20.00"},{"code":"ING_WHIPPING_CREAM","uomCode":"ML","qty":"20.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_MATCHA_LATTE","name":"Matcha Latte","categoryCode":"SIGNATURE","description":"Matcha latte on ice","recipeCode":"RCP_FB_MATCHA_LATTE","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"56000.00","deliveryPrice":"60000.00","flagshipOnly":false,"ingredients":[{"code":"ING_MATCHA_POWDER","uomCode":"GRAM","qty":"10.00"},{"code":"ING_FRESH_MILK","uomCode":"ML","qty":"200.00"},{"code":"ING_SUGAR","uomCode":"GRAM","qty":"10.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_BLACK_TEA","name":"Black Tea","categoryCode":"TEA","description":"Classic black tea","recipeCode":"RCP_FB_BLACK_TEA","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"34000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_BLACK_TEA","uomCode":"GRAM","qty":"8.00"},{"code":"ING_FILTER_WATER","uomCode":"ML","qty":"200.00"},{"code":"ING_SUGAR","uomCode":"GRAM","qty":"12.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_MILK_TEA","name":"Milk Tea","categoryCode":"TEA","description":"Oolong milk tea","recipeCode":"RCP_FB_MILK_TEA","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"47000.00","deliveryPrice":"51000.00","flagshipOnly":false,"ingredients":[{"code":"ING_OOLONG_TEA","uomCode":"GRAM","qty":"8.00"},{"code":"ING_FRESH_MILK","uomCode":"ML","qty":"120.00"},{"code":"ING_FILTER_WATER","uomCode":"ML","qty":"140.00"},{"code":"ING_SUGAR","uomCode":"GRAM","qty":"12.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_PEACH_TEA","name":"Peach Tea","categoryCode":"TEA","description":"Peach tea refresh","recipeCode":"RCP_FB_PEACH_TEA","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"45000.00","deliveryPrice":"49000.00","flagshipOnly":false,"ingredients":[{"code":"ING_BLACK_TEA","uomCode":"GRAM","qty":"6.00"},{"code":"ING_PEACH_SYRUP","uomCode":"ML","qty":"25.00"},{"code":"ING_FILTER_WATER","uomCode":"ML","qty":"180.00"},{"code":"ING_ICE","uomCode":"GRAM","qty":"80.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_LEMON_TEA","name":"Lemon Tea","categoryCode":"TEA","description":"Lemon tea refresh","recipeCode":"RCP_FB_LEMON_TEA","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"43000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_BLACK_TEA","uomCode":"GRAM","qty":"6.00"},{"code":"ING_LEMON_SYRUP","uomCode":"ML","qty":"25.00"},{"code":"ING_FILTER_WATER","uomCode":"ML","qty":"180.00"},{"code":"ING_ICE","uomCode":"GRAM","qty":"80.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_COLD_BREW","name":"Cold Brew","categoryCode":"COFFEE","description":"Slow steeped cold brew","recipeCode":"RCP_FB_COLD_BREW","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"49000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_ARABICA_BEANS","uomCode":"GRAM","qty":"22.00"},{"code":"ING_FILTER_WATER","uomCode":"ML","qty":"220.00"},{"code":"ING_ICE","uomCode":"GRAM","qty":"90.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_SALT_COFFEE","name":"Salt Coffee","categoryCode":"SIGNATURE","description":"Salt coffee signature","recipeCode":"RCP_FB_SALT_COFFEE","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"51000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_ROBUSTA_BEANS","uomCode":"GRAM","qty":"20.00"},{"code":"ING_CONDENSED_MILK","uomCode":"ML","qty":"30.00"},{"code":"ING_WHIPPING_CREAM","uomCode":"ML","qty":"25.00"},{"code":"ING_SEA_SALT","uomCode":"GRAM","qty":"1.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_BAC_XIU","name":"Bac Xiu","categoryCode":"SIGNATURE","description":"Bac xiu with condensed milk","recipeCode":"RCP_FB_BAC_XIU","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"45000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_ROBUSTA_BEANS","uomCode":"GRAM","qty":"12.00"},{"code":"ING_FRESH_MILK","uomCode":"ML","qty":"120.00"},{"code":"ING_CONDENSED_MILK","uomCode":"ML","qty":"40.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_CHOCOLATE","name":"Chocolate","categoryCode":"SIGNATURE","description":"Chocolate drink","recipeCode":"RCP_FB_CHOCOLATE","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"50000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_CHOCOLATE_POWDER","uomCode":"GRAM","qty":"25.00"},{"code":"ING_FRESH_MILK","uomCode":"ML","qty":"180.00"},{"code":"ING_SUGAR","uomCode":"GRAM","qty":"8.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_CROISSANT","name":"Croissant","categoryCode":"BAKERY","description":"Butter croissant","recipeCode":"RCP_FB_CROISSANT","yieldUomCode":"PORTION","yieldQty":1,"taxPercent":"8.00","retailPrice":"36000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_CROISSANT_ITEM","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_MUFFIN","name":"Muffin","categoryCode":"BAKERY","description":"Daily muffin","recipeCode":"RCP_FB_MUFFIN","yieldUomCode":"PORTION","yieldQty":1,"taxPercent":"8.00","retailPrice":"34000.00","deliveryPrice":null,"flagshipOnly":false,"ingredients":[{"code":"ING_MUFFIN_ITEM","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_TIRAMISU_SLICE","name":"Tiramisu Slice","categoryCode":"BAKERY","description":"Tiramisu slice","recipeCode":"RCP_FB_TIRAMISU_SLICE","yieldUomCode":"PORTION","yieldQty":1,"taxPercent":"8.00","retailPrice":"52000.00","deliveryPrice":null,"flagshipOnly":true,"ingredients":[{"code":"ING_TIRAMISU_SLICE_ITEM","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_HAM_CHEESE_SANDWICH","name":"Ham Cheese Sandwich","categoryCode":"BAKERY","description":"Pressed sandwich","recipeCode":"RCP_FB_HAM_CHEESE_SANDWICH","yieldUomCode":"PORTION","yieldQty":1,"taxPercent":"8.00","retailPrice":"64000.00","deliveryPrice":"70000.00","flagshipOnly":true,"ingredients":[{"code":"ING_SANDWICH_BREAD","uomCode":"PIECE","qty":"2.00"},{"code":"ING_HAM_SLICE","uomCode":"PIECE","qty":"2.00"},{"code":"ING_CHEESE_SLICE","uomCode":"PIECE","qty":"1.00"},{"code":"ING_BUTTER_PORTION","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_ORANGE_JUICE","name":"Orange Juice","categoryCode":"FRESH_BAR","description":"Orange juice over ice","recipeCode":"RCP_FB_ORANGE_JUICE","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"48000.00","deliveryPrice":null,"flagshipOnly":true,"ingredients":[{"code":"ING_ORANGE_JUICE_BASE","uomCode":"ML","qty":"220.00"},{"code":"ING_ICE","uomCode":"GRAM","qty":"100.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]},
  {"code":"FB_YOGURT_DRINK","name":"Yogurt Drink","categoryCode":"FRESH_BAR","description":"Yogurt citrus drink","recipeCode":"RCP_FB_YOGURT_DRINK","yieldUomCode":"CUP","yieldQty":1,"taxPercent":"8.00","retailPrice":"49000.00","deliveryPrice":null,"flagshipOnly":true,"ingredients":[{"code":"ING_YOGURT_BASE","uomCode":"ML","qty":"180.00"},{"code":"ING_ORANGE_JUICE_BASE","uomCode":"ML","qty":"80.00"},{"code":"ING_CUP_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_LID_16OZ","uomCode":"PIECE","qty":"1.00"},{"code":"ING_STRAW","uomCode":"PIECE","qty":"1.00"}]}
]
JSON
)"

OUTLET_OVERRIDE_PRICE_SPECS="$(cat <<'JSON'
[
  {"productCode":"FB_ESPRESSO","outletCode":"HN_FLAGSHIP","priceType":"RETAIL","priceValue":"35000.00"},
  {"productCode":"FB_LATTE","outletCode":"HN_FLAGSHIP","priceType":"RETAIL","priceValue":"56000.00"},
  {"productCode":"FB_MATCHA_LATTE","outletCode":"HCM_FLAGSHIP","priceType":"RETAIL","priceValue":"61000.00"},
  {"productCode":"FB_TIRAMISU_SLICE","outletCode":"HCM_FLAGSHIP","priceType":"RETAIL","priceValue":"56000.00"}
]
JSON
)"

SUPPLIER_SPECS="$(cat <<'JSON'
[
  {"code":"SUP_BEAN_CRAFT","name":"Bean Craft Supply","email":"beans@seed.fern.local","phone":"0900000001","address":"District 1 Supply Hub","status":"INACTIVE"},
  {"code":"SUP_DAIRY_DEPOT","name":"Dairy Depot","email":"dairy@seed.fern.local","phone":"0900000002","address":"Cold Chain Zone","status":"INACTIVE"},
  {"code":"SUP_TEA_SYRUP","name":"Tea Syrup Trading","email":"tea@seed.fern.local","phone":"0900000003","address":"Tea Market Warehouse","status":"INACTIVE"},
  {"code":"SUP_BAKERY_HUB","name":"Bakery Hub","email":"bakery@seed.fern.local","phone":"0900000004","address":"Central Bakery Park","status":"INACTIVE"}
]
JSON
)"

build_user_specs() {
  local users='[]'
  while IFS=$'\t' read -r outlet_code outlet_name region_code outlet_type; do
    local outlet_slug
    outlet_slug="$(slugify "${outlet_code}")"
    users="$(jq -c \
      --arg username "seed-om-${outlet_slug}" \
      --arg fullName "Quan ly ${outlet_name}" \
      --arg email "seed-om-${outlet_slug}@seed.fern.local" \
      --arg phone "$(phone_for_key "091" "seed-om-${outlet_slug}")" \
      --argjson roles '["outlet_manager"]' \
      --argjson regionCodes "[\"${region_code}\"]" \
      --argjson outletCodes "[\"${outlet_code}\"]" \
      '. + [{
          username:$username,
          fullName:$fullName,
          email:$email,
          phone:$phone,
          roles:$roles,
          system:false,
          regionCodes:$regionCodes,
          outletCodes:$outletCodes,
          userKind:"outlet_manager"
      }]' <<<"${users}")"
    users="$(jq -c \
      --arg username "seed-staff-${outlet_slug}-01" \
      --arg fullName "Thu ngan ${outlet_name} A" \
      --arg email "seed-staff-${outlet_slug}-01@seed.fern.local" \
      --arg phone "$(phone_for_key "092" "seed-staff-${outlet_slug}-01")" \
      --argjson roles '["staff"]' \
      --argjson regionCodes "[\"${region_code}\"]" \
      --argjson outletCodes "[\"${outlet_code}\"]" \
      '. + [{
          username:$username,
          fullName:$fullName,
          email:$email,
          phone:$phone,
          roles:$roles,
          system:false,
          regionCodes:$regionCodes,
          outletCodes:$outletCodes,
          userKind:"staff"
      }]' <<<"${users}")"
    users="$(jq -c \
      --arg username "seed-staff-${outlet_slug}-02" \
      --arg fullName "Thu ngan ${outlet_name} B" \
      --arg email "seed-staff-${outlet_slug}-02@seed.fern.local" \
      --arg phone "$(phone_for_key "093" "seed-staff-${outlet_slug}-02")" \
      --argjson roles '["staff"]' \
      --argjson regionCodes "[\"${region_code}\"]" \
      --argjson outletCodes "[\"${outlet_code}\"]" \
      '. + [{
          username:$username,
          fullName:$fullName,
          email:$email,
          phone:$phone,
          roles:$roles,
          system:false,
          regionCodes:$regionCodes,
          outletCodes:$outletCodes,
          userKind:"staff"
      }]' <<<"${users}")"
  done < <(jq -r '.[] | [.outletCode,.outletName,.regionCode,.outletType] | @tsv' <<<"${OUTLET_SPECS}")

  while IFS=$'\t' read -r region_code region_name; do
    local region_slug
    region_slug="$(slugify "${region_code}")"
    users="$(jq -c \
      --arg username "seed-rm-${region_slug}" \
      --arg fullName "Quan ly vung ${region_name}" \
      --arg email "seed-rm-${region_slug}@seed.fern.local" \
      --arg phone "$(phone_for_key "094" "seed-rm-${region_slug}")" \
      --argjson roles '["outlet_manager","regional_finance"]' \
      --argjson regionCodes "[\"${region_code}\"]" \
      '. + [{
          username:$username,
          fullName:$fullName,
          email:$email,
          phone:$phone,
          roles:$roles,
          system:false,
          regionCodes:$regionCodes,
          outletCodes:[],
          userKind:"region_manager"
      }]' <<<"${users}")"
    users="$(jq -c \
      --arg username "seed-rf-${region_slug}" \
      --arg fullName "Tai chinh vung ${region_name}" \
      --arg email "seed-rf-${region_slug}@seed.fern.local" \
      --arg phone "$(phone_for_key "095" "seed-rf-${region_slug}")" \
      --argjson roles '["regional_finance"]' \
      --argjson regionCodes "[\"${region_code}\"]" \
      '. + [{
          username:$username,
          fullName:$fullName,
          email:$email,
          phone:$phone,
          roles:$roles,
          system:false,
          regionCodes:$regionCodes,
          outletCodes:[],
          userKind:"regional_finance"
      }]' <<<"${users}")"
  done < <(jq -r '.[] | [.code,.name] | @tsv' <<<"${REGION_SPECS}")

  users="$(jq -c '. + [
      {"username":"seed-hr-core","fullName":"Nhan su He thong","email":"seed-hr-core@seed.fern.local","phone":"0961000001","roles":["hr"],"system":true,"regionCodes":[],"outletCodes":[],"userKind":"hr"},
      {"username":"seed-finance-core","fullName":"Tai chinh He thong","email":"seed-finance-core@seed.fern.local","phone":"0961000002","roles":["finance"],"system":true,"regionCodes":[],"outletCodes":[],"userKind":"finance"},
      {"username":"seed-product-manager","fullName":"Quan ly San pham","email":"seed-product-manager@seed.fern.local","phone":"0961000003","roles":["product_manager"],"system":true,"regionCodes":[],"outletCodes":[],"userKind":"product_manager"},
      {"username":"seed-audit-viewer","fullName":"Kiem soat Audit","email":"seed-audit-viewer@seed.fern.local","phone":"0961000004","roles":["audit_viewer"],"system":true,"regionCodes":[],"outletCodes":[],"userKind":"audit_viewer"},
      {"username":"seed-system-admin","fullName":"Quan tri He thong","email":"seed-system-admin@seed.fern.local","phone":"0961000005","roles":["system_admin"],"system":true,"regionCodes":[],"outletCodes":[],"userKind":"system_admin"}
    ]' <<<"${users}")"

  printf '%s' "${users}"
}

build_employee_specs() {
  local specs='[]'
  local global_index=0
  while IFS=$'\t' read -r outlet_code outlet_name region_code outlet_type; do
    local outlet_slug
    outlet_slug="$(slugify "${outlet_code}")"
    local om_count=1
    local lead_count=1
    local cashier_count=2
    local barista_count=2
    local clerk_count=1
    local service_count=1
    if [[ "${outlet_type}" == "FLAGSHIP" ]]; then
      lead_count=2
    fi

    local i
    for (( i = 1; i <= om_count; i++ )); do
      specs="$(append_employee_spec "${specs}" "${global_index}" "${region_code}" "${outlet_code}" "OM" "${i}" "Outlet Manager" "FULL_TIME" "MONTHLY" "15000000.00" "seed-om-${outlet_slug}")"
      global_index=$((global_index + 1))
    done
    for (( i = 1; i <= lead_count; i++ )); do
      specs="$(append_employee_spec "${specs}" "${global_index}" "${region_code}" "${outlet_code}" "LD" "${i}" "Shift Lead" "FULL_TIME" "MONTHLY" "11000000.00" "")"
      global_index=$((global_index + 1))
    done
    for (( i = 1; i <= cashier_count; i++ )); do
      specs="$(append_employee_spec "${specs}" "${global_index}" "${region_code}" "${outlet_code}" "CA" "${i}" "Cashier" "PART_TIME" "HOURLY" "38000.00" "seed-staff-${outlet_slug}-$(printf '%02d' "${i}")")"
      global_index=$((global_index + 1))
    done
    for (( i = 1; i <= barista_count; i++ )); do
      specs="$(append_employee_spec "${specs}" "${global_index}" "${region_code}" "${outlet_code}" "BR" "${i}" "Barista" "PART_TIME" "HOURLY" "42000.00" "")"
      global_index=$((global_index + 1))
    done
    for (( i = 1; i <= clerk_count; i++ )); do
      specs="$(append_employee_spec "${specs}" "${global_index}" "${region_code}" "${outlet_code}" "CL" "${i}" "Prep Clerk" "FULL_TIME" "MONTHLY" "8500000.00" "")"
      global_index=$((global_index + 1))
    done
    for (( i = 1; i <= service_count; i++ )); do
      specs="$(append_employee_spec "${specs}" "${global_index}" "${region_code}" "${outlet_code}" "SV" "${i}" "Service Crew" "PART_TIME" "HOURLY" "32000.00" "")"
      global_index=$((global_index + 1))
    done
  done < <(jq -r '.[] | [.outletCode,.outletName,.regionCode,.outletType] | @tsv' <<<"${OUTLET_SPECS}")
  printf '%s' "${specs}"
}

append_employee_spec() {
  local current_specs="$1"
  local global_index="$2"
  local region_code="$3"
  local outlet_code="$4"
  local role_code="$5"
  local role_index="$6"
  local position_title="$7"
  local employment_type="$8"
  local salary_type="$9"
  local base_salary="${10}"
  local username="${11}"
  local employee_code="SEED-EMP-${outlet_code}-${role_code}-$(printf '%02d' "${role_index}")"
  local full_name
  full_name="$(name_for_index $((global_index + 1)))"
  local gender
  gender="$(gender_for_index "${global_index}")"
  local hired_at
  hired_at="$(python3 - "${HISTORY_START}" "${global_index}" <<'PY'
import sys
from datetime import date, timedelta
start = date.fromisoformat(sys.argv[1])
offset = int(sys.argv[2])
print((start - timedelta(days=120 + offset)).isoformat())
PY
)"
  local dob
  dob="$(python3 - "${BUSINESS_DATE}" "${global_index}" <<'PY'
import sys
from datetime import date, timedelta
business = date.fromisoformat(sys.argv[1])
offset = int(sys.argv[2])
print((business - timedelta(days=(22 + (offset % 12)) * 365 + (offset % 27))).isoformat())
PY
)"
  jq -c \
    --arg employeeCode "${employee_code}" \
    --arg fullName "${full_name}" \
    --arg dob "${dob}" \
    --arg gender "${gender}" \
    --arg email "$(printf '%s@seed.fern.local' "$(printf '%s' "${employee_code}" | tr '[:upper:]' '[:lower:]')")" \
    --arg phone "097$(printf '%07d' $((1000000 + global_index)))" \
    --arg status "ACTIVE" \
    --arg hiredAt "${hired_at}" \
    --arg username "${username}" \
    --arg regionCode "${region_code}" \
    --arg outletCode "${outlet_code}" \
    --arg positionTitle "${position_title}" \
    --arg employmentType "${employment_type}" \
    --arg salaryType "${salary_type}" \
    --arg baseSalary "${base_salary}" \
    --arg contractStatus "ACTIVE" \
    --arg assignmentStatus "ACTIVE" \
    --arg startDate "${HISTORY_START}" \
    --arg roleCode "${role_code}" \
    --argjson globalIndex "${global_index}" \
    '. + [{
      employeeCode:$employeeCode,
      fullName:$fullName,
      dob:$dob,
      gender:$gender,
      email:$email,
      phone:$phone,
      status:$status,
      hiredAt:$hiredAt,
      username:$username,
      regionCode:$regionCode,
      outletCode:$outletCode,
      positionTitle:$positionTitle,
      employmentType:$employmentType,
      salaryType:$salaryType,
      baseSalary:$baseSalary,
      contractStatus:$contractStatus,
      assignmentStatus:$assignmentStatus,
      startDate:$startDate,
      primaryAssignment:true,
      roleCode:$roleCode,
      globalIndex:$globalIndex
    }]' <<<"${current_specs}"
}

build_supplier_po_lines() {
  local supplier_code="$1"
  case "${supplier_code}" in
    SUP_BEAN_CRAFT)
      jq -nc '[
        {"ingredientCode":"ING_ARABICA_BEANS","uomCode":"GRAM","qtyOrdered":"5000.00","expectedUnitPrice":"0.85","taxPercent":"8.00","note":"Arabica replenishment"},
        {"ingredientCode":"ING_ROBUSTA_BEANS","uomCode":"GRAM","qtyOrdered":"7000.00","expectedUnitPrice":"0.55","taxPercent":"8.00","note":"Robusta replenishment"},
        {"ingredientCode":"ING_SEA_SALT","uomCode":"GRAM","qtyOrdered":"500.00","expectedUnitPrice":"0.15","taxPercent":"8.00","note":"Salt replenishment"}
      ]'
      ;;
    SUP_DAIRY_DEPOT)
      jq -nc '[
        {"ingredientCode":"ING_FRESH_MILK","uomCode":"ML","qtyOrdered":"40000.00","expectedUnitPrice":"0.03","taxPercent":"8.00","note":"Fresh milk replenishment"},
        {"ingredientCode":"ING_CONDENSED_MILK","uomCode":"ML","qtyOrdered":"10000.00","expectedUnitPrice":"0.04","taxPercent":"8.00","note":"Condensed milk replenishment"},
        {"ingredientCode":"ING_WHIPPING_CREAM","uomCode":"ML","qtyOrdered":"8000.00","expectedUnitPrice":"0.06","taxPercent":"8.00","note":"Whipping cream replenishment"}
      ]'
      ;;
    SUP_TEA_SYRUP)
      jq -nc '[
        {"ingredientCode":"ING_BLACK_TEA","uomCode":"GRAM","qtyOrdered":"2500.00","expectedUnitPrice":"0.30","taxPercent":"8.00","note":"Black tea replenishment"},
        {"ingredientCode":"ING_PEACH_SYRUP","uomCode":"ML","qtyOrdered":"6000.00","expectedUnitPrice":"0.02","taxPercent":"8.00","note":"Peach syrup replenishment"},
        {"ingredientCode":"ING_LEMON_SYRUP","uomCode":"ML","qtyOrdered":"5000.00","expectedUnitPrice":"0.02","taxPercent":"8.00","note":"Lemon syrup replenishment"}
      ]'
      ;;
    SUP_BAKERY_HUB)
      jq -nc '[
        {"ingredientCode":"ING_CUP_16OZ","uomCode":"PIECE","qtyOrdered":"600.00","expectedUnitPrice":"0.12","taxPercent":"8.00","note":"Cup replenishment"},
        {"ingredientCode":"ING_LID_16OZ","uomCode":"PIECE","qtyOrdered":"600.00","expectedUnitPrice":"0.05","taxPercent":"8.00","note":"Lid replenishment"},
        {"ingredientCode":"ING_STRAW","uomCode":"PIECE","qtyOrdered":"600.00","expectedUnitPrice":"0.02","taxPercent":"8.00","note":"Straw replenishment"}
      ]'
      ;;
    *)
      fail "No PO line template for supplier ${supplier_code}"
      ;;
  esac
}

build_sale_lines_for_order() {
  local outlet_type="$1"
  local day_index="$2"
  local order_index="$3"
  local available_codes
  local -a code_array
  available_codes="$(product_codes_for_outlet "${outlet_type}")"
  while IFS= read -r code; do
    code_array+=("${code}")
  done <<<"${available_codes}"
  local total_codes="${#code_array[@]}"
  local line_count=$((1 + (order_index % 3)))
  local lines='[]'
  local line_idx
  for (( line_idx = 0; line_idx < line_count; line_idx++ )); do
    local product_code="${code_array[$(((day_index + order_index + line_idx) % total_codes))]}"
    local product_id
    product_id="$(map_get "${PRODUCT_IDS}" "${product_code}")"
    local qty="1.00"
    if (( (day_index + order_index + line_idx) % 4 == 0 )); then
      qty="2.00"
    fi
    lines="$(jq -c --argjson productId "${product_id}" --arg qty "${qty}" '. + [{productId:$productId,qty:($qty|tonumber),note:"seed line"}]' <<<"${lines}")"
  done
  printf '%s' "${lines}"
}

build_initial_stock_qty() {
  local ingredient_code="$1"
  case "${ingredient_code}" in
    ING_CUP_16OZ|ING_LID_16OZ|ING_STRAW) printf '1800.00' ;;
    ING_CROISSANT_ITEM|ING_MUFFIN_ITEM|ING_TIRAMISU_SLICE_ITEM|ING_SANDWICH_BREAD|ING_HAM_SLICE|ING_CHEESE_SLICE|ING_BUTTER_PORTION) printf '900.00' ;;
    ING_SEA_SALT) printf '3000.00' ;;
    ING_BLACK_TEA|ING_OOLONG_TEA|ING_MATCHA_POWDER|ING_CHOCOLATE_POWDER) printf '12000.00' ;;
    ING_ARABICA_BEANS|ING_ROBUSTA_BEANS|ING_SUGAR) printf '60000.00' ;;
    *) printf '90000.00' ;;
  esac
}

phase_bootstrap() {
  log "Phase 1/14: bootstrap auth"
  BOOTSTRAP_TOKEN="$(login_token "${BOOTSTRAP_USERNAME}" "${BOOTSTRAP_PASSWORD}" "bootstrap")"

  DATE_META="$(build_date_meta)"
  HISTORY_START="$(jq -r '.history_start' <<<"${DATE_META}")"
  INITIAL_STOCK_DATE="$(jq -r '.initial_stock_date' <<<"${DATE_META}")"
  WASTE_DATE="$(jq -r '.waste_date' <<<"${DATE_META}")"
  STOCK_COUNT_DATE="$(jq -r '.stock_count_date' <<<"${DATE_META}")"
  PREVIOUS_MONTH_START="$(jq -r '.previous_month_start' <<<"${DATE_META}")"
  PREVIOUS_MONTH_END="$(jq -r '.previous_month_end' <<<"${DATE_META}")"
  PREVIOUS_PAY_DATE="$(jq -r '.previous_pay_date' <<<"${DATE_META}")"
  CURRENT_MONTH_START="$(jq -r '.current_month_start' <<<"${DATE_META}")"
  CURRENT_PERIOD_END="$(jq -r '.current_period_end' <<<"${DATE_META}")"
  CURRENT_PAY_DATE="$(jq -r '.current_pay_date' <<<"${DATE_META}")"
  PREVIOUS_YEAR_MONTH="$(jq -r '.previous_year_month' <<<"${DATE_META}")"
  CURRENT_YEAR_MONTH="$(jq -r '.current_year_month' <<<"${DATE_META}")"
  DATE_SERIES_JSON="$(jq -c '.dates' <<<"${DATE_META}")"
  LOCAL_TODAY="$(python3 - "${FERN_TIMEZONE}" <<'PY'
import sys
from datetime import datetime
from zoneinfo import ZoneInfo
print(datetime.now(ZoneInfo(sys.argv[1])).date().isoformat())
PY
)"
  if [[ "${BUSINESS_DATE}" != "${LOCAL_TODAY}" ]]; then
    warn "BUSINESS_DATE=${BUSINESS_DATE} differs from local today ${LOCAL_TODAY}; /reports/revenue/outlet-stats/today will be verified for valid response shape only"
  fi

  local root_region_id
  root_region_id="$(find_region_id_by_code "ROOT")"
  if [[ -z "${root_region_id}" ]]; then
    local payload
    payload="$(jq -nc \
      --arg code "ROOT" \
      --arg currencyCode "${FERN_CURRENCY}" \
      --arg name "ROOT" \
      --arg timezoneName "${FERN_TIMEZONE}" \
      '{code:$code,parentRegionId:null,currencyCode:$currencyCode,name:$name,timezoneName:$timezoneName}')"
    root_region_id="$(create_or_resolve_entity "ROOT region" "/regions" "${payload}" "find_root_region_id")"
  fi
  REGION_IDS="$(map_put "${REGION_IDS}" "ROOT" "${root_region_id}")"
}

find_root_region_id() {
  find_region_id_by_code "ROOT"
}

phase_org() {
  log "Phase 2/14: org structure"
  local root_region_id
  root_region_id="$(map_get "${REGION_IDS}" "ROOT")"

  while IFS=$'\t' read -r region_code region_name; do
    local payload region_id
    payload="$(jq -nc \
      --arg code "${region_code}" \
      --argjson parentRegionId "${root_region_id}" \
      --arg currencyCode "${FERN_CURRENCY}" \
      --arg name "${region_name}" \
      --arg timezoneName "${FERN_TIMEZONE}" \
      '{code:$code,parentRegionId:$parentRegionId,currencyCode:$currencyCode,name:$name,timezoneName:$timezoneName}')"
    region_id="$(create_or_resolve_entity "region ${region_code}" "/regions" "${payload}" "find_region_id_by_code '${region_code}'")"
    REGION_IDS="$(map_put "${REGION_IDS}" "${region_code}" "${region_id}")"
  done < <(jq -r '.[] | [.code,.name] | @tsv' <<<"${REGION_SPECS}")

  while IFS=$'\t' read -r outlet_code outlet_name region_code outlet_type address phone email; do
    local region_id outlet_id payload
    region_id="$(map_get "${REGION_IDS}" "${region_code}")"
    payload="$(jq -nc \
      --argjson regionId "${region_id}" \
      --arg code "${outlet_code}" \
      --arg name "${outlet_name}" \
      --arg status "ACTIVE" \
      --arg address "${address}" \
      --arg phone "${phone}" \
      --arg email "${email}" \
      --arg openedAt "${HISTORY_START}" \
      '{regionId:$regionId,code:$code,name:$name,status:$status,address:$address,phone:$phone,email:$email,openedAt:$openedAt}')"
    outlet_id="$(create_or_resolve_entity "outlet ${outlet_code}" "/outlets" "${payload}" "find_outlet_id_by_code '${outlet_code}'")"
    OUTLET_IDS="$(map_put "${OUTLET_IDS}" "${outlet_code}" "${outlet_id}")"
  done < <(jq -r '.[] | [.outletCode,.outletName,.regionCode,.outletType,.address,.phone,.email] | @tsv' <<<"${OUTLET_SPECS}")
}

phase_users() {
  log "Phase 3/14: IAM users, roles and scopes"
  local user_specs
  user_specs="$(build_user_specs)"
  assert_equals "$(jq -r 'length' <<<"${user_specs}")" "27" "user spec count"

  while IFS= read -r user_spec; do
    local username full_name email phone user_id roles_json region_codes_json outlet_codes_json system_scope scope_region_ids scope_outlet_ids
    username="$(jq -r '.username' <<<"${user_spec}")"
    full_name="$(jq -r '.fullName' <<<"${user_spec}")"
    email="$(jq -r '.email' <<<"${user_spec}")"
    phone="$(jq -r '.phone' <<<"${user_spec}")"
    roles_json="$(jq -c '.roles' <<<"${user_spec}")"
    region_codes_json="$(jq -c '.regionCodes' <<<"${user_spec}")"
    outlet_codes_json="$(jq -c '.outletCodes' <<<"${user_spec}")"
    system_scope="$(jq -r '.system' <<<"${user_spec}")"

    local payload
    payload="$(jq -nc \
      --arg username "${username}" \
      --arg password "${DEMO_PASSWORD}" \
      --arg fullName "${full_name}" \
      --arg email "${email}" \
      --arg phone "${phone}" \
      --arg status "ACTIVE" \
      '{username:$username,password:$password,fullName:$fullName,email:$email,phone:$phone,status:$status}')"
    user_id="$(create_or_resolve_entity "user ${username}" "/users" "${payload}" "find_user_id_by_username '${username}'")"
    USER_IDS="$(map_put "${USER_IDS}" "${username}" "${user_id}")"

    payload="$(jq -nc --argjson roleCodes "${roles_json}" '{roleCodes:$roleCodes}')"
    api_request POST "/users/${user_id}/roles" "${payload}" "${BOOTSTRAP_TOKEN}"
    http_ok || fail "Assign roles for ${username} failed with ${HTTP_STATUS}: ${HTTP_BODY}"

    scope_region_ids="$(as_json_id_array "${REGION_IDS}" "${region_codes_json}")"
    scope_outlet_ids="$(as_json_id_array "${OUTLET_IDS}" "${outlet_codes_json}")"
    payload="$(jq -nc \
      --argjson system "${system_scope}" \
      --argjson regionIds "${scope_region_ids}" \
      --argjson outletIds "${scope_outlet_ids}" \
      '{system:$system,regionIds:$regionIds,outletIds:$outletIds}')"
    api_request POST "/users/${user_id}/scopes" "${payload}" "${BOOTSTRAP_TOKEN}"
    http_ok || fail "Assign scopes for ${username} failed with ${HTTP_STATUS}: ${HTTP_BODY}"

    ACCOUNT_SUMMARY="$(jq -c \
      --arg username "${username}" \
      --arg fullName "${full_name}" \
      --arg password "${DEMO_PASSWORD}" \
      --argjson roles "${roles_json}" \
      --argjson system "${system_scope}" \
      --argjson regionCodes "${region_codes_json}" \
      --argjson outletCodes "${outlet_codes_json}" \
      '. + [{
         username:$username,
         fullName:$fullName,
         password:$password,
         roles:$roles,
         system:$system,
         regionCodes:$regionCodes,
         outletCodes:$outletCodes
      }]' <<<"${ACCOUNT_SUMMARY}")"
  done < <(jq -c '.[]' <<<"${user_specs}")
}

phase_employees() {
  log "Phase 4/14: employee profiles, contracts and assignments"
  local employee_specs
  employee_specs="$(build_employee_specs)"
  assert_equals "$(jq -r 'length' <<<"${employee_specs}")" "50" "employee spec count"

  while IFS= read -r employee_spec; do
    local employee_code username user_account_id payload employee_id region_code outlet_code region_id outlet_id start_date position_title contract_id assignment_id
    employee_code="$(jq -r '.employeeCode' <<<"${employee_spec}")"
    username="$(jq -r '.username' <<<"${employee_spec}")"
    if [[ -n "${username}" ]]; then
      user_account_id="$(map_get "${USER_IDS}" "${username}")"
    else
      user_account_id=""
    fi
    region_code="$(jq -r '.regionCode' <<<"${employee_spec}")"
    outlet_code="$(jq -r '.outletCode' <<<"${employee_spec}")"
    region_id="$(map_get "${REGION_IDS}" "${region_code}")"
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    start_date="$(jq -r '.startDate' <<<"${employee_spec}")"
    position_title="$(jq -r '.positionTitle' <<<"${employee_spec}")"

    payload="$(jq -nc \
      --arg employeeCode "${employee_code}" \
      --arg fullName "$(jq -r '.fullName' <<<"${employee_spec}")" \
      --arg dob "$(jq -r '.dob' <<<"${employee_spec}")" \
      --arg gender "$(jq -r '.gender' <<<"${employee_spec}")" \
      --arg email "$(jq -r '.email' <<<"${employee_spec}")" \
      --arg phone "$(jq -r '.phone' <<<"${employee_spec}")" \
      --arg status "ACTIVE" \
      --arg hiredAt "$(jq -r '.hiredAt' <<<"${employee_spec}")" \
      --argjson userAccountId "${user_account_id:-null}" \
      '{employeeCode:$employeeCode,fullName:$fullName,dob:$dob,gender:$gender,email:$email,phone:$phone,status:$status,hiredAt:$hiredAt,userAccountId:$userAccountId}')"
    employee_id="$(create_or_resolve_entity "employee ${employee_code}" "/employees" "${payload}" "find_employee_id_by_code '${employee_code}'")"
    EMPLOYEE_IDS="$(map_put "${EMPLOYEE_IDS}" "${employee_code}" "${employee_id}")"

    contract_id="$(find_contract_id "${employee_id}" "${start_date}" "$(jq -r '.salaryType' <<<"${employee_spec}")")"
    if [[ -z "${contract_id}" ]]; then
      payload="$(jq -nc \
        --argjson employeeId "${employee_id}" \
        --arg employmentType "$(jq -r '.employmentType' <<<"${employee_spec}")" \
        --arg salaryType "$(jq -r '.salaryType' <<<"${employee_spec}")" \
        --arg baseSalary "$(jq -r '.baseSalary' <<<"${employee_spec}")" \
        --argjson regionId "${region_id}" \
        --arg taxCode "TAX-${employee_code}" \
        --arg contractStatus "ACTIVE" \
        --arg startDate "${start_date}" \
        '{employeeId:$employeeId,employmentType:$employmentType,salaryType:$salaryType,baseSalary:($baseSalary|tonumber),regionId:$regionId,taxCode:$taxCode,contractStatus:$contractStatus,startDate:$startDate}')"
      api_request POST "/employee-contracts" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create contract for ${employee_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      contract_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    assignment_id="$(find_assignment_id "${employee_id}" "${outlet_id}" "${start_date}" "${position_title}")"
    if [[ -z "${assignment_id}" ]]; then
      payload="$(jq -nc \
        --argjson employeeId "${employee_id}" \
        --argjson regionId "${region_id}" \
        --argjson outletId "${outlet_id}" \
        --arg positionTitle "${position_title}" \
        --arg startDate "${start_date}" \
        --argjson primaryAssignment true \
        --arg status "ACTIVE" \
        '{employeeId:$employeeId,regionId:$regionId,outletId:$outletId,positionTitle:$positionTitle,startDate:$startDate,primaryAssignment:$primaryAssignment,status:$status}')"
      api_request POST "/employee-assignments" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create assignment for ${employee_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      assignment_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    EMPLOYEE_ROSTER="$(jq -c \
      --arg employeeCode "${employee_code}" \
      --argjson employeeId "${employee_id}" \
      --arg regionCode "${region_code}" \
      --argjson regionId "${region_id}" \
      --arg outletCode "${outlet_code}" \
      --argjson outletId "${outlet_id}" \
      --arg positionTitle "${position_title}" \
      --arg roleCode "$(jq -r '.roleCode' <<<"${employee_spec}")" \
      --arg username "${username}" \
      --argjson globalIndex "$(jq -r '.globalIndex' <<<"${employee_spec}")" \
      '. + [{
        employeeCode:$employeeCode,
        employeeId:$employeeId,
        regionCode:$regionCode,
        regionId:$regionId,
        outletCode:$outletCode,
        outletId:$outletId,
        positionTitle:$positionTitle,
        roleCode:$roleCode,
        username:$username,
        globalIndex:$globalIndex
      }]' <<<"${EMPLOYEE_ROSTER}")"
  done < <(jq -c '.[]' <<<"${employee_specs}")
}

phase_catalog() {
  log "Phase 5/14: catalog, recipes, tax, prices and availability"

  while IFS=$'\t' read -r code name symbol; do
    if [[ "$(uom_exists_by_code "${code}")" -eq 0 ]]; then
      local payload
      payload="$(jq -nc --arg code "${code}" --arg name "${name}" --arg symbol "${symbol}" '{code:$code,name:$name,symbol:$symbol}')"
      create_or_resolve_reference "uom ${code}" "/units-of-measure" "${payload}" "uom_exists_by_code '${code}'" >/dev/null
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
  done < <(jq -r '.[] | [.code,.name,.symbol] | @tsv' <<<"${UOM_SPECS}")

  while IFS=$'\t' read -r code name description active; do
    if [[ "$(category_exists "/ingredient-categories" "${code}")" -eq 0 ]]; then
      local payload
      payload="$(jq -nc --arg code "${code}" --arg name "${name}" --arg description "${description}" --argjson active "${active}" '{code:$code,name:$name,description:$description,active:$active}')"
      create_or_resolve_reference "ingredient category ${code}" "/ingredient-categories" "${payload}" "category_exists '/ingredient-categories' '${code}'" >/dev/null
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
  done < <(jq -r '.[] | [.code,.name,.description,.active] | @tsv' <<<"${INGREDIENT_CATEGORY_SPECS}")

  while IFS=$'\t' read -r code name description active; do
    if [[ "$(category_exists "/product-categories" "${code}")" -eq 0 ]]; then
      local payload
      payload="$(jq -nc --arg code "${code}" --arg name "${name}" --arg description "${description}" --argjson active "${active}" '{code:$code,name:$name,description:$description,active:$active}')"
      create_or_resolve_reference "product category ${code}" "/product-categories" "${payload}" "category_exists '/product-categories' '${code}'" >/dev/null
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
  done < <(jq -r '.[] | [.code,.name,.description,.active] | @tsv' <<<"${PRODUCT_CATEGORY_SPECS}")

  while IFS= read -r ingredient_spec; do
    local code payload ingredient_id
    code="$(jq -r '.code' <<<"${ingredient_spec}")"
    payload="$(jq -nc \
      --arg code "${code}" \
      --arg name "$(jq -r '.name' <<<"${ingredient_spec}")" \
      --arg categoryCode "$(jq -r '.categoryCode' <<<"${ingredient_spec}")" \
      --arg baseUomCode "$(jq -r '.baseUomCode' <<<"${ingredient_spec}")" \
      --arg minStockLevel "$(jq -r '.minStockLevel' <<<"${ingredient_spec}")" \
      --arg maxStockLevel "$(jq -r '.maxStockLevel' <<<"${ingredient_spec}")" \
      --arg status "$(jq -r '.status' <<<"${ingredient_spec}")" \
      '{code:$code,name:$name,categoryCode:$categoryCode,baseUomCode:$baseUomCode,minStockLevel:($minStockLevel|tonumber),maxStockLevel:($maxStockLevel|tonumber),status:$status}')"
    ingredient_id="$(create_or_resolve_entity "ingredient ${code}" "/ingredients" "${payload}" "find_ingredient_id_by_code '${code}'")"
    INGREDIENT_IDS="$(map_put "${INGREDIENT_IDS}" "${code}" "${ingredient_id}")"
  done < <(jq -c '.[]' <<<"${INGREDIENT_SPECS}")

  while IFS= read -r product_spec; do
    local product_code product_id recipe_code recipe_id recipe_version_id tax_rate_id retail_price_id delivery_price_id
    product_code="$(jq -r '.code' <<<"${product_spec}")"
    recipe_code="$(jq -r '.recipeCode' <<<"${product_spec}")"
    local payload
    payload="$(jq -nc \
      --arg code "${product_code}" \
      --arg name "$(jq -r '.name' <<<"${product_spec}")" \
      --arg categoryCode "$(jq -r '.categoryCode' <<<"${product_spec}")" \
      --arg status "ACTIVE" \
      --arg description "$(jq -r '.description' <<<"${product_spec}")" \
      '{code:$code,name:$name,categoryCode:$categoryCode,status:$status,description:$description}')"
    product_id="$(create_or_resolve_entity "product ${product_code}" "/products" "${payload}" "find_product_id_by_code '${product_code}'")"
    PRODUCT_IDS="$(map_put "${PRODUCT_IDS}" "${product_code}" "${product_id}")"

    payload="$(jq -nc \
      --argjson productId "${product_id}" \
      --arg recipeCode "${recipe_code}" \
      --arg description "$(jq -r '.description' <<<"${product_spec}")" \
      '{productId:$productId,recipeCode:$recipeCode,description:$description}')"
    recipe_id="$(create_or_resolve_entity "recipe ${recipe_code}" "/recipes" "${payload}" "find_recipe_id_by_code '${recipe_code}'")"
    RECIPE_IDS="$(map_put "${RECIPE_IDS}" "${recipe_code}" "${recipe_id}")"

    recipe_version_id="$(find_recipe_version_id "${recipe_id}" "v1" "${HISTORY_START}")"
    if [[ -z "${recipe_version_id}" ]]; then
      local ingredients_payload='[]'
      while IFS=$'\t' read -r ingredient_code uom_code qty; do
        local ingredient_id
        ingredient_id="$(map_get "${INGREDIENT_IDS}" "${ingredient_code}")"
        ingredients_payload="$(jq -c \
          --argjson ingredientId "${ingredient_id}" \
          --arg uomCode "${uom_code}" \
          --arg qty "${qty}" \
          --argjson sortOrder "$(( $(jq -r 'length' <<<"${ingredients_payload}") + 1 ))" \
          '. + [{ingredientId:$ingredientId,uomCode:$uomCode,qty:($qty|tonumber),sortOrder:$sortOrder}]' <<<"${ingredients_payload}")"
      done < <(jq -r '.ingredients[] | [.code,.uomCode,.qty] | @tsv' <<<"${product_spec}")
      payload="$(jq -nc \
        --argjson recipeId "${recipe_id}" \
        --arg versionNo "v1" \
        --arg yieldQty "$(jq -r '.yieldQty' <<<"${product_spec}")" \
        --arg yieldUomCode "$(jq -r '.yieldUomCode' <<<"${product_spec}")" \
        --arg status "ACTIVE" \
        --arg effectiveFrom "${HISTORY_START}" \
        --argjson ingredients "${ingredients_payload}" \
        '{recipeId:$recipeId,versionNo:$versionNo,yieldQty:($yieldQty|tonumber),yieldUomCode:$yieldUomCode,status:$status,effectiveFrom:$effectiveFrom,ingredients:$ingredients}')"
      api_request POST "/recipe-versions" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create recipe version for ${recipe_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      recipe_version_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    tax_rate_id="$(find_tax_rate_id "${product_id}" "${HISTORY_START}")"
    if [[ -z "${tax_rate_id}" ]]; then
      payload="$(jq -nc \
        --argjson productId "${product_id}" \
        --arg taxPercent "$(jq -r '.taxPercent' <<<"${product_spec}")" \
        --arg effectiveFrom "${HISTORY_START}" \
        '{productId:$productId,taxPercent:($taxPercent|tonumber),effectiveFrom:$effectiveFrom}')"
      api_request POST "/tax-rates" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create tax rate for ${product_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      tax_rate_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    retail_price_id="$(find_product_price_id "${product_id}" "GLOBAL" "null" "RETAIL" "${HISTORY_START}")"
    if [[ -z "${retail_price_id}" ]]; then
      payload="$(jq -nc \
        --argjson productId "${product_id}" \
        --arg scopeType "GLOBAL" \
        --arg priceType "RETAIL" \
        --arg currencyCode "${FERN_CURRENCY}" \
        --arg priceValue "$(jq -r '.retailPrice' <<<"${product_spec}")" \
        --arg effectiveFrom "${HISTORY_START}" \
        '{productId:$productId,scopeType:$scopeType,priceType:$priceType,currencyCode:$currencyCode,priceValue:($priceValue|tonumber),effectiveFrom:$effectiveFrom}')"
      api_request POST "/product-prices" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create retail price for ${product_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      retail_price_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    if [[ "$(jq -r '.deliveryPrice != null' <<<"${product_spec}")" == "true" ]]; then
      delivery_price_id="$(find_product_price_id "${product_id}" "GLOBAL" "null" "DELIVERY" "${HISTORY_START}")"
      if [[ -z "${delivery_price_id}" ]]; then
        payload="$(jq -nc \
          --argjson productId "${product_id}" \
          --arg scopeType "GLOBAL" \
          --arg priceType "DELIVERY" \
          --arg currencyCode "${FERN_CURRENCY}" \
          --arg priceValue "$(jq -r '.deliveryPrice' <<<"${product_spec}")" \
          --arg effectiveFrom "${HISTORY_START}" \
          '{productId:$productId,scopeType:$scopeType,priceType:$priceType,currencyCode:$currencyCode,priceValue:($priceValue|tonumber),effectiveFrom:$effectiveFrom}')"
        api_request POST "/product-prices" "${payload}" "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Create delivery price for ${product_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
      else
        REUSED_TOTAL=$((REUSED_TOTAL + 1))
      fi
    fi
  done < <(jq -c '.[]' <<<"${PRODUCT_SPECS}")

  while IFS= read -r override_spec; do
    local product_code outlet_code product_id outlet_id scope_id existing_price_id payload
    product_code="$(jq -r '.productCode' <<<"${override_spec}")"
    outlet_code="$(jq -r '.outletCode' <<<"${override_spec}")"
    product_id="$(map_get "${PRODUCT_IDS}" "${product_code}")"
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    existing_price_id="$(find_product_price_id "${product_id}" "OUTLET" "${outlet_id}" "$(jq -r '.priceType' <<<"${override_spec}")" "${HISTORY_START}")"
    if [[ -z "${existing_price_id}" ]]; then
      payload="$(jq -nc \
        --argjson productId "${product_id}" \
        --arg scopeType "OUTLET" \
        --argjson scopeId "${outlet_id}" \
        --arg priceType "$(jq -r '.priceType' <<<"${override_spec}")" \
        --arg currencyCode "${FERN_CURRENCY}" \
        --arg priceValue "$(jq -r '.priceValue' <<<"${override_spec}")" \
        --arg effectiveFrom "${HISTORY_START}" \
        '{productId:$productId,scopeType:$scopeType,scopeId:$scopeId,priceType:$priceType,currencyCode:$currencyCode,priceValue:($priceValue|tonumber),effectiveFrom:$effectiveFrom}')"
      api_request POST "/product-prices" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create outlet override price for ${product_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
  done < <(jq -c '.[]' <<<"${OUTLET_OVERRIDE_PRICE_SPECS}")

  while IFS=$'\t' read -r outlet_code outlet_type; do
    local outlet_id
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    while IFS=$'\t' read -r product_code; do
      local product_id available current_value payload
      product_id="$(map_get "${PRODUCT_IDS}" "${product_code}")"
      available="$(product_outlet_is_available "${product_code}" "${outlet_type}")"
      current_value="$(find_product_availability_value "${product_id}" "${outlet_id}")"
      if [[ -n "${current_value}" && "${current_value}" == "${available}" ]]; then
        REUSED_TOTAL=$((REUSED_TOTAL + 1))
      else
        payload="$(jq -nc --argjson productId "${product_id}" --argjson outletId "${outlet_id}" --argjson available "${available}" '{productId:$productId,outletId:$outletId,available:$available}')"
        api_request PUT "/product-availability" "${payload}" "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Upsert product availability for ${product_code}/${outlet_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
      fi
    done < <(jq -r '.[].code' <<<"${PRODUCT_SPECS}")
  done < <(jq -r '.[] | [.outletCode,.outletType] | @tsv' <<<"${OUTLET_SPECS}")
}

phase_inventory_initial_stock() {
  log "Phase 6/14: initial stock adjustments"
  while IFS=$'\t' read -r outlet_code; do
    local outlet_id region_code region_id
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    region_code="$(jq -r --arg outlet_code "${outlet_code}" '.[] | select(.outletCode == $outlet_code) | .regionCode' <<<"${OUTLET_SPECS}")"
    region_id="$(map_get "${REGION_IDS}" "${region_code}")"
    while IFS=$'\t' read -r ingredient_code; do
      local ingredient_id existing_count qty note payload adjustment_id idem_key
      ingredient_id="$(map_get "${INGREDIENT_IDS}" "${ingredient_code}")"
      existing_count="$(inventory_txn_exists "${outlet_id}" "${ingredient_id}" "STOCK_ADJUSTMENT_IN" "${INITIAL_STOCK_DATE}" "STOCK_ADJUSTMENT")"
      if (( existing_count > 0 )); then
        REUSED_TOTAL=$((REUSED_TOTAL + 1))
        continue
      fi
      qty="$(build_initial_stock_qty "${ingredient_code}")"
      note="SEED-INITIAL-STOCK:${outlet_code}:${ingredient_code}:${INITIAL_STOCK_DATE}"
      payload="$(jq -nc \
        --argjson regionId "${region_id}" \
        --argjson outletId "${outlet_id}" \
        --argjson ingredientId "${ingredient_id}" \
        --arg adjustmentDirection "IN" \
        --arg qty "${qty}" \
        --arg businessDate "${INITIAL_STOCK_DATE}" \
        --arg reason "SEED_INITIAL_STOCK" \
        --arg note "${note}" \
        '{regionId:$regionId,outletId:$outletId,ingredientId:$ingredientId,adjustmentDirection:$adjustmentDirection,qty:($qty|tonumber),businessDate:$businessDate,reason:$reason,note:$note}')"
      api_request POST "/stock-adjustments" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create stock adjustment for ${outlet_code}/${ingredient_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      adjustment_id="$(json_get "${HTTP_BODY}" '.id // empty')"
      idem_key="seed-adjustment-post-${outlet_code}-${ingredient_code}-${INITIAL_STOCK_DATE}"
      api_request POST "/stock-adjustments/${adjustment_id}/post" "{}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: ${idem_key}"
      http_ok || fail "Post stock adjustment ${adjustment_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    done < <(jq -r '.[].code' <<<"${INGREDIENT_SPECS}")
  done < <(jq -r '.[].outletCode' <<<"${OUTLET_SPECS}")
}

phase_suppliers_procurement() {
  log "Phase 7/14: suppliers and procurement history"
  while IFS= read -r supplier_spec; do
    local supplier_code supplier_id payload default_region_id
    supplier_code="$(jq -r '.code' <<<"${supplier_spec}")"
    default_region_id="$(map_get "${REGION_IDS}" "NORTH")"
    payload="$(jq -nc \
      --arg supplierCode "${supplier_code}" \
      --arg name "$(jq -r '.name' <<<"${supplier_spec}")" \
      --arg email "$(jq -r '.email' <<<"${supplier_spec}")" \
      --arg phone "$(jq -r '.phone' <<<"${supplier_spec}")" \
      --arg address "$(jq -r '.address' <<<"${supplier_spec}")" \
      --argjson defaultRegionId "${default_region_id}" \
      --arg status "INACTIVE" \
      '{supplierCode:$supplierCode,name:$name,email:$email,phone:$phone,address:$address,defaultRegionId:$defaultRegionId,status:$status}')"
    supplier_id="$(create_or_resolve_entity "supplier ${supplier_code}" "/suppliers" "${payload}" "find_supplier_id_by_code '${supplier_code}'")"
    SUPPLIER_IDS="$(map_put "${SUPPLIER_IDS}" "${supplier_code}" "${supplier_id}")"

    api_request POST "/suppliers/${supplier_id}/activate" "{}" "${BOOTSTRAP_TOKEN}"
    if http_ok; then
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    elif [[ "${HTTP_STATUS}" == "409" || "${HTTP_STATUS}" == "400" ]]; then
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    else
      fail "Activate supplier ${supplier_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
    fi
  done < <(jq -c '.[]' <<<"${SUPPLIER_SPECS}")

  local proc_outlets=(HN_FLAGSHIP HN_STD_01 HN_STD_02 HCM_FLAGSHIP HCM_STD_01 HCM_STD_02 HN_FLAGSHIP HN_STD_01 HN_STD_02 HCM_FLAGSHIP HCM_STD_01 HCM_STD_02 HN_FLAGSHIP)
  local proc_suppliers=(SUP_BEAN_CRAFT SUP_DAIRY_DEPOT SUP_TEA_SYRUP SUP_BAKERY_HUB SUP_BEAN_CRAFT SUP_DAIRY_DEPOT SUP_TEA_SYRUP SUP_BAKERY_HUB SUP_BEAN_CRAFT SUP_DAIRY_DEPOT SUP_TEA_SYRUP SUP_BAKERY_HUB SUP_BEAN_CRAFT)
  local proc_targets=(PAID PAID PAID PAID PAID PAID INVOICE_APPROVED INVOICE_APPROVED GR_POSTED ORDERED APPROVED SUBMITTED DRAFT)
  local idx
  for (( idx = 0; idx < ${#proc_outlets[@]}; idx++ )); do
    local outlet_code="${proc_outlets[$idx]}"
    local supplier_code="${proc_suppliers[$idx]}"
    local target_state="${proc_targets[$idx]}"
    local business_date
    business_date="$(jq -r --argjson idx "$((idx * 2))" '.[$idx % length]' <<<"${DATE_SERIES_JSON}")"
    local outlet_id region_code region_id supplier_id po_note po_id po_body po_status
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    region_code="$(jq -r --arg outlet_code "${outlet_code}" '.[] | select(.outletCode == $outlet_code) | .regionCode' <<<"${OUTLET_SPECS}")"
    region_id="$(map_get "${REGION_IDS}" "${region_code}")"
    supplier_id="$(map_get "${SUPPLIER_IDS}" "${supplier_code}")"
    po_note="SEED-PO:${outlet_code}:${business_date}:$(printf '%02d' "${idx}")"
    po_id="$(find_purchase_order_id_by_note "${outlet_id}" "${supplier_id}" "${po_note}")"
    if [[ -z "${po_id}" ]]; then
      local source_lines lines_payload='[]'
      source_lines="$(build_supplier_po_lines "${supplier_code}")"
      while IFS= read -r line; do
        local ingredient_code ingredient_id
        ingredient_code="$(jq -r '.ingredientCode' <<<"${line}")"
        ingredient_id="$(map_get "${INGREDIENT_IDS}" "${ingredient_code}")"
        lines_payload="$(jq -c \
          --argjson ingredientId "${ingredient_id}" \
          --arg uomCode "$(jq -r '.uomCode' <<<"${line}")" \
          --arg qtyOrdered "$(jq -r '.qtyOrdered' <<<"${line}")" \
          --arg expectedUnitPrice "$(jq -r '.expectedUnitPrice' <<<"${line}")" \
          --arg taxPercent "$(jq -r '.taxPercent' <<<"${line}")" \
          --arg note "$(jq -r '.note' <<<"${line}")" \
          '. + [{
            ingredientId:$ingredientId,
            uomCode:$uomCode,
            qtyOrdered:($qtyOrdered|tonumber),
            expectedUnitPrice:($expectedUnitPrice|tonumber),
            taxPercent:($taxPercent|tonumber),
            note:$note
          }]' <<<"${lines_payload}")"
      done < <(jq -c '.[]' <<<"${source_lines}")
      local po_payload
      po_payload="$(jq -nc \
        --argjson regionId "${region_id}" \
        --argjson outletId "${outlet_id}" \
        --argjson supplierId "${supplier_id}" \
        --arg orderDate "${business_date}" \
        --arg expectedDeliveryDate "${business_date}" \
        --arg note "${po_note}" \
        --argjson lines "${lines_payload}" \
        '{regionId:$regionId,outletId:$outletId,supplierId:$supplierId,orderDate:$orderDate,expectedDeliveryDate:$expectedDeliveryDate,note:$note,lines:$lines}')"
      api_request POST "/purchase-orders" "${po_payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create purchase order ${po_note} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      po_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    po_body="$(api_get "${BOOTSTRAP_TOKEN}" "/purchase-orders/${po_id}")"
    po_status="$(jq -r '.status' <<<"${po_body}")"

    if [[ "${target_state}" != "DRAFT" && "${po_status}" == "DRAFT" ]]; then
      api_request POST "/purchase-orders/${po_id}/submit" "{}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Submit purchase order ${po_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      po_body="$(api_get "${BOOTSTRAP_TOKEN}" "/purchase-orders/${po_id}")"
      po_status="$(jq -r '.status' <<<"${po_body}")"
    fi

    if [[ "${target_state}" =~ ^(APPROVED|ORDERED|GR_POSTED|INVOICE_APPROVED|PAID)$ && "${po_status}" == "SUBMITTED" ]]; then
      api_request POST "/purchase-orders/${po_id}/approve" "{}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Approve purchase order ${po_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      po_body="$(api_get "${BOOTSTRAP_TOKEN}" "/purchase-orders/${po_id}")"
      po_status="$(jq -r '.status' <<<"${po_body}")"
    fi

    if [[ "${target_state}" =~ ^(ORDERED|GR_POSTED|INVOICE_APPROVED|PAID)$ && "${po_status}" == "APPROVED" ]]; then
      api_request POST "/purchase-orders/${po_id}/issue" "{}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Issue purchase order ${po_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    fi

    if [[ "${target_state}" =~ ^(GR_POSTED|INVOICE_APPROVED|PAID)$ ]]; then
      local gr_note gr_id gr_body gr_status receipt_time po_lines gr_lines_payload='[]'
      gr_note="SEED-GR:${outlet_code}:${business_date}:$(printf '%02d' "${idx}")"
      gr_id="$(find_goods_receipt_id_by_note "${po_id}" "${gr_note}")"
      if [[ -z "${gr_id}" ]]; then
        po_body="$(api_get "${BOOTSTRAP_TOKEN}" "/purchase-orders/${po_id}")"
        po_lines="$(jq -c '.lines' <<<"${po_body}")"
        while IFS= read -r line; do
          local purchase_order_line_id ingredient_id uom_code qty_received unit_cost line_note
          purchase_order_line_id="$(jq -r '.id' <<<"${line}")"
          ingredient_id="$(jq -r '.ingredientId' <<<"${line}")"
          uom_code="$(jq -r '.uomCode' <<<"${line}")"
          qty_received="$(jq -r '.qtyOrdered' <<<"${line}")"
          unit_cost="$(jq -r '.expectedUnitPrice // 0' <<<"${line}")"
          line_note="$(jq -r '.note // "seed receipt"' <<<"${line}")"
          gr_lines_payload="$(jq -c \
            --argjson purchaseOrderLineId "${purchase_order_line_id}" \
            --argjson ingredientId "${ingredient_id}" \
            --arg uomCode "${uom_code}" \
            --arg qtyReceived "${qty_received}" \
            --arg unitCost "${unit_cost}" \
            --arg note "${line_note}" \
            '. + [{
               purchaseOrderLineId:$purchaseOrderLineId,
               ingredientId:$ingredientId,
               uomCode:$uomCode,
               qtyReceived:($qtyReceived|tonumber),
               unitCost:($unitCost|tonumber),
               note:$note
            }]' <<<"${gr_lines_payload}")"
        done < <(jq -c '.[]' <<<"${po_lines}")
        receipt_time="$(to_utc_instant "${business_date}" "09:30:00")"
        local gr_payload
        gr_payload="$(jq -nc \
          --argjson purchaseOrderId "${po_id}" \
          --arg receiptTime "${receipt_time}" \
          --arg businessDate "${business_date}" \
          --arg supplierLotNumber "LOT-${outlet_code}-${idx}" \
          --arg note "${gr_note}" \
          --argjson lines "${gr_lines_payload}" \
          '{purchaseOrderId:$purchaseOrderId,receiptTime:$receiptTime,businessDate:$businessDate,supplierLotNumber:$supplierLotNumber,note:$note,lines:$lines}')"
        api_request POST "/goods-receipts" "${gr_payload}" "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Create goods receipt ${gr_note} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
        gr_id="$(json_get "${HTTP_BODY}" '.id // empty')"
      else
        REUSED_TOTAL=$((REUSED_TOTAL + 1))
      fi

      gr_body="$(api_get "${BOOTSTRAP_TOKEN}" "/goods-receipts/${gr_id}")"
      gr_status="$(jq -r '.status' <<<"${gr_body}")"
      if [[ "${gr_status}" == "DRAFT" ]]; then
        api_request POST "/goods-receipts/${gr_id}/receive" "{}" "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Receive goods receipt ${gr_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
        gr_body="$(api_get "${BOOTSTRAP_TOKEN}" "/goods-receipts/${gr_id}")"
        gr_status="$(jq -r '.status' <<<"${gr_body}")"
      fi

      if [[ "${gr_status}" == "RECEIVED" ]]; then
        api_request POST "/goods-receipts/${gr_id}/post" "{}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-gr-post-${outlet_code}-${idx}"
        http_ok || fail "Post goods receipt ${gr_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
      fi

      if [[ "${target_state}" =~ ^(INVOICE_APPROVED|PAID)$ ]]; then
        local invoice_number invoice_id invoice_body invoice_status invoice_lines_payload='[]'
        invoice_number="SEED-INV-${outlet_code}-${business_date//-/}-$(printf '%02d' "${idx}")"
        invoice_id="$(find_supplier_invoice_id_by_number "${supplier_id}" "${outlet_id}" "${invoice_number}")"
        if [[ -z "${invoice_id}" ]]; then
          gr_body="$(api_get "${BOOTSTRAP_TOKEN}" "/goods-receipts/${gr_id}")"
          while IFS= read -r gr_line; do
            local goods_receipt_line_id qty_invoiced unit_price tax_percent line_subtotal line_tax line_total description ingredient_id ingredient_code
            goods_receipt_line_id="$(jq -r '.id' <<<"${gr_line}")"
            ingredient_id="$(jq -r '.ingredientId' <<<"${gr_line}")"
            ingredient_code="$(jq -r --argjson ingredient_id "${ingredient_id}" '.[] | select((.id|tonumber) == $ingredient_id) | .code' <<<"$(jq -c 'to_entries | map({code:.key,id:.value})' <<<"${INGREDIENT_IDS}")")"
            qty_invoiced="$(jq -r '.qtyReceived' <<<"${gr_line}")"
            unit_price="$(jq -r '.unitCost' <<<"${gr_line}")"
            tax_percent="8.00"
            line_subtotal="$(decimal_mul "${qty_invoiced}" "${unit_price}")"
            line_tax="$(decimal_tax "${line_subtotal}" "${tax_percent}")"
            line_total="$(decimal_add "${line_subtotal}" "${line_tax}")"
            description="Invoice ${ingredient_code}"
            invoice_lines_payload="$(jq -c \
              --arg lineType "STOCK" \
              --argjson goodsReceiptLineId "${goods_receipt_line_id}" \
              --arg description "${description}" \
              --arg qtyInvoiced "${qty_invoiced}" \
              --arg unitPrice "${unit_price}" \
              --arg taxPercent "${tax_percent}" \
              --arg taxAmount "${line_tax}" \
              --arg lineTotal "${line_total}" \
              '. + [{
                lineType:$lineType,
                goodsReceiptLineId:$goodsReceiptLineId,
                description:$description,
                qtyInvoiced:($qtyInvoiced|tonumber),
                unitPrice:($unitPrice|tonumber),
                taxPercent:($taxPercent|tonumber),
                taxAmount:($taxAmount|tonumber),
                lineTotal:($lineTotal|tonumber),
                note:"seed invoice line"
              }]' <<<"${invoice_lines_payload}")"
          done < <(jq -c '.lines[]' <<<"${gr_body}")
          local invoice_payload
          invoice_payload="$(jq -nc \
            --argjson supplierId "${supplier_id}" \
            --argjson regionId "${region_id}" \
            --argjson outletId "${outlet_id}" \
            --arg currencyCode "${FERN_CURRENCY}" \
            --arg invoiceNumber "${invoice_number}" \
            --arg invoiceDate "${business_date}" \
            --arg dueDate "${business_date}" \
            --arg note "SEED-INVOICE:${outlet_code}:${business_date}:$(printf '%02d' "${idx}")" \
            --argjson lines "${invoice_lines_payload}" \
            '{supplierId:$supplierId,regionId:$regionId,outletId:$outletId,currencyCode:$currencyCode,invoiceNumber:$invoiceNumber,invoiceDate:$invoiceDate,dueDate:$dueDate,note:$note,lines:$lines}')"
          api_request POST "/supplier-invoices" "${invoice_payload}" "${BOOTSTRAP_TOKEN}"
          http_ok || fail "Create supplier invoice ${invoice_number} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
          invoice_id="$(json_get "${HTTP_BODY}" '.id // empty')"
        else
          REUSED_TOTAL=$((REUSED_TOTAL + 1))
        fi

        invoice_body="$(api_get "${BOOTSTRAP_TOKEN}" "/supplier-invoices/${invoice_id}")"
        invoice_status="$(jq -r '.status' <<<"${invoice_body}")"
        if [[ "${invoice_status}" != "APPROVED" ]]; then
          api_request POST "/supplier-invoices/${invoice_id}/approve" "{}" "${BOOTSTRAP_TOKEN}"
          http_ok || fail "Approve supplier invoice ${invoice_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
        fi

        if [[ "${target_state}" == "PAID" ]]; then
          local transaction_ref payment_id payment_time payment_amount
          transaction_ref="SEED-SPAY-${invoice_number}"
          payment_id="$(find_supplier_payment_id_by_transaction_ref "${supplier_id}" "${transaction_ref}")"
          if [[ -z "${payment_id}" ]]; then
            invoice_body="$(api_get "${BOOTSTRAP_TOKEN}" "/supplier-invoices/${invoice_id}")"
            payment_amount="$(jq -r '.totalAmount' <<<"${invoice_body}")"
            payment_time="$(to_utc_instant "${business_date}" "16:30:00")"
            local payment_method="BANK_TRANSFER"
            case $((idx % 4)) in
              0) payment_method="BANK_TRANSFER" ;;
              1) payment_method="CARD" ;;
              2) payment_method="EWALLET" ;;
              3) payment_method="CASH" ;;
            esac
            local payment_payload
            payment_payload="$(jq -nc \
              --argjson supplierId "${supplier_id}" \
              --arg currencyCode "${FERN_CURRENCY}" \
              --arg paymentMethod "${payment_method}" \
              --arg amount "${payment_amount}" \
              --arg paymentTime "${payment_time}" \
              --arg transactionRef "${transaction_ref}" \
              --arg note "SEED-PAYMENT:${invoice_number}" \
              --argjson invoiceAllocations "[{\"supplierInvoiceId\":${invoice_id},\"allocatedAmount\":${payment_amount},\"note\":\"seed allocation\"}]" \
              '{supplierId:$supplierId,currencyCode:$currencyCode,paymentMethod:$paymentMethod,amount:($amount|tonumber),paymentTime:$paymentTime,transactionRef:$transactionRef,note:$note,invoiceAllocations:$invoiceAllocations}')"
            api_request POST "/supplier-payments" "${payment_payload}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-supplier-payment-${invoice_number}"
            http_ok || fail "Create supplier payment ${transaction_ref} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
            CREATED_TOTAL=$((CREATED_TOTAL + 1))
          else
            REUSED_TOTAL=$((REUSED_TOTAL + 1))
          fi
        fi
      fi
    fi
  done
}

phase_schedules_attendance() {
  log "Phase 8/14: shift schedules, assignments, attendance and approvals"
  local day_index=0
  while IFS= read -r work_date; do
    while IFS=$'\t' read -r outlet_code region_code; do
      local outlet_id region_id morning_schedule_id evening_schedule_id payload
      outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
      region_id="$(map_get "${REGION_IDS}" "${region_code}")"

      morning_schedule_id="$(find_shift_schedule_id "${outlet_id}" "${work_date}" "Morning")"
      if [[ -z "${morning_schedule_id}" ]]; then
        payload="$(jq -nc \
          --argjson regionId "${region_id}" \
          --argjson outletId "${outlet_id}" \
          --arg shiftDate "${work_date}" \
          --arg shiftName "Morning" \
          --arg startTime "06:00:00" \
          --arg endTime "14:00:00" \
          --arg status "SCHEDULED" \
          '{regionId:$regionId,outletId:$outletId,shiftDate:$shiftDate,shiftName:$shiftName,startTime:$startTime,endTime:$endTime,status:$status}')"
        api_request POST "/shift-schedules" "${payload}" "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Create morning shift for ${outlet_code} ${work_date} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
        morning_schedule_id="$(json_get "${HTTP_BODY}" '.id // empty')"
      else
        REUSED_TOTAL=$((REUSED_TOTAL + 1))
      fi

      evening_schedule_id="$(find_shift_schedule_id "${outlet_id}" "${work_date}" "Evening")"
      if [[ -z "${evening_schedule_id}" ]]; then
        payload="$(jq -nc \
          --argjson regionId "${region_id}" \
          --argjson outletId "${outlet_id}" \
          --arg shiftDate "${work_date}" \
          --arg shiftName "Evening" \
          --arg startTime "14:00:00" \
          --arg endTime "22:00:00" \
          --arg status "SCHEDULED" \
          '{regionId:$regionId,outletId:$outletId,shiftDate:$shiftDate,shiftName:$shiftName,startTime:$startTime,endTime:$endTime,status:$status}')"
        api_request POST "/shift-schedules" "${payload}" "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Create evening shift for ${outlet_code} ${work_date} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
        evening_schedule_id="$(json_get "${HTTP_BODY}" '.id // empty')"
      else
        REUSED_TOTAL=$((REUSED_TOTAL + 1))
      fi

      while IFS= read -r employee_record; do
        local employee_id global_index target_shift_name shift_schedule_id shift_assignment_id approval_status attendance_mode shift_start_time shift_end_time clock_in_time clock_out_time
        employee_id="$(jq -r '.employeeId' <<<"${employee_record}")"
        global_index="$(jq -r '.globalIndex' <<<"${employee_record}")"
        if (( (day_index + global_index) % 2 == 0 )); then
          target_shift_name="Morning"
          shift_schedule_id="${morning_schedule_id}"
          shift_start_time="06:00:00"
          shift_end_time="14:00:00"
        else
          target_shift_name="Evening"
          shift_schedule_id="${evening_schedule_id}"
          shift_start_time="14:00:00"
          shift_end_time="22:00:00"
        fi

        shift_assignment_id="$(find_shift_assignment_id "${shift_schedule_id}" "${employee_id}")"
        if [[ -z "${shift_assignment_id}" ]]; then
          payload="$(jq -nc \
            --argjson shiftScheduleId "${shift_schedule_id}" \
            --argjson employeeId "${employee_id}" \
            --arg assignedRole "$(jq -r '.positionTitle' <<<"${employee_record}")" \
            --arg note "SEED-SHIFT:${outlet_code}:${work_date}:${target_shift_name}" \
            '{shiftScheduleId:$shiftScheduleId,employeeId:$employeeId,assignedRole:$assignedRole,note:$note}')"
          api_request POST "/shift-assignments" "${payload}" "${BOOTSTRAP_TOKEN}"
          http_ok || fail "Create shift assignment for employee ${employee_id} on ${work_date} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
          shift_assignment_id="$(json_get "${HTTP_BODY}" '.id // empty')"
        else
          REUSED_TOTAL=$((REUSED_TOTAL + 1))
        fi

        approval_status="$(get_attendance_approval_status "${shift_assignment_id}")"
        if [[ "${approval_status}" == "APPROVED" ]]; then
          REUSED_TOTAL=$((REUSED_TOTAL + 1))
          continue
        fi

        attendance_mode=$(( (day_index + global_index) % 10 ))
        if (( attendance_mode == 0 )); then
          :
        else
          if (( attendance_mode == 1 )); then
            clock_in_time="$(to_utc_instant "${work_date}" "$(python3 - "${shift_start_time}" <<'PY'
import sys
from datetime import datetime, timedelta
value = datetime.strptime(sys.argv[1], "%H:%M:%S") + timedelta(minutes=12)
print(value.strftime("%H:%M:%S"))
PY
)")"
          else
            clock_in_time="$(to_utc_instant "${work_date}" "${shift_start_time}")"
          fi
          clock_out_time="$(to_utc_instant "${work_date}" "${shift_end_time}")"

          payload="$(jq -nc \
            --argjson employeeId "${employee_id}" \
            --argjson regionId "${region_id}" \
            --argjson outletId "${outlet_id}" \
            --argjson shiftAssignmentId "${shift_assignment_id}" \
            --arg eventType "CLOCK_IN" \
            --arg eventTime "${clock_in_time}" \
            --arg sourceSystem "SEED_HR" \
            '{employeeId:$employeeId,regionId:$regionId,outletId:$outletId,shiftAssignmentId:$shiftAssignmentId,eventType:$eventType,eventTime:$eventTime,sourceSystem:$sourceSystem}')"
          api_request POST "/attendance-events" "${payload}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-attendance-${shift_assignment_id}-clock-in"
          http_ok || fail "CLOCK_IN for shift assignment ${shift_assignment_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))

          payload="$(jq -nc \
            --argjson employeeId "${employee_id}" \
            --argjson regionId "${region_id}" \
            --argjson outletId "${outlet_id}" \
            --argjson shiftAssignmentId "${shift_assignment_id}" \
            --arg eventType "CLOCK_OUT" \
            --arg eventTime "${clock_out_time}" \
            --arg sourceSystem "SEED_HR" \
            '{employeeId:$employeeId,regionId:$regionId,outletId:$outletId,shiftAssignmentId:$shiftAssignmentId,eventType:$eventType,eventTime:$eventTime,sourceSystem:$sourceSystem}')"
          api_request POST "/attendance-events" "${payload}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-attendance-${shift_assignment_id}-clock-out"
          http_ok || fail "CLOCK_OUT for shift assignment ${shift_assignment_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
        fi

        api_request POST "/attendance-approvals/${shift_assignment_id}/approve" '{"comments":"seed approved attendance"}' "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Approve attendance ${shift_assignment_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
      done < <(jq -c --arg outlet_code "${outlet_code}" '.[] | select(.outletCode == $outlet_code)' <<<"${EMPLOYEE_ROSTER}")
    done < <(jq -r '.[] | [.outletCode,.regionCode] | @tsv' <<<"${OUTLET_SPECS}")
    day_index=$((day_index + 1))
  done < <(jq -r '.[]' <<<"${DATE_SERIES_JSON}")
}

phase_payroll() {
  log "Phase 9/14: payroll periods and runs"
  while IFS=$'\t' read -r region_code region_name; do
    local region_id paid_period_name current_period_name paid_period_id current_period_id paid_run_note current_run_note paid_run_id current_run_id run_body run_status
    region_id="$(map_get "${REGION_IDS}" "${region_code}")"
    paid_period_name="Payroll ${region_name} ${PREVIOUS_YEAR_MONTH}"
    current_period_name="Payroll ${region_name} ${CURRENT_YEAR_MONTH} Open"

    paid_period_id="$(find_payroll_period_id_by_name "${region_id}" "${paid_period_name}")"
    if [[ -z "${paid_period_id}" ]]; then
      local payload
      payload="$(jq -nc \
        --argjson regionId "${region_id}" \
        --arg name "${paid_period_name}" \
        --arg startDate "${PREVIOUS_MONTH_START}" \
        --arg endDate "${PREVIOUS_MONTH_END}" \
        --arg payDate "${PREVIOUS_PAY_DATE}" \
        --arg note "SEED-PAYROLL-PERIOD:${region_code}:${PREVIOUS_YEAR_MONTH}" \
        '{regionId:$regionId,name:$name,startDate:$startDate,endDate:$endDate,payDate:$payDate,note:$note}')"
      api_request POST "/payroll-periods" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create paid payroll period for ${region_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      paid_period_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
    PAYROLL_PERIOD_IDS="$(map_put "${PAYROLL_PERIOD_IDS}" "${region_code}:${PREVIOUS_YEAR_MONTH}" "${paid_period_id}")"

    current_period_id="$(find_payroll_period_id_by_name "${region_id}" "${current_period_name}")"
    if [[ -z "${current_period_id}" ]]; then
      local payload
      payload="$(jq -nc \
        --argjson regionId "${region_id}" \
        --arg name "${current_period_name}" \
        --arg startDate "${CURRENT_MONTH_START}" \
        --arg endDate "${CURRENT_PERIOD_END}" \
        --arg payDate "${CURRENT_PAY_DATE}" \
        --arg note "SEED-PAYROLL-PERIOD:${region_code}:${CURRENT_YEAR_MONTH}:OPEN" \
        '{regionId:$regionId,name:$name,startDate:$startDate,endDate:$endDate,payDate:$payDate,note:$note}')"
      api_request POST "/payroll-periods" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create current payroll period for ${region_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      current_period_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
    PAYROLL_PERIOD_IDS="$(map_put "${PAYROLL_PERIOD_IDS}" "${region_code}:${CURRENT_YEAR_MONTH}" "${current_period_id}")"

    paid_run_note="SEED-PAYROLL-RUN:${region_code}:${PREVIOUS_YEAR_MONTH}:PAID"
    paid_run_id="$(find_payroll_run_id_by_note "${region_id}" "${paid_run_note}")"
    if [[ -z "${paid_run_id}" ]]; then
      local payload
      payload="$(jq -nc --argjson payrollPeriodId "${paid_period_id}" --arg runDate "${PREVIOUS_PAY_DATE}" --arg note "${paid_run_note}" '{payrollPeriodId:$payrollPeriodId,runDate:$runDate,note:$note}')"
      api_request POST "/payroll-runs" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create paid payroll run for ${region_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      paid_run_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
    PAYROLL_RUN_IDS="$(map_put "${PAYROLL_RUN_IDS}" "${region_code}:${PREVIOUS_YEAR_MONTH}:PAID" "${paid_run_id}")"

    run_body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs/${paid_run_id}")"
    run_status="$(jq -r '.status' <<<"${run_body}")"
    if [[ "${run_status}" == "DRAFT" ]]; then
      api_request POST "/payroll-runs/${paid_run_id}/submit" '{"note":"seed submit"}' "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Submit payroll run ${paid_run_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      run_body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs/${paid_run_id}")"
      run_status="$(jq -r '.status' <<<"${run_body}")"
    fi
    if [[ "${run_status}" == "SUBMITTED" ]]; then
      api_request POST "/payroll-runs/${paid_run_id}/approve" '{"note":"seed approve"}' "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Approve payroll run ${paid_run_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      run_body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs/${paid_run_id}")"
      run_status="$(jq -r '.status' <<<"${run_body}")"
    fi
    if [[ "${run_status}" == "APPROVED" ]]; then
      api_request POST "/payroll-runs/${paid_run_id}/mark-paid" "$(jq -nc --arg paymentReference "PAYROLL-${region_code}-${PREVIOUS_YEAR_MONTH}" --arg note "seed paid" '{paymentReference:$paymentReference,note:$note}')" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Mark paid payroll run ${paid_run_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    fi

    current_run_note="SEED-PAYROLL-RUN:${region_code}:${CURRENT_YEAR_MONTH}:SUBMITTED"
    current_run_id="$(find_payroll_run_id_by_note "${region_id}" "${current_run_note}")"
    if [[ -z "${current_run_id}" ]]; then
      local payload
      payload="$(jq -nc --argjson payrollPeriodId "${current_period_id}" --arg runDate "${BUSINESS_DATE}" --arg note "${current_run_note}" '{payrollPeriodId:$payrollPeriodId,runDate:$runDate,note:$note}')"
      api_request POST "/payroll-runs" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create current payroll run for ${region_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      current_run_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi
    PAYROLL_RUN_IDS="$(map_put "${PAYROLL_RUN_IDS}" "${region_code}:${CURRENT_YEAR_MONTH}:SUBMITTED" "${current_run_id}")"

    run_body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs/${current_run_id}")"
    run_status="$(jq -r '.status' <<<"${run_body}")"
    if [[ "${run_status}" == "DRAFT" ]]; then
      api_request POST "/payroll-runs/${current_run_id}/submit" '{"note":"seed submit current"}' "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Submit current payroll run ${current_run_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    fi
  done < <(jq -r '.[] | [.code,.name] | @tsv' <<<"${REGION_SPECS}")
}

phase_pos() {
  log "Phase 10/14: POS sessions, orders, payments, completion and close/reconcile"
  local day_index=0
  while IFS= read -r work_date; do
    while IFS=$'\t' read -r outlet_code outlet_type region_code; do
      local outlet_id region_id terminal_id session_id session_body session_status
      outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
      region_id="$(map_get "${REGION_IDS}" "${region_code}")"
      terminal_id="seed-$(slugify "${outlet_code}")-t1"
      session_id="$(find_pos_session_id "${outlet_id}" "${terminal_id}" "${work_date}")"
      if [[ -z "${session_id}" ]]; then
        local payload
        payload="$(jq -nc \
          --argjson regionId "${region_id}" \
          --argjson outletId "${outlet_id}" \
          --arg terminalId "${terminal_id}" \
          --arg currencyCode "${FERN_CURRENCY}" \
          --arg businessDate "${work_date}" \
          --arg note "SEED-POS:${outlet_code}:${work_date}" \
          '{regionId:$regionId,outletId:$outletId,terminalId:$terminalId,currencyCode:$currencyCode,businessDate:$businessDate,note:$note}')"
        api_request POST "/pos-sessions" "${payload}" "${BOOTSTRAP_TOKEN}"
        http_ok || fail "Open POS session for ${outlet_code} ${work_date} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
        CREATED_TOTAL=$((CREATED_TOTAL + 1))
        session_id="$(json_get "${HTTP_BODY}" '.id // empty')"
      else
        REUSED_TOTAL=$((REUSED_TOTAL + 1))
      fi

      local completed_orders=8
      local create_cancelled=0
      local create_open=0
      if (( day_index % 5 == 0 )); then
        create_cancelled=1
      fi
      if [[ "${work_date}" == "${BUSINESS_DATE}" ]]; then
        create_open=1
      fi

      local order_idx
      for (( order_idx = 1; order_idx <= completed_orders; order_idx++ )); do
        local order_note order_id order_body order_status lines_payload payment_method total_amount
        order_note="SEED-ORDER:${outlet_code}:${work_date}:$(printf '%02d' "${order_idx}")"
        order_id="$(find_sale_order_id_by_note "${session_id}" "${order_note}")"
        if [[ -z "${order_id}" ]]; then
          lines_payload="$(build_sale_lines_for_order "${outlet_type}" "${day_index}" "${order_idx}")"
          local order_type="TAKEAWAY"
          case $(((day_index + order_idx) % 3)) in
            0) order_type="TAKEAWAY" ;;
            1) order_type="DINE_IN" ;;
            2) order_type="DELIVERY" ;;
          esac
          local payload
          payload="$(jq -nc \
            --argjson posSessionId "${session_id}" \
            --arg orderType "${order_type}" \
            --arg note "${order_note}" \
            --argjson lines "${lines_payload}" \
            '{posSessionId:$posSessionId,orderType:$orderType,note:$note,lines:$lines}')"
          api_request POST "/sale-orders" "${payload}" "${BOOTSTRAP_TOKEN}"
          http_ok || fail "Create sale order ${order_note} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
          order_id="$(json_get "${HTTP_BODY}" '.id // empty')"
        else
          REUSED_TOTAL=$((REUSED_TOTAL + 1))
        fi

        order_body="$(api_get "${BOOTSTRAP_TOKEN}" "/sale-orders/${order_id}")"
        order_status="$(jq -r '.status' <<<"${order_body}")"
        total_amount="$(jq -r '.totalAmount' <<<"${order_body}")"
        if [[ "${order_status}" != "COMPLETED" ]]; then
          case $(((day_index + order_idx) % 4)) in
            0) payment_method="CASH" ;;
            1) payment_method="CARD" ;;
            2) payment_method="EWALLET" ;;
            3) payment_method="BANK_TRANSFER" ;;
          esac
          local payment_payload
          payment_payload="$(jq -nc \
            --arg paymentMethod "${payment_method}" \
            --arg amount "${total_amount}" \
            --arg transactionRef "SEED-PAY-${outlet_code}-${work_date//-/}-${order_idx}" \
            --arg note "seed payment" \
            --arg status "SUCCESS" \
            '{paymentMethod:$paymentMethod,amount:($amount|tonumber),transactionRef:$transactionRef,note:$note,status:$status}')"
          api_request POST "/sale-orders/${order_id}/payments" "${payment_payload}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-sale-payment-${outlet_code}-${work_date}-${order_idx}"
          http_ok || fail "Add payment for order ${order_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))

          api_request POST "/sale-orders/${order_id}/complete" "{}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-sale-complete-${outlet_code}-${work_date}-${order_idx}"
          http_ok || fail "Complete order ${order_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
        fi
      done

      if (( create_cancelled == 1 )); then
        local cancelled_note cancelled_id cancelled_body cancelled_status cancelled_lines
        cancelled_note="SEED-ORDER:${outlet_code}:${work_date}:CX"
        cancelled_id="$(find_sale_order_id_by_note "${session_id}" "${cancelled_note}")"
        if [[ -z "${cancelled_id}" ]]; then
          cancelled_lines="$(build_sale_lines_for_order "${outlet_type}" "${day_index}" 99)"
          local payload
          payload="$(jq -nc \
            --argjson posSessionId "${session_id}" \
            --arg orderType "TAKEAWAY" \
            --arg note "${cancelled_note}" \
            --argjson lines "${cancelled_lines}" \
            '{posSessionId:$posSessionId,orderType:$orderType,note:$note,lines:$lines}')"
          api_request POST "/sale-orders" "${payload}" "${BOOTSTRAP_TOKEN}"
          http_ok || fail "Create cancelled order ${cancelled_note} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
          cancelled_id="$(json_get "${HTTP_BODY}" '.id // empty')"
        else
          REUSED_TOTAL=$((REUSED_TOTAL + 1))
        fi
        cancelled_body="$(api_get "${BOOTSTRAP_TOKEN}" "/sale-orders/${cancelled_id}")"
        cancelled_status="$(jq -r '.status' <<<"${cancelled_body}")"
        if [[ "${cancelled_status}" == "OPEN" ]]; then
          api_request POST "/sale-orders/${cancelled_id}/cancel" "{}" "${BOOTSTRAP_TOKEN}"
          http_ok || fail "Cancel order ${cancelled_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
        fi
      fi

      if (( create_open == 1 )); then
        local open_note open_id open_lines
        open_note="SEED-ORDER:${outlet_code}:${work_date}:OPEN"
        open_id="$(find_sale_order_id_by_note "${session_id}" "${open_note}")"
        if [[ -z "${open_id}" ]]; then
          open_lines="$(build_sale_lines_for_order "${outlet_type}" "${day_index}" 77)"
          local payload
          payload="$(jq -nc \
            --argjson posSessionId "${session_id}" \
            --arg orderType "DINE_IN" \
            --arg note "${open_note}" \
            --argjson lines "${open_lines}" \
            '{posSessionId:$posSessionId,orderType:$orderType,note:$note,lines:$lines}')"
          api_request POST "/sale-orders" "${payload}" "${BOOTSTRAP_TOKEN}"
          http_ok || fail "Create open order ${open_note} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
        else
          REUSED_TOTAL=$((REUSED_TOTAL + 1))
        fi
      fi

      session_body="$(api_get "${BOOTSTRAP_TOKEN}" "/pos-sessions/${session_id}")"
      session_status="$(jq -r '.status' <<<"${session_body}")"
      if [[ "${work_date}" != "${BUSINESS_DATE}" ]]; then
        if [[ "${session_status}" == "OPEN" ]]; then
          api_request POST "/pos-sessions/${session_id}/close" "{}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-pos-close-${outlet_code}-${work_date}"
          http_ok || fail "Close session ${session_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
          session_body="$(api_get "${BOOTSTRAP_TOKEN}" "/pos-sessions/${session_id}")"
          session_status="$(jq -r '.status' <<<"${session_body}")"
        fi
        if [[ "${session_status}" == "CLOSED" ]]; then
          local counted_cash
          counted_cash="$(jq -r '.expectedCashAmount // 0' <<<"${session_body}")"
          local payload
          payload="$(jq -nc --arg countedCashAmount "${counted_cash}" --arg note "seed reconcile" '{countedCashAmount:($countedCashAmount|tonumber),note:$note}')"
          api_request POST "/pos-sessions/${session_id}/reconcile" "${payload}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-pos-reconcile-${outlet_code}-${work_date}"
          http_ok || fail "Reconcile session ${session_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
          CREATED_TOTAL=$((CREATED_TOTAL + 1))
        fi
      fi
    done < <(jq -r '.[] | [.outletCode,.outletType,.regionCode] | @tsv' <<<"${OUTLET_SPECS}")
    day_index=$((day_index + 1))
  done < <(jq -r '.[]' <<<"${DATE_SERIES_JSON}")
}

phase_waste_stock_count() {
  log "Phase 11/14: waste and stock count"
  local waste_ingredient_code="ING_FRESH_MILK"
  local waste_qty="25.00"
  while IFS=$'\t' read -r outlet_code region_code; do
    local outlet_id region_id ingredient_id existing_count payload waste_id
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    region_id="$(map_get "${REGION_IDS}" "${region_code}")"
    ingredient_id="$(map_get "${INGREDIENT_IDS}" "${waste_ingredient_code}")"
    existing_count="$(inventory_txn_exists "${outlet_id}" "${ingredient_id}" "WASTE_OUT" "${WASTE_DATE}" "WASTE_RECORD")"
    if (( existing_count == 0 )); then
      payload="$(jq -nc \
        --argjson regionId "${region_id}" \
        --argjson outletId "${outlet_id}" \
        --argjson ingredientId "${ingredient_id}" \
        --arg qty "${waste_qty}" \
        --arg businessDate "${WASTE_DATE}" \
        --arg reason "SEED_WASTE" \
        --arg note "SEED-WASTE:${outlet_code}:${WASTE_DATE}" \
        '{regionId:$regionId,outletId:$outletId,ingredientId:$ingredientId,qty:($qty|tonumber),businessDate:$businessDate,reason:$reason,note:$note}')"
      api_request POST "/waste-records" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create waste record for ${outlet_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      waste_id="$(json_get "${HTTP_BODY}" '.id // empty')"
      api_request POST "/waste-records/${waste_id}/post" "{}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-waste-post-${outlet_code}-${WASTE_DATE}"
      http_ok || fail "Post waste record ${waste_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    local session_note session_id session_body session_status
    session_note="SEED-STOCK-COUNT:${outlet_code}:${STOCK_COUNT_DATE}"
    session_id="$(find_stock_count_session_id_by_note "${outlet_id}" "${session_note}")"
    if [[ -z "${session_id}" ]]; then
      local ingredient_ids_json
      ingredient_ids_json="$(jq -nc --argjson map "${INGREDIENT_IDS}" '[ $map[] | tonumber ]')"
      payload="$(jq -nc \
        --argjson regionId "${region_id}" \
        --argjson outletId "${outlet_id}" \
        --arg countDate "${STOCK_COUNT_DATE}" \
        --arg note "${session_note}" \
        --argjson ingredientIds "${ingredient_ids_json}" \
        '{regionId:$regionId,outletId:$outletId,countDate:$countDate,note:$note,ingredientIds:$ingredientIds}')"
      api_request POST "/stock-count-sessions" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Create stock count session for ${outlet_code} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      session_id="$(json_get "${HTTP_BODY}" '.id // empty')"
    else
      REUSED_TOTAL=$((REUSED_TOTAL + 1))
    fi

    session_body="$(api_get "${BOOTSTRAP_TOKEN}" "/stock-count-sessions/${session_id}")"
    session_status="$(jq -r '.status' <<<"${session_body}")"
    if [[ "${session_status}" == "DRAFT" ]]; then
      api_request POST "/stock-count-sessions/${session_id}/start" "{}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Start stock count session ${session_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
      session_body="$(api_get "${BOOTSTRAP_TOKEN}" "/stock-count-sessions/${session_id}")"
      session_status="$(jq -r '.status' <<<"${session_body}")"
    fi

    if [[ "${session_status}" == "COUNTING" ]]; then
      local lines_payload='[]'
      while IFS= read -r line; do
        local ingredient_id system_qty actual_qty ingredient_index
        ingredient_id="$(jq -r '.ingredientId' <<<"${line}")"
        system_qty="$(jq -r '.systemQty' <<<"${line}")"
        ingredient_index=$((ingredient_id % 11))
        actual_qty="$(python3 - "${system_qty}" "${ingredient_index}" <<'PY'
import sys
from decimal import Decimal, ROUND_HALF_UP
system_qty = Decimal(sys.argv[1])
ingredient_index = int(sys.argv[2])
if ingredient_index % 7 == 0:
    actual = max(Decimal("0.00"), system_qty - Decimal("2.00"))
elif ingredient_index % 5 == 0:
    actual = system_qty + Decimal("1.00")
else:
    actual = system_qty
print(actual.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
PY
)"
        lines_payload="$(jq -c \
          --argjson ingredientId "${ingredient_id}" \
          --arg actualQty "${actual_qty}" \
          '. + [{ingredientId:$ingredientId,actualQty:($actualQty|tonumber),note:"seed count"}]' <<<"${lines_payload}")"
      done < <(jq -c '.lines[]' <<<"${session_body}")
      payload="$(jq -nc --argjson lines "${lines_payload}" '{lines:$lines}')"
      api_request PUT "/stock-count-sessions/${session_id}/lines" "${payload}" "${BOOTSTRAP_TOKEN}"
      http_ok || fail "Update stock count session ${session_id} lines failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))

      api_request POST "/stock-count-sessions/${session_id}/post" "{}" "${BOOTSTRAP_TOKEN}" "Idempotency-Key: seed-stock-count-post-${outlet_code}-${STOCK_COUNT_DATE}"
      http_ok || fail "Post stock count session ${session_id} failed with ${HTTP_STATUS}: ${HTTP_BODY}"
      CREATED_TOTAL=$((CREATED_TOTAL + 1))
    fi
  done < <(jq -r '.[] | [.outletCode,.regionCode] | @tsv' <<<"${OUTLET_SPECS}")
}

phase_verify() {
  log "Phase 12/14: verify counts, business effects and reports"
  local users_body employees_body products_body recipes_body prices_body
  users_body="$(api_get "${BOOTSTRAP_TOKEN}" "/users?search=$(urlencode "seed-")&page=0&size=100")"
  employees_body="$(api_get "${BOOTSTRAP_TOKEN}" "/employees?search=$(urlencode "SEED-EMP-")&page=0&size=100")"
  products_body="$(api_get "${BOOTSTRAP_TOKEN}" "/products?limit=500")"
  recipes_body="$(api_get "${BOOTSTRAP_TOKEN}" "/recipes")"
  prices_body="$(api_get "${BOOTSTRAP_TOKEN}" "/product-prices")"

  assert_equals "$(jq -r '.items | length' <<<"${users_body}")" "27" "seed user count"
  assert_equals "$(jq -r '.items | length' <<<"${employees_body}")" "50" "seed employee count"
  assert_equals "$(jq -r '[ .[] | select((.code // "") | startswith("FB_")) ] | length' <<<"${products_body}")" "20" "seed product count"
  assert_equals "$(jq -r '[ .[] | select((.recipeCode // "") | startswith("RCP_FB_")) ] | length' <<<"${recipes_body}")" "20" "seed recipe count"
  assert_equals "$(jq -r --arg historyStart "${HISTORY_START}" '[ .[] | select(.scopeType == "GLOBAL" and .priceType == "RETAIL" and .effectiveFrom == $historyStart) ] | length' <<<"${prices_body}")" "20" "global retail price count"

  local product_code
  while IFS= read -r product_code; do
    local product_id recipe_id recipe_count price_count
    product_id="$(map_get "${PRODUCT_IDS}" "${product_code}")"
    recipe_count="$(jq -r --argjson productId "${product_id}" '[ .[] | select(.productId == $productId) ] | length' <<<"${recipes_body}")"
    price_count="$(jq -r --argjson productId "${product_id}" '[ .[] | select(.productId == $productId and .priceType == "RETAIL") ] | length' <<<"${prices_body}")"
    assert_gte "${recipe_count}" 1 "recipe coverage ${product_code}"
    assert_gte "${price_count}" 1 "price coverage ${product_code}"
  done < <(jq -r '.[].code' <<<"${PRODUCT_SPECS}")

  while IFS=$'\t' read -r outlet_code; do
    local outlet_id stock_balances txn_sale txn_purchase
    outlet_id="$(map_get "${OUTLET_IDS}" "${outlet_code}")"
    stock_balances="$(api_get "${BOOTSTRAP_TOKEN}" "/stock-balances?outletId=${outlet_id}&page=0&size=200")"
    assert_gte "$(jq -r '.items | length' <<<"${stock_balances}")" 20 "stock balances ${outlet_code}"
    if jq -e '.items[] | select(.qtyOnHand < 0)' >/dev/null 2>&1 <<<"${stock_balances}"; then
      fail "negative stock detected at ${outlet_code}"
    fi
    VERIFIED_TOTAL=$((VERIFIED_TOTAL + 1))
    txn_sale="$(api_get "${BOOTSTRAP_TOKEN}" "/inventory-transactions?outletId=${outlet_id}&txnType=SALE_USAGE&from=${HISTORY_START}&to=${BUSINESS_DATE}&page=0&size=50")"
    assert_gte "$(jq -r '.items | length' <<<"${txn_sale}")" 1 "sale usage inventory movement ${outlet_code}"
    txn_purchase="$(api_get "${BOOTSTRAP_TOKEN}" "/inventory-transactions?outletId=${outlet_id}&txnType=PURCHASE_IN&from=${HISTORY_START}&to=${BUSINESS_DATE}&page=0&size=50")"
    assert_gte "$(jq -r '.items | length' <<<"${txn_purchase}")" 1 "purchase inventory movement ${outlet_code}"
  done < <(jq -r '.[].outletCode' <<<"${OUTLET_SPECS}")

  local procurement_po_body procurement_gr_body procurement_invoice_body procurement_payment_body payroll_runs_body
  procurement_po_body="$(api_get "${BOOTSTRAP_TOKEN}" "/purchase-orders?limit=200")"
  procurement_gr_body="$(api_get "${BOOTSTRAP_TOKEN}" "/goods-receipts?limit=200")"
  procurement_invoice_body="$(api_get "${BOOTSTRAP_TOKEN}" "/supplier-invoices?limit=200")"
  procurement_payment_body="$(api_get "${BOOTSTRAP_TOKEN}" "/supplier-payments?limit=200")"
  payroll_runs_body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs?limit=200")"

  assert_gte "$(jq -r '[ .[] | select((.note // "") | startswith("SEED-PO:")) ] | length' <<<"${procurement_po_body}")" 13 "procurement purchase orders"
  assert_gte "$(jq -r '[ .[] | select((.note // "") | startswith("SEED-GR:")) ] | length' <<<"${procurement_gr_body}")" 8 "procurement goods receipts"
  assert_gte "$(jq -r '[ .[] | select((.invoiceNumber // "") | startswith("SEED-INV-")) ] | length' <<<"${procurement_invoice_body}")" 8 "procurement invoices"
  assert_gte "$(jq -r '[ .[] | select((.transactionRef // "") | startswith("SEED-SPAY-")) ] | length' <<<"${procurement_payment_body}")" 6 "procurement payments"
  assert_gte "$(jq -r '[ .[] | select((.note // "") | startswith("SEED-PAYROLL-RUN:")) ] | length' <<<"${payroll_runs_body}")" 4 "payroll runs"

  local paid_run_id paid_run_body
  paid_run_id="$(map_get "${PAYROLL_RUN_IDS}" "NORTH:${PREVIOUS_YEAR_MONTH}:PAID")"
  paid_run_body="$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs/${paid_run_id}")"
  assert_equals "$(jq -r '.status' <<<"${paid_run_body}")" "PAID" "paid payroll run status"
  assert_gte "$(jq -r '.employees | length' <<<"${paid_run_body}")" 1 "paid payroll run employee results"

  local revenue_query inventory_outlet_id north_region_id report_body
  revenue_query="$(build_repeated_outlet_ids_query "NORTH")&$(build_repeated_outlet_ids_query "SOUTH")"
  if [[ "${BUSINESS_DATE}" == "${LOCAL_TODAY}" ]]; then
    retry_until_non_empty "${BOOTSTRAP_TOKEN}" "/reports/revenue/outlet-stats/today?${revenue_query}" 'length' "revenue outlet stats today" >/dev/null
  else
    assert_non_empty_json "$(api_get "${BOOTSTRAP_TOKEN}" "/reports/revenue/outlet-stats/today?${revenue_query}")" 'type' "revenue outlet stats today response"
  fi

  inventory_outlet_id="$(map_get "${OUTLET_IDS}" "HN_FLAGSHIP")"
  retry_until_non_empty "${BOOTSTRAP_TOKEN}" "/reports/inventory/stock-balance-snapshots?outletId=${inventory_outlet_id}&page=0&size=50" '.items | length' "inventory stock balance snapshots" >/dev/null
  retry_until_non_empty "${BOOTSTRAP_TOKEN}" "/reports/inventory/transaction-facts?outletId=${inventory_outlet_id}&from=${HISTORY_START}&to=${BUSINESS_DATE}&page=0&size=50" '.items | length' "inventory transaction facts" >/dev/null

  north_region_id="$(map_get "${REGION_IDS}" "NORTH")"
  report_body="$(retry_until_non_empty "${BOOTSTRAP_TOKEN}" "/reports/payroll/summary?regionId=${north_region_id}&fromDate=${PREVIOUS_MONTH_START}&toDate=${CURRENT_PERIOD_END}" '.runCount' "payroll summary")"
  assert_gte "$(jq -r '.runCount' <<<"${report_body}")" 1 "payroll summary run count"

  assert_non_empty_json "$(api_get "${BOOTSTRAP_TOKEN}" "/products?limit=10")" 'length' "catalog list"
  assert_non_empty_json "$(api_get "${BOOTSTRAP_TOKEN}" "/employees?search=$(urlencode "SEED-EMP-")&page=0&size=10")" '.items | length' "HR list"
  assert_non_empty_json "$(api_get "${BOOTSTRAP_TOKEN}" "/purchase-orders?limit=10")" 'length' "procurement list"
  assert_non_empty_json "$(api_get "${BOOTSTRAP_TOKEN}" "/payroll-runs?limit=10")" 'length' "finance list"
  assert_non_empty_json "$(api_get "${BOOTSTRAP_TOKEN}" "/users?search=$(urlencode "seed-")&page=0&size=10")" '.items | length' "IAM list"
}

phase_verify_accounts() {
  log "Phase 13/14: verify representative test accounts"
  local token north_region_id north_outlets_query flagship_outlet_id
  north_region_id="$(map_get "${REGION_IDS}" "NORTH")"
  flagship_outlet_id="$(map_get "${OUTLET_IDS}" "HN_FLAGSHIP")"
  north_outlets_query="$(build_repeated_outlet_ids_query "NORTH")"

  token="$(login_token "seed-system-admin" "${DEMO_PASSWORD}" "seed-system-admin")"
  verify_endpoint_with_token "${token}" "/users?search=$(urlencode "seed-")&page=0&size=5" "system admin access"

  token="$(login_token "seed-product-manager" "${DEMO_PASSWORD}" "seed-product-manager")"
  verify_endpoint_with_token "${token}" "/products?limit=5" "product manager access"

  token="$(login_token "seed-hr-core" "${DEMO_PASSWORD}" "seed-hr-core")"
  verify_endpoint_with_token "${token}" "/employees?search=$(urlencode "SEED-EMP-")&page=0&size=5" "hr access"

  token="$(login_token "seed-finance-core" "${DEMO_PASSWORD}" "seed-finance-core")"
  verify_endpoint_with_token "${token}" "/payroll-runs?limit=5" "finance access"

  token="$(login_token "seed-rf-north" "${DEMO_PASSWORD}" "seed-rf-north")"
  verify_endpoint_with_token "${token}" "/reports/payroll/summary?regionId=${north_region_id}&fromDate=${PREVIOUS_MONTH_START}&toDate=${CURRENT_PERIOD_END}" "regional finance access"

  token="$(login_token "seed-rm-north" "${DEMO_PASSWORD}" "seed-rm-north")"
  verify_endpoint_with_token "${token}" "/reports/revenue/outlet-stats/today?${north_outlets_query}" "region manager access"

  token="$(login_token "seed-om-hn-flagship" "${DEMO_PASSWORD}" "seed-om-hn-flagship")"
  verify_endpoint_with_token "${token}" "/pos-sessions?outletId=${flagship_outlet_id}&businessDate=${BUSINESS_DATE}&limit=5" "outlet manager access"

  token="$(login_token "seed-staff-hn-flagship-01" "${DEMO_PASSWORD}" "seed-staff-hn-flagship-01")"
  verify_endpoint_with_token "${token}" "/products?limit=5" "staff access"

  token="$(login_token "seed-audit-viewer" "${DEMO_PASSWORD}" "seed-audit-viewer")"
  [[ -n "${token}" ]] || fail "audit viewer login returned empty token"
  VERIFIED_TOTAL=$((VERIFIED_TOTAL + 1))
}

phase_summary() {
  log "Phase 14/14: summary"
  printf '\n=== FERN E2E Seed Summary ===\n'
  printf 'Business date: %s\n' "${BUSINESS_DATE}"
  printf 'History days: %s\n' "${HISTORY_DAYS}"
  printf 'Created actions: %s\n' "${CREATED_TOTAL}"
  printf 'Reused actions: %s\n' "${REUSED_TOTAL}"
  printf 'Verified checks: %s\n' "${VERIFIED_TOTAL}"
  printf '\nTest account password: %s\n' "${DEMO_PASSWORD}"
  printf 'Representative accounts:\n'
  while IFS= read -r account; do
    printf '  - %s | roles=%s | scope=%s%s\n' \
      "$(jq -r '.username' <<<"${account}")" \
      "$(jq -r '.roles | join(",")' <<<"${account}")" \
      "$(jq -r 'if .system then "SYSTEM" else ((.regionCodes | join(",")) + ":" + (.outletCodes | join(","))) end' <<<"${account}")" \
      ""
  done < <(jq -c '.[]' <<<"${ACCOUNT_SUMMARY}")
  printf '=============================\n'
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd python3

  require_env FERN_BASE_URL
  require_env BOOTSTRAP_USERNAME
  require_env BOOTSTRAP_PASSWORD
  require_env BUSINESS_DATE

  [[ "${HISTORY_DAYS}" == "30" ]] || warn "HISTORY_DAYS is ${HISTORY_DAYS}; this seed is tuned for 30 days"

  phase_bootstrap
  phase_org
  phase_users
  phase_employees
  phase_catalog
  phase_inventory_initial_stock
  phase_suppliers_procurement
  phase_schedules_attendance
  phase_payroll
  phase_pos
  phase_waste_stock_count
  phase_verify
  phase_verify_accounts
  phase_summary
}

main "$@"
