# Backup, Restore, Retention, And NFR Validation

## Scope

This runbook covers:

- PostgreSQL backup and restore for master and operational schemas
- Redis and Kafka recovery expectations
- reporting projection rebuild order
- export artifact restore behavior
- data retention defaults
- validation checklist for idempotency and immutable-state guarantees

## Source Of Truth

- PostgreSQL business schemas are the source of truth for operational data.
- `raw_events.event_landing`, reporting facts, audit rows, and notification delivery tables are durable rebuild targets when the backing PostgreSQL data exists.
- Redis is not a source of truth. It is cache and token acceptance state only.
- Kafka is not a source of truth. Missing topics must be rebuilt from PostgreSQL data and outbox/raw landing tables when available.
- Export artifacts on filesystem are disposable derivatives. Metadata in `report.export_job` is the durable audit surface.

## Backup Policy

### PostgreSQL

- Run physical or managed snapshots daily for the cluster backing `fern_master`.
- Run logical dumps for schema-level recovery at least daily:
  - `pg_dump --schema=iam --schema=org --schema=catalog --schema=inventory --schema=procurement --schema=pos --schema=hr --schema=finance --schema=report --schema=audit --schema=notification fern_master`
- Keep WAL or point-in-time recovery enabled for the production cluster.
- Validate restore on a non-production environment at least quarterly.

### Redis

- No backup is required for correctness.
- Preserve configuration-as-code for Redis sizing and eviction policy.
- After restore or replacement, allow services to repopulate token version keys and caches.

### Kafka

- Retain broker backups only for faster recovery, not correctness.
- Treat topic loss as a rebuild event.
- Preserve topic configuration and ACL definitions in infrastructure code.

### Filesystem Export Artifacts

- Keep export artifacts on shared storage for 30 days.
- Do not treat artifact files as canonical backups.
- If files are lost but `report.export_job` metadata remains, regenerate from the saved export request.

## Restore Order

### Full Environment Restore

1. Restore PostgreSQL cluster or schema dumps.
2. Start Redis empty and allow services to warm token acceptance and cache entries.
3. Start Kafka brokers and recreate required topics if needed.
4. Start business services with outbox publishing disabled if point-in-time consistency needs inspection.
5. Rebuild reporting, audit, and notification derivatives from PostgreSQL.
6. Re-enable publishers and consumers after validation.

### Reporting Rebuild Order

1. Verify business tables are consistent in `finance`, `inventory`, `procurement`, `pos`, and `hr`.
2. Preserve or rebuild `raw_events.event_landing` if still available.
3. Truncate reporting derivative tables in this order:
   - `report.company_daily_summary`
   - `report.region_daily_summary`
   - `report.expense_fact`
   - `report.payroll_fact`
   - `report.attendance_fact`
   - `report.procurement_fact`
   - `report.inventory_movement_fact`
   - `report.payment_fact`
   - `report.sales_fact`
4. Replay durable events from raw landing or reconstruct from business source tables plus outbox history.
5. Validate row counts and daily totals by region before reopening report exports.

### Notification Rebuild Order

1. Restore `notification.notification_job`, `notification.delivery_attempt`, and `notification.webhook_delivery_log` from PostgreSQL backup when possible.
2. If notification tables are lost, regenerate only from durable operational failure sources that still exist:
   - terminal outbox failures
   - failed export jobs
   - DLQ detections that were persisted elsewhere
3. Do not mutate completed jobs back to `PENDING`; create a new job if replay is required.

## Failure-Specific Procedures

### Export Artifact Missing

1. Confirm `report.export_job.status = COMPLETED`.
2. Confirm the stored request payload still exists.
3. Regenerate the artifact into the configured export directory.
4. Update `file_path`, `checksum`, and `expires_at` only if a new artifact is produced from the same request.
5. Do not alter `requested_at`, `started_at`, `completed_at`, or `row_count`.

### Outbox Terminal Failure

1. Identify the service and failed row from the `fern_outbox_terminal_failures_total` alert and service logs.
2. Inspect `last_error`, `retry_count`, aggregate identifiers, and payload.
3. Fix the downstream dependency or payload defect.
4. Replay by creating a new outbox row or a service-owned replay tool.
5. Do not overwrite the failed row into `PENDING`; terminal rows are immutable audit evidence.

### DLQ Message Detected

1. Identify the original topic, partition, and offset from notification payload.
2. Determine whether the message can be safely replayed.
3. If replay is safe, re-publish with original idempotency key semantics preserved.
4. If replay is unsafe, repair the business state first and document the operator decision.

## Retention Defaults

- Audit projections and webhook delivery logs: 365 days online
- Export job metadata: 180 days online
- Export files on filesystem: 30 days
- Payroll, payment, and related export audit metadata required for compliance: 7 years
- Business source-of-truth tables follow domain-specific legal retention and must not be shortened by reporting cleanup jobs

## Immutable-State Rules

- `report.export_job` terminal states `COMPLETED` and `FAILED` are immutable except for cleanup of expired artifacts or metadata retention operations.
- `notification.notification_job` terminal states `SENT` and `FAILED` are immutable.
- `sale_payment` terminal business rows are never rewritten by reporting or notification post-processing.
- `finance.payroll_run` state is never mutated by report or notification flows.
- Audit rows are append-only. No service should update or delete emitted audit events.

## Idempotency Validation Checklist

Run these checks on every release affecting async flows:

- replay the same `finance.expense.posted` event and confirm no duplicate `report.expense_fact`
- replay the same sales completion event and confirm no duplicate `sales_fact` or `payment_fact`
- submit the same export request with the same `Idempotency-Key` and confirm exactly one `report.export_job`
- replay the same `ops.alert` payload and confirm exactly one `notification.notification_job`
- exhaust notification retries and confirm terminal failed jobs are not retried again
- verify region and company summaries recompute to the same totals after replay

## Recovery Validation Checklist

- Compare reporting totals with source-of-truth SQL for a known date range and region.
- Confirm `fern_projection_consumer_lag` returns to an acceptable steady state.
- Confirm no pending outbox rows are stuck beyond the service retry window.
- Confirm notification webhook delivery has resumed and no new DLQ alerts are firing.
- Confirm export download works for a freshly generated artifact and expires correctly after the retention window.
