# Backend Truth Review and Frontend Pilot Roadmap

_Snapshot date: 2026-04-04_

_Scope snapshot: current working tree, including uncommitted changes already present in `pos-service` and `iam-service`._

## 0. Delta Since Previous Review

- `pos-service` has moved from `Usable with risk` to `Frontend-ready` for core cashier flows. `PosServiceIntegrationTest` now passes with `50` tests, `0` failures, `0` errors.
- `procurement-service` has moved from `Usable with risk` to `Frontend-ready`. `ProcurementServiceIntegrationTest` now passes with `28` tests, `0` failures, `0` errors.
- POS scope has expanded beyond cashier basics:
  - customer CRUD and loyalty history are present
  - outlet today stats are present
  - dine-in table management is now present
  - sale orders now support `promotionCode` and `tableId`
- The main hard blocker did **not** change: `iam-service` still fails startup because `V19__pos_customer_permissions.sql` inserts permission rows without the required `name` column.
- A new platform-level integration gap is now clearer: POS public APIs have expanded faster than gateway and IAM bootstrap support. The new frontend-facing gaps are:
  - missing gateway routes for `customers/**`
  - missing gateway routes for `pos-stats/**`
  - missing gateway routes for `/api/pos/tables/**`
  - missing IAM permission bootstrap for `pos.table.read`, `pos.table.write`, and `pos.table.manage`

## 1. Review Method

This review treats backend source code as the only source of truth. A flow is considered supported only when all of the following exist in code:

- public API surface in controller or gateway contract
- business logic in service layer
- persistence or migration support
- runtime evidence from integration tests or current test logs

Maturity labels used in this document:

- `Frontend-ready`: API, logic, persistence, and current test signal are aligned enough to build UI now
- `Usable with risk`: flow appears implemented, but there is a current regression, route gap, or red test that lowers delivery confidence
- `Partial`: only part of the flow is implemented or the contract is too narrow for a real screen
- `Blocked`: backend exists on paper, but current branch state prevents normal frontend integration

Primary evidence sources for this review:

- gateway shell and route contracts in `services/api-gateway`
- permission model in `platform-common`
- controllers, DTOs, services, and Flyway migrations inside each domain service
- Surefire results under `services/*/target/surefire-reports`

## 2. Executive Summary

- The backend already supports a credible `Outlet Ops` pilot across shell/navigation, org scope, catalog lookup, POS core, inventory operations, procurement core, reporting, audit, and websocket-based live event fan-out.
- Compared with the previous review, POS and procurement are materially stronger: both module-level integration suites are now green on the current branch.
- POS has expanded into more realistic F&B outlet behavior: customer and loyalty workflows, order-level promotion support, and dine-in table management are now implemented in source.
- The only hard branch-level blocker for an integrated frontend is `iam-service`: the current Flyway migration `V19__pos_customer_permissions.sql` breaks application startup, which blocks login and all authenticated frontend integration on this branch.
- The current API gateway still routes only core POS endpoints. It does **not** route `customers/**`, `pos-stats/**`, or `/api/pos/tables/**`, even though the current working tree now exposes controllers for all of them. That is a frontend integration gap, not a domain logic gap.
- The permission model also lags the new POS feature surface: `PermissionCodes` now defines `pos.table.read`, `pos.table.write`, and `pos.table.manage`, but there is no IAM migration yet to publish and assign those permissions.
- A phase-based frontend roadmap should start with outlet operations, but it should include a short pre-wave backend stabilization gate.

## 3. Frontend-Facing Platform Contracts

### 3.1 Shell, scope, and module visibility

Current frontend shell contracts already exist in the gateway:

- `GET /ui/action-hub`
- `GET /ui/shell-context`

These contracts are backed by `UiSurfaceController` and already encode:

- role/persona inference
- visible module list
- quick actions
- shell scope chips
- region and outlet selection options

The gateway currently exposes these module families:

- `home`
- `pos`
- `catalog`
- `iam`
- `audit`
- `org`
- `regional-ops`
- `hr`
- `finance`
- `procurement`
- `inventory`
- `workforce`
- `reports`

Important frontend implication:

- module visibility is prefix-based, not feature-flag-based
- `UiSurfaceController` determines visibility from permission prefixes such as `pos.`, `inventory.`, `procurement.`, `hr.`, `finance.`, `iam.`, `audit.`, and `report.`
- the frontend information architecture should mirror this prefix model, then action-gate specific buttons by exact permissions

### 3.2 Auth and access contracts

The current branch exposes the right auth and access APIs for a real frontend:

- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
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

However, these contracts are `Blocked` at branch level because `iam-service` fails startup during Flyway migration.

