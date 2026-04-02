#!/usr/bin/env bash
# =============================================================================
# seed-ui-test-users.sh — Dedicated UI/UX test accounts (separate from demo-*)
# =============================================================================
# Creates users with the same role + scope matrix as seed-demo.sh, using
# prefix "uitest-" so you can log in without clashing with demo seed data.
#
# Prerequisites:
#   - Gateway reachable (FERN_BASE_URL)
#   - Org seed: region DEMO-HCM and outlets DEMO-HCM-DIST1, DEMO-HCM-DIST3
#     must exist (run ./scripts/seed-demo.sh once, or create org via API)
#
# Usage:
#   ./scripts/seed-ui-test-users.sh
#
# Environment:
#   FERN_BASE_URL=http://localhost:8080
#   BOOTSTRAP_USERNAME=bootstrap-admin
#   BOOTSTRAP_PASSWORD=Admin123!
#   UITEST_PASSWORD=UiTest2026!
# =============================================================================

set -euo pipefail

FERN_BASE_URL="${FERN_BASE_URL:-http://localhost:8080}"
BOOTSTRAP_USERNAME="${BOOTSTRAP_USERNAME:-bootstrap-admin}"
BOOTSTRAP_PASSWORD="${BOOTSTRAP_PASSWORD:-Admin123!}"
UITEST_PASSWORD="${UITEST_PASSWORD:-UiTest2026!}"
BOOTSTRAP_TOKEN_FILE="${TMPDIR:-/tmp}/fern-uitest-bootstrap-token.$$"
BOOTSTRAP_REFRESH_FILE="${TMPDIR:-/tmp}/fern-uitest-bootstrap-refresh.$$"

log() { printf '[uitest-users] %s\n' "$*"; }
fail() { printf '[uitest-users] ERROR: %s\n' "$*" >&2; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || fail "Missing: $1"; }

require_cmd curl
require_cmd python3

cleanup() { rm -f "${BOOTSTRAP_TOKEN_FILE}" "${BOOTSTRAP_REFRESH_FILE}"; }
trap cleanup EXIT

curl -fsS "${FERN_BASE_URL}/actuator/health" >/dev/null 2>&1 \
  || fail "Gateway not reachable at ${FERN_BASE_URL}. Start the stack first."

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$1"
}

collection_find_field_value() {
  local body="$1"
  local out_field="$2"
  shift 2 || true
  printf '%s' "${body}" | python3 -c '
import json, sys
out_field = sys.argv[1]
pairs = sys.argv[2:]
assert len(pairs) % 2 == 0, "expected field/value pairs"
data = json.load(sys.stdin)
items = data.get("items", data) if isinstance(data, dict) else data
if not isinstance(items, list):
    items = []
for item in items:
    matched = True
    for index in range(0, len(pairs), 2):
        field = pairs[index]
        expected = pairs[index + 1]
        value = item.get(field)
        if value is None or str(value) != expected:
            matched = False
            break
    if matched:
        value = item.get(out_field)
        if value is not None:
            print(value)
            break
' "$out_field" "$@"
}

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

json_field() {
  local field="$1"; local value="$2"
  printf '%s' "${value}" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['${field}'])"
}

