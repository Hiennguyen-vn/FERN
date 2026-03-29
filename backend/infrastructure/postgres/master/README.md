# PostgreSQL Master Layer

This directory documents the canonical PostgreSQL master-data layer used by:

- `iam`
- `org`
- `catalog`
- `procurement_master`
- `hr_master`
- `config`

## Local Development

- Database name: `fern_master`
- Owner: `fern`
- Bootstrap creation: handled by `docker-compose.yml`

## Service Ownership

- `iam-service` owns the `iam` schema
- `org-service` owns the `org` schema
- `catalog-service` owns the `catalog` schema
- `procurement-service` owns the `procurement_master` schema
- `hr-service` owns the `hr_master` schema
- `finance-service` owns the `config` schema only if numbering/policy data becomes finance-managed later; for now the schema is shared operational configuration and provisioned as DB foundation

## Migration Model

- Master-data schemas are migrated with Flyway from each owning service module.
- Cross-service hard foreign keys are intentionally avoided.
