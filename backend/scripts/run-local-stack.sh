#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=scripts/smoke/common.sh
source "${ROOT_DIR}/scripts/smoke/common.sh"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-local-stack.sh

Environment:
  KEEP_INFRA_UP=1        Keep Docker infra after shutdown (default)
  SKIP_INFRA_BOOTSTRAP=1 Reuse existing postgres/redis/kafka containers
  SKIP_BUILD=1           Skip Maven install for runnable modules
  SKIP_MIGRATE=1         Skip Flyway migration step

This script starts the full local application stack and keeps services alive
for UI/UAT checking until the process is stopped.
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

trap cleanup EXIT INT TERM

start_local_infrastructure
build_runnable_modules
apply_database_migrations
start_application_stack

log "Application stack is ready and will stay running for UI/UAT checking."
log "Press Ctrl+C to stop the app services."

while true; do
  sleep 60
done