json_id() {
  printf '%s' "$1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

BOOTSTRAP_TOKEN=""
refresh_bootstrap_token() {
  if [[ -n "${BOOTSTRAP_TOKEN}" ]]; then
    return
  fi
  if [[ -f "${BOOTSTRAP_TOKEN_FILE}" ]]; then
    BOOTSTRAP_TOKEN="$(cat "${BOOTSTRAP_TOKEN_FILE}")"
    if [[ -n "${BOOTSTRAP_TOKEN}" ]]; then
      return
    fi
  fi
  # Prefer refresh-token exchange (avoids /auth/login rate limits: 10/min on gateway).
  if [[ -f "${BOOTSTRAP_REFRESH_FILE}" ]] && [[ -s "${BOOTSTRAP_REFRESH_FILE}" ]]; then
    local rt
    rt="$(cat "${BOOTSTRAP_REFRESH_FILE}")"
    perform_request POST "${FERN_BASE_URL}/auth/refresh" "{\"refreshToken\":\"${rt}\"}" ""
    if [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]; then
      BOOTSTRAP_TOKEN="$(json_field accessToken "${HTTP_BODY}")"
      printf '%s' "${BOOTSTRAP_TOKEN}" > "${BOOTSTRAP_TOKEN_FILE}"
      local new_rt
      new_rt="$(printf '%s' "${HTTP_BODY}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("refreshToken") or "")')"
      if [[ -n "${new_rt}" ]]; then
        printf '%s' "${new_rt}" > "${BOOTSTRAP_REFRESH_FILE}"
      fi
      return
    fi
    rm -f "${BOOTSTRAP_REFRESH_FILE}"
  fi
  local attempt=1
  local max_attempts=6
  while (( attempt <= max_attempts )); do
    perform_request POST "${FERN_BASE_URL}/auth/login" \
      "{\"username\":\"${BOOTSTRAP_USERNAME}\",\"password\":\"${BOOTSTRAP_PASSWORD}\"}" ""
    if [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]; then
      BOOTSTRAP_TOKEN="$(json_field accessToken "${HTTP_BODY}")"
      printf '%s' "${BOOTSTRAP_TOKEN}" > "${BOOTSTRAP_TOKEN_FILE}"
      local new_rt
      new_rt="$(printf '%s' "${HTTP_BODY}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("refreshToken") or "")')"
      if [[ -n "${new_rt}" ]]; then
        printf '%s' "${new_rt}" > "${BOOTSTRAP_REFRESH_FILE}"
      fi
      return
    fi
    if [[ "${HTTP_STATUS}" == "429" ]]; then
      sleep $(( attempt * 2 ))
      attempt=$(( attempt + 1 ))
      continue
    fi
    fail "HTTP ${HTTP_STATUS} for POST ${FERN_BASE_URL}/auth/login: ${HTTP_BODY}"
  done
  fail "HTTP 429 for login after ${max_attempts} attempts"
}

bootstrap_request() {
  local method="$1"; local path="$2"; local body="${3:-}"
  shift 3 || true
  local attempt=1
  local max_attempts=6
  while (( attempt <= max_attempts )); do
    refresh_bootstrap_token
    perform_request "${method}" "${FERN_BASE_URL}${path}" "${body}" "Bearer ${BOOTSTRAP_TOKEN}" "$@"
    if [[ "${HTTP_STATUS}" == "401" ]]; then
      BOOTSTRAP_TOKEN=""
      rm -f "${BOOTSTRAP_TOKEN_FILE}"
      # refresh_bootstrap_token will try /auth/refresh before login again
      attempt=$(( attempt + 1 ))
      continue
    fi
    if [[ "${HTTP_STATUS}" == "429" ]]; then
      sleep $(( attempt ))
      attempt=$(( attempt + 1 ))
      continue
    fi
    return
  done
}

bootstrap_get() {
  local path="$1"
  shift || true
  bootstrap_request GET "${path}" "" "$@"
  [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]] \
    || fail "HTTP ${HTTP_STATUS} for GET ${path}: ${HTTP_BODY}"
  printf '%s' "${HTTP_BODY}"
}

find_region_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/regions?search=$(urlencode "${code}")&page=0&size=100")"
  collection_find_field_value "${body}" id code "${code}"
}

find_outlet_id_by_code() {
  local code="$1"
  local body
  body="$(bootstrap_get "/outlets?search=$(urlencode "${code}")&page=0&size=100")"
  collection_find_field_value "${body}" id code "${code}"
}

find_user_id_by_username() {
  local username="$1"
  local body
  body="$(bootstrap_get "/users?search=$(urlencode "${username}")&page=0&size=100")"
  collection_find_field_value "${body}" id username "${username}"
}

create_uitest_user() {
  local username="$1"; local full_name="$2"; local roles_json="$3"
  local scope_json="${4:-}"

  refresh_bootstrap_token

  bootstrap_request POST "/users" \
    "{\"username\":\"${username}\",\"password\":\"${UITEST_PASSWORD}\",\"fullName\":\"${full_name}\",\"status\":\"ACTIVE\"}"

  local user_id
  if [[ "${HTTP_STATUS}" == "409" ]]; then
    user_id="$(find_user_id_by_username "${username}")"
    [[ -n "${user_id}" ]] || fail "409 for user ${username} but not resolvable from /users"
    log "  ${username} already exists → id=${user_id}" >&2
  elif [[ "${HTTP_STATUS}" -ge 200 && "${HTTP_STATUS}" -lt 300 ]]; then
    user_id="$(json_id "${HTTP_BODY}")"
    log "  Created ${username} → id=${user_id}" >&2
  else
    fail "Failed to create ${username}: ${HTTP_STATUS} ${HTTP_BODY}"
  fi

  bootstrap_request POST "/users/${user_id}/roles" \
    "{\"roleCodes\":${roles_json}}" >/dev/null 2>&1 || true

  # assignRoles bumps policy version — same JWT cannot be used for the next IAM call.
  BOOTSTRAP_TOKEN=""
  rm -f "${BOOTSTRAP_TOKEN_FILE}"

  if [[ -n "${scope_json}" ]]; then
    bootstrap_request POST "/users/${user_id}/scopes" \
      "${scope_json}" >/dev/null 2>&1 || true
  fi

  # assignScopes bumps scope version — invalidate cached access (refresh still works).
  BOOTSTRAP_TOKEN=""
  rm -f "${BOOTSTRAP_TOKEN_FILE}"

  printf '%s' "${user_id}"
}