### 3.3 Gateway-routed paths available to frontend today

The gateway currently routes these public path groups:

- `/auth/**`
- `/users/**`, `/roles/**`, `/permissions/**`
- `/regions/**`, `/outlets/**`, `/exchange-rates/**`
- `/ingredients/**`, `/ingredient-categories/**`, `/product-categories/**`, `/units-of-measure/**`, `/uom-conversions/**`
- `/products/**`, `/recipes/**`, `/recipe-versions/**`
- `/tax-rates/**`, `/product-prices/**`, `/product-availability/**`, `/catalog/promotions/**`
- `/audit/**`
- `/pos-sessions/**`, `/sale-orders/**`
- `/stock-balances/**`, `/inventory-transactions/**`, `/stock-adjustments/**`, `/waste-records/**`, `/stock-count-sessions/**`
- `/suppliers/**`, `/purchase-orders/**`, `/goods-receipts/**`, `/supplier-invoices/**`, `/supplier-payments/**`
- `/employees/**`, `/employee-contracts/**`, `/employee-assignments/**`, `/shift-schedules/**`, `/shift-assignments/**`, `/attendance-events/**`, `/attendance-approvals/**`
- `/payroll-periods/**`, `/payroll-runs/**`, `/finance-config/**`
- `/reports/**`
- `/ws/**`

Current route gap:

- `customers/**` exists in `pos-service` source but is not routed by the gateway
- `pos-stats/**` exists in `pos-service` source but is not routed by the gateway
- `/api/pos/tables/**` exists in `pos-service` source but is not routed by the gateway

This means the current branch has a mismatch between backend capability and frontend entry path availability.

## 4. Domain Truth Matrix

### 4.1 Operational domains

| Domain | Main actors | Real backend flows supported now | Permission dependency | Evidence | Maturity | Frontend recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| Gateway UI shell | All authenticated users | Shell context, scope selection, action hub, module visibility, quick actions | Prefix-driven by `iam.*`, `pos.*`, `inventory.*`, `procurement.*`, `hr.*`, `finance.*`, `audit.*`, `report.*`, `org.*`, `catalog.*` | `UiSurfaceController`, `RouteConfig`, `ApiGatewayIntegrationTest` | Frontend-ready | Use as the navigation and persona backbone from day one |
| Org | System admin, regional ops | Region CRUD, outlet CRUD, exchange rates, scope expansion, outlet-close coordination | `org.region.*`, `org.outlet.*`, `org.scope.resolve` | `RegionController`, `OutletController`, `ExchangeRateController`, `OrgScopeController`, passing org integration tests | Frontend-ready | Wave 1 lookup and scope selector; Wave 2 admin editing |
| Catalog | HQ catalog admin, outlet ops | Product CRUD, ingredient CRUD, recipe and recipe-version lifecycle, tax, pricing, availability, promotions, reference masters, internal menu and resolution APIs | `catalog.*` | `ProductController`, `IngredientController`, `RecipeController`, `CatalogPricingController`, `PromotionController`, `InternalCatalogController`, passing catalog tests | Frontend-ready | Wave 1 lookup and selling dependencies; Wave 2 backoffice maintenance |
| POS core | Cashier, outlet manager | POS session open/list/get/close/reconcile, sale order create/list/get/snapshot/update, payment capture, complete, cancel, order-level promotion support, order-level table assignment fields | `pos.session.*`, `pos.order.*` | `PosSessionController`, `SaleOrderController`, `PosOrderService`, `PosPricingService`, `PosServiceIntegrationTest` passing with `50` tests, `0` failures, `0` errors | Frontend-ready | Wave 1 core pilot flow |
| POS customer and stats | Cashier, outlet manager | Customer create/get/update/search, loyalty history, outlet today stats | `pos.customer.read`, `pos.customer.write`, `pos.session.read` | `CustomerController`, `PosStatsController`, `PosCustomerService`, `PosStatsService`, POS module tests are green, but IAM migration and gateway route gaps remain | Usable with risk | Keep in Wave 1 only after IAM and gateway route alignment |
| POS dine-in and table ops | Cashier, floor manager, outlet manager | Dining table create/update/get/list, table status transitions, assignment to orders, release after completion/cancel | `pos.table.read`, `pos.table.write`, `pos.table.manage` | `DineInController`, `PosDineInService`, `V9__dine_in_and_promotion.sql`, sale-order model now carries `tableId` and `tableName` | Usable with risk | Plan for late Wave 1 or Wave 2 after gateway and IAM bootstrap are fixed |
| Inventory | Outlet manager, stock controller | Stock balances, inventory ledger, stock count lifecycle, stock adjustment lifecycle, waste lifecycle, sale reservation internal API, outlet close check | `inventory.balance.read`, `inventory.ledger.read`, `inventory.adjustment.write`, `inventory.waste.write`, `inventory.stock_count.*` | `InventoryReadController`, `InventoryCommandController`, `InternalInventoryController`, `StockCountService`, `StockReservationService`, passing inventory tests | Frontend-ready | Wave 1 |
| Procurement core | Buyer, outlet manager, finance reviewer | Supplier create/list/update/activate, PO draft-submit-approve-issue-cancel, GR create-receive-post-cancel, supplier invoice create/approve/dispute, supplier payment record | `procurement.supplier.*`, `procurement.po.*`, `procurement.gr.*`, `procurement.invoice.*`, `procurement.payment.*` | `SupplierController`, `PurchaseOrderController`, `GoodsReceiptController`, `SupplierInvoiceController`, `SupplierPaymentController`, `PurchaseFlowService`, `ProcurementServiceIntegrationTest` passing with `28` tests, `0` failures, `0` errors | Frontend-ready | Wave 1 for supplier + PO + GR; Wave 2 for invoice/payment review screens |
| Notification/live ops | Outlet ops, regional ops | WebSocket endpoint, outlet-level POS topic fan-out, ops alert ingest, DLQ ingest, webhook delivery | No dedicated published permission model yet; visibility should be tied to parent module permissions | `WebSocketConfig`, `NotificationEventConsumer`, passing notification tests | Usable with risk | Optional Wave 1 enhancement for live operations; do not build a full inbox yet |

