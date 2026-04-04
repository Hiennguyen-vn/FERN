# Backend Truth Review and Frontend Pilot Roadmap

_Snapshot date: 2026-04-04_

_Scope snapshot: current working tree under `backend`; `frontend/` is still empty, so this deliverable stops at flow planning and screen-bundle sequencing._

## 0. Delta Since Prior Review

- The previous branch-level blockers in IAM and gateway are now closed on the current working tree:
  - `iam-service` boots cleanly again; `V19__pos_customer_permissions.sql` and `V20__pos_table_permissions.sql` are present and covered by integration tests.
  - `api-gateway` now routes `/customers/**`, `/pos-stats/**`, and `/api/pos/tables/**` to POS.
- `IAM`, `POS customer/stats`, and `POS dine-in/table ops` move from `Blocked` or `Usable with risk` to `Frontend-ready` on this branch.
- The current rerun of module integration suites is green across all frontend-relevant services. There is no current red-suite hard blocker for an integrated frontend pilot.
- The remaining frontend-impacting gaps are narrower and mostly about public contract shape rather than missing domain logic:
  - notification has live transport and webhook delivery, but no frontend inbox/history/ack API
  - outlet-close coordination exists only through internal service-to-service APIs
  - the shell exposes `/regional-ops`, but there is no dedicated regional-ops public API family
  - POS tables use a non-uniform public path shape: `/api/pos/tables/**`
- `Outlet Ops` is still the right first frontend slice. The difference now is that the pilot is no longer blocked by core backend readiness.

## 1. Platform Contracts

### 1.1 Review Standard

This review treats backend source code as the only source of truth. A flow is considered supported only when all of the following exist in the current branch:

- public API surface in a gateway route or controller
- business logic in service layer
- persistence or migration support
- runtime evidence from rerun integration suites or current Surefire output

Maturity labels used in this document:

- `Frontend-ready`: public API, logic, persistence, and current runtime signal are aligned enough to build UI now
- `Usable with risk`: usable for a bounded UI flow, but there is still a contract or platform limitation that the frontend must work around
- `Partial`: backend support exists only for a narrow slice of the workflow, or the public contract is missing a screen-critical part
- `Blocked`: the current branch cannot support normal frontend integration for that flow

Important review rule:

- Internal APIs are not counted as frontend-ready contracts. When a flow exists only through `/internal/**`, it is treated as a backend dependency, not a frontend capability.

### 1.2 Verification Snapshot

Rerun on the current branch used targeted integration suites and current Surefire output. Format below is `tests/failures/errors`.

- `iam-service`: `IamServiceIntegrationTest` `21/0/0`
- `api-gateway`: `ApiGatewayIntegrationTest` `20/0/0`
- `org-service`: `OrgServiceIntegrationTest` `10/0/0`
- `catalog-service`: `CatalogServiceIntegrationTest` `23/0/0`, `CatalogAuditScopeIntegrationTest` `3/0/0`
- `pos-service`: `PosServiceIntegrationTest` `50/0/0`
- `inventory-service`: `InventoryServiceIntegrationTest` `41/0/0`
- `procurement-service`: `ProcurementServiceIntegrationTest` `28/0/0`
- `hr-service`: `HrServiceIntegrationTest` `27/0/0`
- `finance-service`: `FinanceServiceIntegrationTest` `2/0/0`, `FinanceSecurityIntegrationTest` `18/0/0`, `FinanceProcurementConsumerHardeningTest` `13/0/0`
- `report-service`: `ReportServiceIntegrationTest` `21/0/0`
- `audit-service`: `AuditServiceIntegrationTest` `6/0/0`
- `notification-service`: `NotificationServiceIntegrationTest` `10/0/0`

Current conclusion from rerun evidence:

- there is no active red-suite blocker across the services that matter for frontend integration
- frontend sequencing can now be driven by business priority and contract shape, not by core branch instability

### 1.3 Frontend-Facing Platform Contract

#### Auth and access contract

The current branch exposes a complete frontend auth and access base:

- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /.well-known/jwks.json`
- `GET /users`
- `POST /users`
- `GET /users/{id}`
- `PATCH /users/{id}`
- `POST /users/{id}/roles`
- `POST /users/{id}/scopes`
- `GET /users/{id}/permission-overrides`
- `PUT /users/{id}/permission-overrides`
- `GET /users/{id}/effective-access`
- `GET /roles`
- `POST /roles`
- `GET /permissions`

Frontend implication:

- the shell can rely on JWT plus `/users/{id}/effective-access` for exact permission gating
- the admin UI can be deferred to Wave 3 without blocking login, shell, or Wave 1 outlet flows

#### Shell and scope contract

The gateway already exposes the two shell contracts that a frontend can anchor on:

- `GET /ui/action-hub`
- `GET /ui/shell-context`

These responses already encode:

- persona inference
- visible module list
- quick actions
- scope chips
- region and outlet selector options

Module visibility is prefix-driven. The shell currently infers module visibility from permission families such as:

- `pos.*`
- `inventory.*`
- `procurement.*`
- `hr.*`
- `finance.*`
- `iam.*`
- `audit.*`
- `org.*`
- `catalog.*`
- `report.*`

Frontend implication:

- use permission prefixes for navigation visibility
- use exact permission codes for button-level actions

#### Gateway-routed public path families

The gateway currently exposes these frontend-facing route families:

- `/auth/**`
- `/users/**`, `/roles/**`, `/permissions/**`
- `/regions/**`, `/outlets/**`, `/exchange-rates/**`
- `/ingredients/**`, `/ingredient-categories/**`, `/product-categories/**`, `/units-of-measure/**`, `/uom-conversions/**`
- `/products/**`, `/recipes/**`, `/recipe-versions/**`
- `/tax-rates/**`, `/product-prices/**`, `/product-availability/**`, `/catalog/promotions/**`
- `/audit/**`
- `/pos-sessions/**`, `/sale-orders/**`, `/customers/**`, `/pos-stats/**`, `/api/pos/tables/**`
- `/stock-balances/**`, `/inventory-transactions/**`, `/stock-adjustments/**`, `/waste-records/**`, `/stock-count-sessions/**`
- `/suppliers/**`, `/purchase-orders/**`, `/goods-receipts/**`, `/supplier-invoices/**`, `/supplier-payments/**`
- `/employees/**`, `/employee-contracts/**`, `/employee-assignments/**`, `/shift-schedules/**`, `/shift-assignments/**`, `/attendance-events/**`, `/attendance-approvals/**`
- `/payroll-periods/**`, `/payroll-runs/**`, `/finance-config/**`
- `/reports/**`
- `/ws/**`

#### Live ops transport contract

The notification service currently gives the frontend a usable live transport, but not a full notification center:

- WebSocket/SockJS endpoint: `/ws`
- broker topics: `/topic/**`, `/queue/**`
- POS event fan-out topic: `/topic/pos/{outletId}`
- backend also ingests `ops.alert` and configured DLQ topics for webhook delivery

Frontend implication:

- a live outlet panel is feasible
- a full inbox/history/acknowledgement screen is not yet supported by a public REST contract

## 2. Domain Truth Matrix

| Domain | Actors | Supported public flows and endpoints | Lifecycle/state in code | Permission dependency | Evidence | Maturity | Frontend impact |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Gateway shell and scope | All authenticated users | `/ui/action-hub`, `/ui/shell-context`; visible modules, quick actions, scope chips, region/outlet selector | Shell rehydrates from JWT claims plus selected region/outlet query params; no separate persisted UI state | Prefix-driven by `pos.*`, `inventory.*`, `procurement.*`, `hr.*`, `finance.*`, `iam.*`, `audit.*`, `org.*`, `catalog.*`, `report.*` | `UiSurfaceController`, `RouteConfig`, `ApiGatewayIntegrationTest` `20/0/0` | Frontend-ready | Use as the shared shell and navigation backbone from day one |
| IAM auth and access admin | System admin, all users | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /.well-known/jwks.json`, `/users/**`, `/roles/**`, `/permissions` | login -> refresh -> logout is implemented; user status changes affect login; role assignment, scope assignment, and permission overrides flow into `/effective-access` | `iam.*` plus the exact domain permissions surfaced through `/effective-access` | `AuthController`, `UserController`, `RoleController`, `PermissionController`, `SecurityMetadataController`, `V19`, `V20`, `IamServiceIntegrationTest` `21/0/0` | Frontend-ready | Auth shell integration can start immediately; admin console can be deferred to Wave 3 |
| Org master data | System admin, regional ops | `/regions/**`, `/outlets/**`, `/exchange-rates/**` for CRUD and lookup | Public lifecycle is CRUD-only; outlet-close coordination exists internally but is not exposed as a frontend contract | `org.region.*`, `org.outlet.*` | `RegionController`, `OutletController`, `ExchangeRateController`, `OrgServiceIntegrationTest` `10/0/0` | Frontend-ready | Use in Wave 1 for scope selector and outlet lookup; keep outlet-close UI out of scope for now |
| Catalog selling dependencies | HQ catalog admin, outlet ops | `/products/**`, `/ingredients/**`, `/recipes/**`, `/recipe-versions/**`, `/tax-rates/**`, `/product-prices/**`, `/product-availability/**`, `/catalog/promotions/**` | CRUD and versioning are public; recipe version activation/archive is public; internal resolution APIs exist but are not required for the first frontend | `catalog.*` | `ProductController`, `IngredientController`, `RecipeController`, `CatalogPricingController`, `PromotionController`, `CatalogServiceIntegrationTest` `23/0/0`, `CatalogAuditScopeIntegrationTest` `3/0/0` | Frontend-ready | Wave 1 can consume products, prices, availability, and promotions directly |
| POS cashier core | Cashier, outlet manager | `/pos-sessions/**`, `/sale-orders/**` for session open/list/get/close/reconcile, order create/list/get/snapshot/update, payments, complete, cancel | Session `OPEN -> CLOSED -> RECONCILED`; order `OPEN -> COMPLETED/CANCELLED`; payment `UNPAID -> PARTIALLY_PAID -> PAID`; pricing supports promotions and table assignment fields | `pos.session.*`, `pos.order.*` | `PosSessionController`, `SaleOrderController`, `PosOrderService`, `PosPricingService`, `PosServiceIntegrationTest` `50/0/0` | Frontend-ready | Core Wave 1 pilot flow |
| POS customer, loyalty, and outlet stats | Cashier, outlet manager | `/customers`, `/customers/{id}`, `/customers/{id}/loyalty-transactions`, `/pos-stats/today` | Customer create/get/update/search is public; loyalty history is public; outlet operational stats are public by outlet list | `pos.customer.read`, `pos.customer.write`, `pos.session.read` | `CustomerController`, `PosStatsController`, `PosCustomerService`, `PosStatsService`, gateway route coverage in `ApiGatewayIntegrationTest`, IAM coverage in `IamServiceIntegrationTest` | Frontend-ready | Include in Wave 1 for customer lookup, loyalty context, and outlet dashboard cards |
| POS dine-in and table ops | Cashier, floor manager, outlet manager | `/api/pos/tables`, `/api/pos/tables/{id}`, `/api/pos/tables/{id}/status` for create/update/get/list/status update | Table states include `AVAILABLE`, `RESERVED`, `OCCUPIED`, `CLEANING`; order creation can assign a table, completion/cancel can release it | `pos.table.read`, `pos.table.write`, `pos.table.manage` | `DineInController`, `PosDineInService`, `V9__dine_in_and_promotion.sql`, `V20__pos_table_permissions.sql`, `PosServiceIntegrationTest` `50/0/0`, gateway and IAM integration coverage | Frontend-ready | Ready for Wave 1 if the pilot includes dine-in; otherwise keep as Wave 1.5 |
| Inventory operations | Outlet manager, stock controller | `/stock-balances`, `/inventory-transactions`, `/stock-adjustments/**`, `/waste-records/**`, `/stock-count-sessions/**` | Adjustment and waste `DRAFT -> POSTED/CANCELLED`; stock count `DRAFT -> STARTED -> POSTED/CANCELLED`; internal sale reservation exists but is not a frontend contract | `inventory.balance.read`, `inventory.ledger.read`, `inventory.adjustment.write`, `inventory.waste.write`, `inventory.stock_count.*` | `InventoryReadController`, `InventoryCommandController`, `InventoryServiceIntegrationTest` `41/0/0` | Frontend-ready | Strong Wave 1 candidate |
| Procurement sourcing and receiving | Buyer, outlet manager | `/suppliers/**`, `/purchase-orders/**`, `/goods-receipts/**` | Supplier create/update/activate; PO `DRAFT -> SUBMITTED -> APPROVED -> ISSUED/CANCELLED`; GR `DRAFT -> RECEIVED -> POSTED/CANCELLED` | `procurement.supplier.*`, `procurement.po.*`, `procurement.gr.*` | `SupplierController`, `PurchaseOrderController`, `GoodsReceiptController`, `PurchaseFlowService`, `ProcurementServiceIntegrationTest` `28/0/0` | Frontend-ready | Wave 1 procurement slice |
| Procurement payables | Finance reviewer, procurement reviewer | `/supplier-invoices/**`, `/supplier-payments/**` | Invoice create/list/get plus `APPROVE` and `DISPUTE`; supplier payment record allocates approved invoices; idempotency and scope checks are covered | `procurement.invoice.*`, `procurement.payment.*` | `SupplierInvoiceController`, `SupplierPaymentController`, `PayablesService`, `ProcurementServiceIntegrationTest` `28/0/0` | Frontend-ready | Wave 2; no need to pull into Wave 1 unless finance review is in pilot scope |
| HR workforce | HR ops, outlet manager, staff | `/employees/**`, `/employee-contracts/**`, `/employee-assignments/**`, `/shift-schedules/**`, `/shift-assignments/**`, `/attendance-events/**`, `/attendance-approvals/**` | Attendance goes from event capture to `APPROVED/REJECTED`; employee, contract, assignment, and shift flows are public; internal effective-contract and approved-attendance APIs are not frontend contracts | `hr.employee.*`, `hr.contract.*`, `hr.shift.*`, `hr.attendance.*`, `hr.payroll.prepare` | `HrCommandController`, `HrReadController`, `HrServiceIntegrationTest` `27/0/0` | Frontend-ready | Wave 2, or earlier if the pilot needs attendance review |
| Finance payroll and config | Finance lead, payroll ops | `/payroll-periods/**`, `/payroll-runs/**`, `/finance-config/**` | Payroll run lifecycle is public: `DRAFT -> SUBMITTED -> APPROVED/REJECTED/CANCELLED -> PAID`; detail visibility is gated by `finance.payroll.detail.read`; internal outlet-close check is not a frontend contract | `finance.payroll.*`, `finance.config.*` | `FinanceCommandController`, `FinanceReadController`, `FinanceServiceIntegrationTest` `2/0/0`, `FinanceSecurityIntegrationTest` `18/0/0`, `FinanceProcurementConsumerHardeningTest` `13/0/0` | Frontend-ready | Wave 3, or late Wave 2 for finance-only users |
| Reports and export jobs | Regional ops, finance, executives | `/reports/revenue/outlet-stats/today`, `/reports/inventory/**`, `/reports/payroll/**`, `/reports/exports/**` | Read-model queries are public; export job create/list/get/preview/download is public; projection freshness is internal only | `report.read`, `report.export`, `report.payroll.read`, `report.payroll.export` | `ReportRevenueController`, `ReportInventoryController`, `ReportController`, `ReportExportController`, `ReportServiceIntegrationTest` `21/0/0` | Frontend-ready | Wave 2 dashboards and export center |
| Audit lookup | System admin, security, ops leads | `/audit/events`, `/audit/events/{id}`, `/audit/security-events`, `/audit/security-events/{id}`, `/audit/request-traces`, `/audit/request-traces/{id}` | Public contract is list/detail lookup only; `audit.export` permission exists in shared codes, but there is no public audit export endpoint yet | `audit.read`, `audit.detail.read` | `AuditController`, `AuditServiceIntegrationTest` `6/0/0` | Frontend-ready | Wave 2 read screens are safe; do not plan audit export yet |
| Notification and live ops | Outlet ops, regional ops | Public frontend transport is only `/ws/**` via gateway; live topic fan-out to `/topic/pos/{outletId}` | POS Kafka events fan out live; `ops.alert` and DLQ ingestion drive webhook delivery; there is no public inbox/history/ack workflow | No dedicated `notification.*` family yet; current gating should follow parent module access | `WebSocketConfig`, `NotificationEventConsumer`, `NotificationService`, `NotificationServiceIntegrationTest` `10/0/0` | Usable with risk | Optional Wave 1 live panel only; do not build a full notification center on current backend |

Internal APIs intentionally excluded from the matrix as frontend contracts:

- `/internal/scopes/expand`
- `/internal/inventory/**`
- `/internal/procurement/**`
- `/internal/hr/**`
- `/internal/finance/**`
- `/internal/report/**`

Those APIs are real backend dependencies, but they do not yet justify frontend screens unless a public contract is added on top.

## 3. Frontend Roadmap and Blockers

### 3.1 Recommended Roadmap

#### Pre-wave: contract lock, not backend rescue

There is no current hard backend blocker that should delay frontend kickoff. Pre-wave work should focus on contract locking and UI planning:

- build the frontend against gateway contracts only
- derive navigation from `/ui/action-hub` and `/ui/shell-context`
- use `/users/{id}/effective-access` and exact permission codes for action gating
- explicitly exclude outlet-close orchestration and a full notification center from the pilot backlog
- decide whether the pilot includes dine-in tables; the backend is ready, so this is now a product sequencing decision, not a backend rescue task

#### Wave 1: Outlet Ops pilot

Target users:

- cashier
- outlet manager
- stock controller

Recommended screen bundles:

- login, refresh, logout
- app shell and scope selector
- POS session list/open/detail/close/reconcile
- sale order create/edit/detail/payment/complete/cancel
- product, price, availability, and promotion lookup inside the POS flow
- customer search/create/update and loyalty history
- outlet today stats
- stock balances
- stock count session create/start/enter lines/post/cancel
- stock adjustment create/post/cancel
- waste record create/post/cancel
- supplier list/create/update
- purchase order create/edit draft/submit/approve/issue
- goods receipt create/receive/post
- dine-in tables and status board if the pilot includes table service
- optional live outlet panel via `/ws` and `/topic/pos/{outletId}`

#### Wave 2: HQ backoffice and supervisory flows

Target users:

- regional ops
- procurement reviewer
- HR reviewer
- audit and reporting consumers

Recommended screen bundles:

- catalog maintenance for products, ingredients, recipes, pricing, tax, availability, and promotions
- supplier invoice review and dispute flow
- supplier payment review and record flow
- attendance review and approval queue
- revenue, inventory, and payroll reporting
- export job center
- audit event, security event, and request trace lookup
- regional ops screens composed from existing org, procurement, inventory, and report APIs

#### Wave 3: platform admin and finance operations

Target users:

- system admin
- finance lead
- payroll ops

Recommended screen bundles:

- user, role, scope, and permission-override administration
- effective-access inspection
- payroll period and payroll run lifecycle
- finance configuration maintenance
- advanced admin-only reporting and export utilities

### 3.2 Current Backend Gaps That Still Matter To Frontend

| Category | Gap | Backend truth | Frontend impact | Recommended action |
| --- | --- | --- | --- | --- |
| Missing public capability | Outlet-close orchestration is internal-only | Org service coordinates inventory, procurement, and finance close checks through `/internal/**`, but there is no gateway-routed public outlet-close workflow contract | A real outlet close screen is blocked on the current backend | Keep outlet close out of Wave 1; add a public orchestration API before designing that screen |
| Missing public capability | Notification center read model is absent | `/ws` exists for live fan-out, and notification jobs/webhook delivery are implemented, but there is no public REST API for list/history/ack/preferences | A full notification inbox or alert history screen is blocked | Keep live ops to transient feed panels only; add REST read APIs before planning a notification center |
| Permission model gap | Notification has no dedicated published permission family | Live ops visibility currently has to piggyback on parent modules like POS or ops-facing roles | Frontend cannot model notification as a standalone module with clean role assignment | Gate live panels through existing parent-module permissions in phase 1; add `notification.*` only if product needs independent control |
| Platform aggregation gap | `/regional-ops` is a shell grouping, not a dedicated API family | The shell exposes a `regional-ops` module and quick action, but there is no `/regional-ops/**` backend route family or composite dashboard API | Regional dashboards must be composed in the frontend from existing org/report/inventory/procurement calls | Accept frontend composition in Wave 2; add a BFF only if the composition becomes too expensive or too slow |
| Contract inconsistency | POS dine-in path shape is non-uniform | Tables use `/api/pos/tables/**` while other POS APIs use root-level prefixes | Client routing and API client code need one explicit exception | Document it and move on; do not block Wave 1 on path normalization |
| Regression or red test | No active red-suite blocker in the rerun set | All targeted integration suites listed above are currently green | There is no test-based reason to delay frontend kickoff | Start frontend work; keep re-running the same suites as a contract safety net |

### 3.3 Sequencing Rules

- Treat `pos-stats/today` as operational data for outlet screens; treat `/reports/**` as supervisory or HQ reporting.
- Do not count internal controllers as hidden frontend contracts. If a screen needs a new public endpoint, call it out explicitly instead of tunneling through `/internal/**`.
- Do not introduce a separate BFF in Wave 1 unless the Wave 2 regional-ops composition proves too chatty or too slow.
- Keep notification scope intentionally narrow until a public read model exists.
