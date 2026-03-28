#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# shellcheck source=scripts/smoke/common.sh
source "${ROOT_DIR}/scripts/smoke/common.sh"
# shellcheck source=scripts/smoke/scenarios/setup_shared.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/setup_shared.sh"
# shellcheck source=scripts/smoke/scenarios/iam_auth.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/iam_auth.sh"
# shellcheck source=scripts/smoke/scenarios/hr_attendance.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/hr_attendance.sh"
# shellcheck source=scripts/smoke/scenarios/inventory_outlet.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/inventory_outlet.sh"
# shellcheck source=scripts/smoke/scenarios/pos_order_to_cash.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/pos_order_to_cash.sh"
# shellcheck source=scripts/smoke/scenarios/procure_to_pay.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/procure_to_pay.sh"
# shellcheck source=scripts/smoke/scenarios/payroll_and_reports.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/payroll_and_reports.sh"
# shellcheck source=scripts/smoke/scenarios/audit_traceability.sh
source "${ROOT_DIR}/scripts/smoke/scenarios/audit_traceability.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/smoke/run-all.sh

Environment:
  FERN_BASE_URL=http://localhost:8080  Gateway base URL
  KEEP_INFRA_UP=1                      Keep Docker infra after the run
  SKIP_INFRA_BOOTSTRAP=1               Reuse existing postgres/redis/kafka containers
  SKIP_BUILD=1                         Skip Maven install for runnable modules
  SKIP_MIGRATE=1                       Skip Flyway migration step
EOF
}

for arg in "$@"; do
  case "${arg}" in
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: ${arg}"
      ;;
  esac
done

trap cleanup EXIT

start_local_infrastructure
build_runnable_modules
apply_database_migrations
start_application_stack

scenario_setup_shared
scenario_iam_auth_login
scenario_hr_attendance
scenario_inventory_outlet
scenario_pos_order_to_cash
scenario_procure_to_pay
scenario_payroll_and_reports
scenario_audit_traceability
scenario_iam_auth_logout

summarize_smoke_success