### 4.2 Admin, workforce, and analytics domains

| Domain | Main actors | Real backend flows supported now | Permission dependency | Evidence | Maturity | Frontend recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| IAM | System admin, all users | Login, refresh, logout, user CRUD, role assignment, scope assignment, permission overrides, effective access, JWKS | `iam.*` | `AuthController`, `UserController`, `RoleController`, `PermissionController`, failing `IamServiceIntegrationTest` on Flyway migration | Blocked | Fix before integrated frontend work starts |
| HR | HR ops, outlet manager, staff | Employee CRUD, contracts, assignments, shift schedules, shift assignments, attendance events, attendance approval and rejection, internal effective contracts and approved attendance | `hr.employee.*`, `hr.contract.*`, `hr.shift.*`, `hr.attendance.*`, `hr.payroll.prepare` | `HrCommandController`, `HrReadController`, `InternalHrController`, `HrAttendanceService`, passing HR integration tests | Frontend-ready | Wave 2; workforce approval queue can be added earlier if needed |
| Finance payroll | Finance lead | Payroll periods and runs, submit, approve, reject, cancel, mark paid, numbering rules, system policies, outlet close check | `finance.payroll.*`, `finance.config.*` | `FinanceCommandController`, `FinanceReadController`, `InternalFinanceController`, `PayrollRunOrchestrator`, passing finance tests | Frontend-ready | Wave 3, or late Wave 2 for finance-only users |
| Reports | Regional ops, finance, executives | Revenue today, inventory projections, payroll summary/run detail, export job create/list/get/preview/download, projection freshness | `report.*`, `report.payroll.*` | `ReportRevenueController`, `ReportInventoryController`, `ReportController`, `ReportExportController`, `ReportExportService`, passing report tests | Frontend-ready | Wave 2 |
| Audit | System admin, security, ops leads | Audit event list/detail, security event list/detail, request trace list/detail | `audit.*` | `AuditController`, passing audit tests | Frontend-ready | Wave 2 |

## 5. Current Test Health and What It Really Means

### 5.1 Services with passing module tests in the current branch

- `api-gateway`
- `org-service`
- `catalog-service`
- `pos-service`
- `inventory-service`
- `procurement-service`
- `hr-service`
- `finance-service`
- `report-service`
- `audit-service`
- `notification-service`

### 5.2 Services with red module tests in the current branch

| Service | Current failure signal | What it means for frontend |
| --- | --- | --- |
| IAM | `IamServiceIntegrationTest` fails to load `ApplicationContext` because `V19__pos_customer_permissions.sql` inserts into `iam.permission` without the required `name` column | Hard blocker for login and any authenticated frontend integration on the current branch |

## 6. Backend Gaps That Block or Distort Frontend Work

