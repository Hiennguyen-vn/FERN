#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "${SCRIPT_DIR}/common.sh"

SCENARIOS=(
  pos_peak_hour
  multi_outlet_concurrency
  payment_burst
  inventory_event_storm
  outbox_inbox_backlog
  projection_lag
  soak
  stress
  failover_under_load
)

for scenario in "${SCENARIOS[@]}"; do
  "${SCRIPT_DIR}/run-scenario.sh" "${scenario}"
done

log "Completed load suite ${LOAD_RUN_ID}"