log "Resolving org scope (DEMO-HCM)…"
REGION_ID="$(find_region_id_by_code DEMO-HCM)"
[[ -n "${REGION_ID}" ]] || fail "Region DEMO-HCM not found. Run ./scripts/seed-demo.sh first (or create the region)."

OUTLET_D1="$(find_outlet_id_by_code DEMO-HCM-DIST1)"
[[ -n "${OUTLET_D1}" ]] || fail "Outlet DEMO-HCM-DIST1 not found. Run ./scripts/seed-demo.sh first."

OUTLET_D3="$(find_outlet_id_by_code DEMO-HCM-DIST3)"
[[ -n "${OUTLET_D3}" ]] || fail "Outlet DEMO-HCM-DIST3 not found. Run ./scripts/seed-demo.sh first."

log "regionId=${REGION_ID} outlet DIST1=${OUTLET_D1} DIST3=${OUTLET_D3}"
log "Creating uitest-* users (password: ${UITEST_PASSWORD})…"

create_uitest_user "uitest-staff" "UI Test Staff" \
  '["staff"]' \
  "{\"regionIds\":[],\"outletIds\":[${OUTLET_D1}]}" >/dev/null

create_uitest_user "uitest-outlet-mgr" "UI Test Outlet Manager" \
  '["outlet_manager"]' \
  "{\"regionIds\":[],\"outletIds\":[${OUTLET_D1}]}" >/dev/null

create_uitest_user "uitest-region-mgr" "UI Test Region Manager" \
  '["region_manager"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[]}" >/dev/null

create_uitest_user "uitest-reg-finance" "UI Test Regional Finance" \
  '["regional_finance"]' \
  "{\"regionIds\":[${REGION_ID}],\"outletIds\":[]}" >/dev/null

create_uitest_user "uitest-hr" "UI Test HR" \
  '["hr"]' \
  "{\"system\":true}" >/dev/null

create_uitest_user "uitest-finance" "UI Test Finance" \
  '["finance"]' \
  "{\"system\":true}" >/dev/null

create_uitest_user "uitest-product-mgr" "UI Test Product Manager" \
  '["product_manager"]' \
  "{\"system\":true}" >/dev/null

create_uitest_user "uitest-sysadmin" "UI Test System Admin" \
  '["system_admin"]' \
  "{\"system\":true}" >/dev/null

create_uitest_user "uitest-audit" "UI Test Audit Viewer" \
  '["audit_viewer"]' \
  "{\"system\":true}" >/dev/null

create_uitest_user "uitest-readonly" "UI Test Read-Only (edge outlet)" \
  '["regional_finance"]' \
  "{\"regionIds\":[],\"outletIds\":[${OUTLET_D3}]}" >/dev/null

cat <<EOF

======================================================================
UI TEST ACCOUNTS READY
======================================================================
Password for all: ${UITEST_PASSWORD}

  uitest-staff        staff               outlet DEMO-HCM-DIST1 only
  uitest-outlet-mgr   outlet_manager      outlet DEMO-HCM-DIST1 only
  uitest-region-mgr   region_manager      region DEMO-HCM (${REGION_ID})
  uitest-reg-finance  regional_finance    region DEMO-HCM (${REGION_ID})
  uitest-hr           hr                  system scope
  uitest-finance      finance             system scope
  uitest-product-mgr  product_manager     system scope
  uitest-sysadmin     system_admin        system scope
  uitest-audit        audit_viewer        system scope
  uitest-readonly     regional_finance    outlet DEMO-HCM-DIST3 only (edge)

Gateway: ${FERN_BASE_URL}
======================================================================
EOF
