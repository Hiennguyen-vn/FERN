# PostgreSQL Operational Layer

This directory documents the canonical PostgreSQL operational-data layer used by:

- `pos`
- `inventory`
- `procurement`
- `hr`
- `finance`

## Local Development

- Database name: `fern_operational`
- Owner: `fern`
- Bootstrap creation: `infrastructure/postgres/init/01-init-databases.sql`

## Operational Rules

- PostgreSQL remains the system of record for low-latency transactional paths.
- Region/outlet routing keys are stored in operational tables instead of cross-service foreign keys.
- Physical sharding by region is deferred; V1 uses a single operational database with shard-ready schemas and indexes.
