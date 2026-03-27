# F&B ERP System - Canonical Database Topology

## Overview

The system uses PostgreSQL for source-of-truth OLTP data and Snowflake for reporting and projection workloads.

### Canonical Topology

| Layer | Platform | Database | Schemas |
| --- | --- | --- | --- |
| Master | PostgreSQL | `fern_master` | `iam`, `org`, `catalog`, `procurement_master`, `hr_master`, `config` |
| Operational | PostgreSQL | `fern_operational` | `pos`, `inventory`, `procurement`, `hr`, `finance` |
| Reporting | Snowflake | `FERN_REPORTING` | `RAW_EVENTS`, `REPORT`, `FINANCE_PROJECTION`, `AUDIT`, `NOTIFICATION` |

This replaces the older all-PostgreSQL reporting assumption. Reporting, projection, audit, and notification persistence now live in Snowflake.

## Schema Ownership

### PostgreSQL Master

| Schema | Owning Service | Purpose | Migration Location |
| --- | --- | --- | --- |
| `iam` | `iam-service` | Identity, roles, permissions, auth session metadata | `services/iam-service/src/main/resources/db/migration` |
| `org` | `org-service` | Currency, region hierarchy, outlet master, scope projection | `services/org-service/src/main/resources/db/migration` |
| `catalog` | `catalog-service` | Product, ingredient, pricing, tax, recipe master data | `services/catalog-service/src/main/resources/db/migration/postgresql/master` |
| `procurement_master` | `procurement-service` | Supplier master and coverage | `services/procurement-service/src/main/resources/db/migration/postgresql/master` |
| `hr_master` | `hr-service` | Employee profile and contract master | `services/hr-service/src/main/resources/db/migration/postgresql/master` |
| `config` | `finance-service` | Shared numbering and policy configuration | `services/finance-service/src/main/resources/db/migration/postgresql/master` |

### PostgreSQL Operational

| Schema | Owning Service | Purpose | Migration Location |
| --- | --- | --- | --- |
| `pos` | `pos-service` | POS session, order, payment, promotion, refund, void | `services/pos-service/src/main/resources/db/migration/postgresql/operational` |
| `inventory` | `inventory-service` | Inventory ledger, balances, counts, waste, adjustments | `services/inventory-service/src/main/resources/db/migration/postgresql/operational` |
| `procurement` | `procurement-service` | PO, GR, supplier invoice, supplier payment | `services/procurement-service/src/main/resources/db/migration/postgresql/operational` |
| `hr` | `hr-service` | Assignment, schedule, attendance event and approval | `services/hr-service/src/main/resources/db/migration/postgresql/operational` |
| `finance` | `finance-service` | Payroll run and expense source-of-truth | `services/finance-service/src/main/resources/db/migration/postgresql/operational` |

### Snowflake Reporting

| Schema | Owning Service | Purpose | Migration Location |
| --- | --- | --- | --- |
| `RAW_EVENTS` | `report-service` | Append-only landing zone for projected Kafka events | `services/report-service/src/main/resources/db/migration/snowflake` |
| `REPORT` | `report-service` | Fact tables, daily summaries, export jobs | `services/report-service/src/main/resources/db/migration/snowflake` |
| `FINANCE_PROJECTION` | `finance-service` | Accounting and reconciliation projections | `services/finance-service/src/main/resources/db/migration/snowflake` |
| `AUDIT` | `audit-service` | Audit, security, and trace projections | `services/audit-service/src/main/resources/db/migration/snowflake` |
| `NOTIFICATION` | `notification-service` | Notification jobs, delivery attempts, webhook logs | `services/notification-service/src/main/resources/db/migration/snowflake` |

## Canonical Domain Mapping

### Master Data

- `iam`
  - `user_account`
  - `role`
  - `permission`
  - `role_permission`
  - `user_role_assignment`
  - `user_scope_assignment`
  - `user_permission_override`
  - `auth_session`
  - `policy_version_state`
  - `scope_version_state`
  - `outbox_event`
- `org`
  - `currency`
  - `exchange_rate`
  - `region`
  - `outlet`
  - `region_closure`
  - `legal_entity`
  - `outlet_config`
  - `scope_version_state`
  - `outbox_event`
- `catalog`
  - `product_category`
  - `ingredient_category`
  - `unit_of_measure`
  - `uom_conversion`
  - `ingredient`
  - `product`
  - `recipe`
  - `recipe_version`
  - `recipe_version_ingredient`
  - `tax_rate`
  - `product_price`
  - `product_outlet_availability`
  - `outbox_event`
- `procurement_master`
  - `supplier`
  - `supplier_contact`
  - `supplier_region_coverage`
