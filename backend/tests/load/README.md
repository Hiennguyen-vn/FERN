# Load Test Harness

This directory contains a staging-first load test harness for the FERN online-only F&B platform.

## Layout

- `k6/`: executable load scenarios and shared JS helpers
- `config/`: environment, fixture, PromQL, and scenario manifests
- `scripts/`: orchestration helpers for seed, run, failover, snapshot, and report generation

## Prerequisites

- `k6`
- `jq`
- `curl`
- `bash`
- `psql` for DB-side assertions
- `kubectl` if running failover tests against Kubernetes staging

## Quick Start

1. Generate or update staging fixtures:

```bash
tests/load/scripts/seed-staging.sh
```

2. Run a single scenario:

```bash
tests/load/scripts/run-scenario.sh pos_peak_hour
```

3. Run the whole suite:

```bash
tests/load/scripts/run-suite.sh
```

Artifacts are written under `.tmp/load/<run-id>/`.

## Core Environment Variables

- `LOAD_BASE_URL`: API Gateway base URL
- `LOAD_IAM_BASE_URL`: IAM base URL for login
- `LOAD_PROMETHEUS_URL`: Prometheus API base URL
- `LOAD_ENV_FILE`: environment config JSON override
- `LOAD_SEED_FILE`: generated fixture JSON override
- `LOAD_OUTPUT_ROOT`: output root directory, default `.tmp/load`
- `LOAD_BOOTSTRAP_USERNAME`: bootstrap admin user for seeding
- `LOAD_BOOTSTRAP_PASSWORD`: bootstrap admin password for seeding
- `LOAD_PSQL_URI_MASTER`: PostgreSQL URI for master/report assertions
- `LOAD_PSQL_URI_OPERATIONAL`: PostgreSQL URI for operational assertions
- `LOAD_TARGET_MODE`: `kubernetes`, `compose`, or `noop`
- `LOAD_K8S_NAMESPACE`: staging namespace for failover actions

## Fixture Model

The harness expects a fixture file with:

- region and outlet ids
- one ingredient + one product + recipe + price + tax
- one active supplier
- per-outlet user accounts for each logical terminal

`seed-staging.sh` generates `tests/load/config/seeds/generated-staging.json` by default.

## Scenario Types

- `stateful_pos`: POS-heavy request mix with session/order/payment state per terminal
- `inventory_event_storm`: mixed POS, procurement, and inventory command event ingress

Backlog, projection-lag, soak, stress, and failover scenarios reuse `stateful_pos` with different k6 executors and orchestration hooks.
