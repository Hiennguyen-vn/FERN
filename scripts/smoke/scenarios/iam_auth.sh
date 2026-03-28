if [[ -z "${FERN_SMOKE_COMMON_LOADED:-}" ]]; then
  # shellcheck source=../common.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"
fi

scenario_iam_auth_login() {
  scenario_start "iam_auth_login"

  local gateway_login_token
  gateway_login_token="$(login_access_token "${FERN_BASE_URL}" "${SMOKE_USER_USERNAME}" "${SMOKE_USER_PASSWORD}")"
  refresh_bootstrap_token

  http_expect_status 200 GET "${FERN_BASE_URL}/regions/${REGION_ID}" "" "Bearer ${gateway_login_token}" >/dev/null

  sleep 1
  refresh_smoke_access_token
  refresh_outsider_access_token

  scenario_pass "UC-IAM-04"
}

scenario_iam_auth_logout() {
  scenario_start "iam_auth_logout"

  http_expect_status 204 POST "${FERN_BASE_URL}/auth/logout" '{}' "Bearer ${SMOKE_ACCESS_TOKEN}" >/dev/null
  local revoked_status
  revoked_status="$(http_status GET "${FERN_BASE_URL}/regions/${REGION_ID}" "Bearer ${SMOKE_ACCESS_TOKEN}")"
  if [[ "${revoked_status}" != "401" ]]; then
    fail "Expected revoked token to return 401, got ${revoked_status}"
  fi

  scenario_pass "UC-IAM-05"
}