| Gap | Backend truth | Frontend impact | Required action |
| --- | --- | --- | --- |
| IAM startup failure | `V19__pos_customer_permissions.sql` inserts only `code` and `description`, but current schema requires `name` | Blocks real login and authenticated shell integration | Fix migration first; do not start integrated frontend on this branch until IAM boots cleanly |
| Gateway route mismatch for POS customer, stats, and dine-in tables | `CustomerController`, `PosStatsController`, and `DineInController` exist, but `RouteConfig` still routes only `/pos-sessions/**` and `/sale-orders/**` to POS | Customer search, loyalty history, outlet today stats, and table management screens cannot be reached through the gateway | Add `/customers/**`, `/pos-stats/**`, and `/api/pos/tables/**` gateway routes before building those screens |
| Missing IAM bootstrap for table permissions | `PermissionCodes` defines `pos.table.read`, `pos.table.write`, and `pos.table.manage`, but no IAM migration currently inserts and assigns these permissions | Even after gateway routing, dine-in screens cannot rely on stable permission-based access control | Add an IAM migration to publish and assign table permissions to the intended roles |
| Inconsistent public path shape for dine-in tables | New POS table controller uses `/api/pos/tables`, while existing gateway-routed POS paths use root-level prefixes like `/pos-sessions` and `/sale-orders` | Frontend routing and gateway mapping become more brittle and less uniform | Decide whether to keep `/api/pos/tables/**` as-is and route it, or normalize it into the existing POS public path style |
| Notification has no frontend read model | WebSocket fan-out exists, but there is no REST inbox, history query, ack, or alert preference API | Build live feed panels only; do not promise a full notification center yet | Keep Wave 1 notification scope to transient live signals only |

## 7. Frontend Roadmap Based on Current Backend Truth

### Pre-wave stabilization gate

Complete these backend items before integrated frontend development:

- fix IAM migration so `iam-service` starts and login works
- add gateway routes for `customers/**`, `pos-stats/**`, and `/api/pos/tables/**`
- publish and assign `pos.table.read`, `pos.table.write`, and `pos.table.manage`

### Wave 1: Outlet Ops pilot

Target users:

- cashier
- outlet manager
- stock controller at outlet level

Recommended screens and flows:

- login, token refresh, logout
- app shell using `/ui/action-hub` and `/ui/shell-context`
- region and outlet scope selection
- POS session list, open session, session detail, close, reconcile
- sale order create, detail, line update, payment capture, complete, cancel
- promotion-aware order pricing inside the sale flow
- product and pricing lookup required by POS
- customer search, create, update, loyalty history
- outlet today stats
- stock balances by outlet
- stock count session create, start, enter lines, post, cancel
- stock adjustment create, post, cancel
- waste record create, post, cancel
- supplier list/create/update
- purchase order create, edit draft, submit, approve, issue
- goods receipt create, receive, post
- optional live POS event panel via `/ws` and `/topic/pos/{outletId}`

Wave 1 intentionally avoids:

- payroll operations
- IAM administration screens
- advanced exports
- notification inbox/history workflows

Wave 1.5, after gateway and IAM bootstrap fixes:

- dining table list and status board
- table assignment during dine-in order creation
- simple floor-operation flow for `AVAILABLE -> OCCUPIED -> AVAILABLE/RESERVED/CLEANING`

### Wave 2: HQ backoffice and supervisory operations

Target users:

- regional ops
- procurement reviewer
- HR reviewer
- audit and reporting consumers

Recommended screens and flows:

- catalog maintenance for product, ingredient, recipe, pricing, tax, promotion, and availability
- procurement invoice and payment review
- attendance review and approval queues
- revenue dashboard and inventory reporting
- audit event, security event, and request trace lookup
- selected workforce screens for attendance visibility

### Wave 3: Platform admin and finance operations

Target users:

- system admin
- finance lead
- payroll operations

Recommended screens and flows:

- user, role, scope, and permission-override administration
- effective-access inspection
- payroll periods and payroll run lifecycle
- finance configuration maintenance
- export-job center and advanced reconciliation/reporting surfaces

## 8. Recommended Frontend Sequencing Rules

- Build the frontend against gateway contracts first, not direct service URLs.
- Treat scope selection as a first-class shell concern because most operational flows are outlet- or region-scoped.
- Mirror module visibility from permission prefixes in the shell, then enforce button-level actions by exact permissions.
- Do not introduce a separate BFF in phase 1 unless new UI aggregation needs appear after the gateway route gaps are fixed.
- Keep notification scope narrow until a read model exists.

## 9. Decision Summary

- Start frontend with `Outlet Ops`, not with IAM admin or payroll.
- Do not wait for every domain to be perfect before building Wave 1.
- Do fix IAM startup and gateway POS route gaps before integrating the frontend against the current branch.
- POS and procurement are no longer the main blockers; the main blockers are now IAM startup, gateway exposure for new POS APIs, and IAM permission bootstrap for dine-in tables.
