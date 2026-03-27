# Snowflake Reporting Layer

Snowflake replaces the PostgreSQL reporting database in this repo.

## Canonical Topology

- Database: `FERN_REPORTING`
- Schemas:
  - `RAW_EVENTS`
  - `REPORT`
  - `FINANCE_PROJECTION`
  - `AUDIT`
  - `NOTIFICATION`

## Execution Model

- OLTP services write to PostgreSQL only.
- Reporting and projection services consume Kafka events and project them into Snowflake.
- No synchronous business write path depends on Snowflake availability.

## Environment Variables

- `FERN_SNOWFLAKE_ACCOUNT`
- `FERN_SNOWFLAKE_USER`
- `FERN_SNOWFLAKE_PASSWORD`
- `FERN_SNOWFLAKE_ROLE`
- `FERN_SNOWFLAKE_WAREHOUSE`
- `FERN_SNOWFLAKE_DATABASE`
- `FERN_SNOWFLAKE_SCHEMA`

## Warehouses

- `FERN_INGEST_WH` for projection writes
- `FERN_BI_WH` for dashboard and export reads

## Migrations

- `report-service` owns `RAW_EVENTS` and `REPORT`
- `finance-service` owns `FINANCE_PROJECTION`
- `audit-service` owns `AUDIT`
- `notification-service` owns `NOTIFICATION`

Use Flyway's Snowflake support with SQL migrations under the owning service modules.

For a single-command rollout from the repo root:

```bash
cp infrastructure/migration.env.example .env.migrations
./scripts/migrate-platform.sh snowflake
```

The script bootstraps warehouses and the `FERN_REPORTING` database unless `--skip-snowflake-bootstrap` is provided.