- `hr_master`
  - `employee_profile`
  - `employee_contract`
  - `employee_bank_account`
- `config`
  - `document_numbering_rule`
  - `system_policy`

### Operational Data

- `pos`
  - `pos_session`
  - `sale_order`
  - `sale_order_line`
  - `sale_payment`
  - `sale_snapshot`
  - `promotion`
  - `promotion_scope`
  - `sale_order_line_promotion`
  - `refund_request`
  - `void_request`
  - `outbox_event`
- `inventory`
  - `inventory_transaction`
  - `stock_balance`
  - `stock_count_session`
  - `stock_count_line`
  - `waste_record`
  - `stock_adjustment`
  - `availability_projection`
  - `inbox_event`
  - `outbox_event`
- `procurement`
  - `purchase_order`
  - `purchase_order_line`
  - `goods_receipt`
  - `goods_receipt_line`
  - `supplier_invoice`
  - `supplier_invoice_line`
  - `supplier_payment`
  - `supplier_payment_allocation`
  - `outbox_event`
- `hr`
  - `employee_assignment`
  - `shift_schedule`
  - `shift_assignment`
  - `attendance_event`
  - `attendance_approval`
  - `outbox_event`
- `finance`
  - `payroll_period`
  - `payroll_run`
  - `payroll_employee_result`
  - `expense_record`
  - `expense_inventory_purchase`
  - `expense_operating`
  - `expense_other`
  - `expense_payroll`
  - `outbox_event`

### Reporting And Projection Data

- `RAW_EVENTS`
  - `EVENT_LANDING`
- `REPORT`
  - `SALES_FACT`
  - `PAYMENT_FACT`
  - `INVENTORY_MOVEMENT_FACT`
  - `PROCUREMENT_FACT`
  - `ATTENDANCE_FACT`
  - `PAYROLL_FACT`
  - `EXPENSE_FACT`
  - `REGION_DAILY_SUMMARY`
  - `COMPANY_DAILY_SUMMARY`
  - `EXPORT_JOB`
- `FINANCE_PROJECTION`
  - `ACCOUNTING_POSTING_PROJECTION`
  - `RECONCILIATION_SNAPSHOT`
- `AUDIT`
  - `AUDIT_EVENT`
  - `SECURITY_EVENT`
  - `REQUEST_TRACE`
- `NOTIFICATION`
  - `ALERT_RULE`
  - `WEBHOOK_ENDPOINT`
  - `NOTIFICATION_JOB`
  - `DELIVERY_ATTEMPT`
  - `WEBHOOK_DELIVERY_LOG`

## Data Rules

- PostgreSQL master and operational data remain the only mutation path for business transactions.
- Snowflake is projection-only. No synchronous write path depends on Snowflake availability.
- Cross-service hard foreign keys are not used. Services reference external entities by scalar IDs and indexed business keys.
- Domain statuses are modeled with `VARCHAR` plus `CHECK` constraints instead of PostgreSQL enum types.
- Every event-producing schema includes `outbox_event`.
- Async consumers or derived stores use `inbox_event` or projection checkpoint patterns for idempotency.
- Snowflake fact and projection tables carry `source_event_id`, `source_service`, `event_type`, `occurred_at`, `ingested_at`, and `idempotency_key`.

## Migration Workflow

### PostgreSQL Bootstrap

1. Start local infrastructure with `docker compose up -d postgres redis kafka`
2. PostgreSQL init scripts create `fern_master` and `fern_operational`
3. Run Flyway migrations per owning service against the correct database
4. Local Docker PostgreSQL listens on `127.0.0.1:55432` by default

Config templates:
- `infrastructure/postgres/master/flyway.conf.example`
- `infrastructure/postgres/operational/flyway.conf.example`
- `infrastructure/migration.env.example`
- `scripts/migrate-platform.sh`

### Snowflake Bootstrap

1. Create `FERN_REPORTING` and required warehouses with `infrastructure/snowflake/reporting/01-bootstrap-reporting.sql`
2. Run Flyway migrations from the Snowflake-owning service modules

Config template:
- `infrastructure/snowflake/reporting/flyway.conf.example`

Preferred repo entrypoint:
- `./scripts/migrate-platform.sh all`

## Event Projection Flow

1. Source service writes to PostgreSQL and appends to `outbox_event`
2. Outbox publisher emits Kafka event
3. Projection consumers land the event into `FERN_REPORTING.RAW_EVENTS.EVENT_LANDING`
4. Projection processors upsert into `REPORT`, `FINANCE_PROJECTION`, `AUDIT`, or `NOTIFICATION`

This document is the canonical reconcile point between the architecture docs and the database coverage derived from the original ERP schema inventory.
