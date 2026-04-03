# Stitch Route Mapping Matrix

This document is the implementation and QA source of truth for Stitch parity in the frontend.
`screen.png` is the visual target. `code.html` is the chrome, spacing, token, and composition target.

## Shared chrome

| Frontend Surface | Canonical Stitch Source | Mapping Mode | Required states / notes |
| --- | --- | --- | --- |
| `AppShell` | `action_hub_system_admin_view` chrome | Exact chrome + persona-aware data | Sticky glass topbar, left navigation rail, scope controls, profile slot. |
| `PosLayout` | `pos_terminal_outlet_view` chrome | Exact chrome | POS outlet-first terminal header, live status, operator context. |
| Auth shell wrapper | `login_normal_state` | Exact chrome | Pattern background, stacked auth card, security note, footer links. |

## Auth and home

| Frontend Surface | Canonical Stitch Source | Mapping Mode | Required states / notes |
| --- | --- | --- | --- |
| `/login` | `login_normal_state` | Exact | Normal sign-in state with tokenized inputs and gradient CTA. |
| `/login` loading | `login_loading_state` | Variant | Preserve same chrome while showing submit pending state. |
| `/login` error | `login_error_state` | Variant | Preserve same chrome while surfacing auth error inline. |
| `/session-expired` | `login_error_state` | Variant | Security boundary state inside auth shell. |
| `/unauthorized` | `login_error_state` | Variant | Permission-denied auth shell variant. |
| `/home` | `action_hub_system_admin_view`, `action_hub_finance_view`, `action_hub_outlet_manager_view`, `action_hub_staff_view_1`, `action_hub_staff_view_2` | Persona family | Same shell chrome, role-aware KPI, feed, quick action, and module atlas composition. |

## POS

| Frontend Surface | Canonical Stitch Source | Mapping Mode | Required states / notes |
| --- | --- | --- | --- |
| `/pos` | `pos_home_session_not_started`, `pos_home_open_session`, `pos_home_offline_blocked`, `pos_terminal_outlet_view` | Family | No-session, open-session, offline-blocked, and live terminal states. |
| `/pos/orders/:orderId` | `pos_payment_open_insufficient`, `pos_payment_split_payment_active`, `pos_payment_completed_read_only`, `pos_payment_cancelled_read_only` | Family | Active payment capture, insufficient payment, completed readonly, cancelled readonly. |
| `/pos/sessions` | `pos_session_active_overview`, `pos_session_reconciliation_flow`, `pos_session_reconciled_record` | Family | Overview list, reconciliation flow, reconciled readonly. |
| `/pos/sessions/:sessionId` | `pos_session_active_overview`, `pos_session_reconciliation_flow`, `pos_session_reconciled_record` | Family | Detail follows session lifecycle state. |

## Inventory and workforce-facing operations

| Frontend Surface | Canonical Stitch Source | Mapping Mode | Required states / notes |
| --- | --- | --- | --- |
| `/inventory/stock-balances` | `stock_overview_normal_state` | Exact | Summary-first stock overview. |
| `/inventory/transactions` | `inventory_stock_moves`, `inventory_ledger_forensic_view` | Family | Transaction list and forensic ledger density. |
| `/inventory/stock-adjustments/new` | `inventory_adjustment_draft` | Exact | Draft adjustment form. |
| `/inventory/waste-records/new` | `waste_record_draft` | Exact | Waste entry draft form. |
| `/inventory/stock-count-sessions` | `stock_count_review_draft`, `stock_count_review_posted`, `stock_count_draft_review` | Family | Draft review list and posted/read-only list states. |
| `/inventory/stock-count-sessions/new` | `stock_count_counting_state` | Exact | Counting-first creation state. |
| `/inventory/stock-count-sessions/:sessionId` | `stock_count_draft_review`, `stock_count_posted_read_only` | Family | Draft detail and posted readonly detail. |
| `/workforce/my-attendance` | `staff_action_hub_attendance_self_service`, `my_attendance_ready_to_clock_in`, `my_attendance_active_shift` | Family | Self-service check-in, active shift, scoped attendance event recording. |
| `/workforce/attendance-approvals` | `attendance_review_pending_queue`, `attendance_review_approved_record` | Family | Queue and terminal approval states. |
| `/workforce/attendance-approvals/:shiftAssignmentId` | `attendance_review_record_detail` | Exact | Approval detail investigation layout. |

## Procurement, regional ops, and organization

| Frontend Surface | Canonical Stitch Source | Mapping Mode | Required states / notes |
| --- | --- | --- | --- |
| `/procurement/suppliers` | `supplier_master_list` | Exact | Supplier listing surface. |
| `/procurement/suppliers/new` | `supplier_detail_admin` | Family | Editable supplier admin detail form. |
| `/procurement/purchase-orders` | `po_approval_submitted_regional_finance`, `po_detail_draft_outlet_manager` | Family | Queue/list surface plus draft/detail posture. |
| `/procurement/purchase-orders/new` | `create_purchase_order_draft` | Exact | Create PO draft flow. |
| `/procurement/purchase-orders/:purchaseOrderId` | `po_detail_draft_outlet_manager`, `po_detail_completed_read_only` | Family | Draft/edit and completed readonly states. |
| `/procurement/goods-receipts` | `gr_detail_received_ready_to_post` | Family | GR queue density and post-ready visual language. |
| `/procurement/goods-receipts/new` | `create_goods_receipt_draft` | Exact | Create GR draft flow. |
| `/procurement/goods-receipts/:goodsReceiptId` | `gr_detail_received_ready_to_post`, `gr_detail_posted_read_only`, `gr_detail_cancelled_read_only` | Family | Live, posted, and cancelled detail states. |
| `/procurement/supplier-invoices` | `vendor_approval_queue` | Family | Invoice review queue surface. |
| `/procurement/supplier-invoices/new` | `vendor_approval_detail` | Family | Creation/edit detail follows approval-detail framing. |
| `/procurement/supplier-invoices/:invoiceId` | `vendor_approval_detail` | Exact | Invoice detail review surface. |
| `/procurement/supplier-payments` | `supplier_payment_schedule_dashboard` | Exact | Payment schedule dashboard density and KPI framing. |
| `/procurement/supplier-payments/new` | `supplier_payment_schedule_dashboard` | Family | New payment flow inherits payment dashboard chrome. |
| `/procurement/three-way-matching` | `vendor_approval_detail` | Family | Matching review detail posture. |
| `/regional-ops` | `regional_sales_dashboard` | Exact | Dashboard-first regional oversight. |
| `/regional-ops/outlets` | `region_outlet_tree_overview` | Exact | Region-to-outlet hierarchy overview. |
| `/regional-ops/outlets/:outletId` | `outlet_detail_active_state`, `outlet_detail_closing_blocked`, `outlet_detail_closed_read_only` | Family | Active, blocked, and readonly outlet detail. |
| `/org/regions` | `region_outlet_tree_overview` | Exact | Administrative region tree and hierarchy overview. |
| `/org/regions/new` | `region_outlet_tree_overview` | Family | Create flow stays inside hierarchy-first composition. |
| `/org/regions/:regionId` | `region_outlet_tree_overview` | Exact | Region detail follows hierarchy summary framing. |
| `/org/regions/:regionId/edit` | `region_outlet_tree_overview` | Family | Edit stays within same admin family. |
| `/org/outlets` | `region_outlet_tree_overview` | Family | Outlet list inside org administration. |
| `/org/outlets/new` | `outlet_detail_active_state` | Family | Editable outlet detail. |
| `/org/outlets/:outletId` | `outlet_detail_active_state`, `outlet_detail_closing_blocked`, `outlet_detail_closed_read_only` | Family | Outlet detail lifecycle states. |
| `/org/outlets/:outletId/edit` | `outlet_detail_active_state` | Family | Editable outlet detail state. |

## Catalog, HR, and finance

| Frontend Surface | Canonical Stitch Source | Mapping Mode | Required states / notes |
| --- | --- | --- | --- |
| `/catalog/products` | `product_master_list`, `product_master_catalog_high_detail`, `product_catalog_back_office_view` | Family | Master list, high-detail, and back-office list variants. |
| `/catalog/products/new` | `create_product_draft_state`, `create_product_validation_error` | Family | Draft and validation-error creation states. |
| `/catalog/products/:productId` | `product_detail_read_only_mode`, `product_activation_blocked` | Family | Readonly and blocked activation detail states. |
| `/catalog/products/:productId/edit` | `edit_product_active_state` | Exact | Editable product detail state. |
| `/catalog/ingredients` | `ingredient_master_list_detailed_overview` | Exact | Detailed ingredient list. |
| `/catalog/ingredients/new` | `create_ingredient_draft_state_1`, `create_ingredient_draft_state_2`, `create_ingredient_validation_error_state` | Family | Multi-step draft and validation-error states. |
| `/catalog/ingredients/:ingredientId/edit` | `edit_ingredient_active_state_1`, `edit_ingredient_active_state_2` | Family | Editable ingredient states. |
| `/catalog/recipes` | `recipe_master_catalog_detailed_list`, `recipe_management_forensic_detail` | Family | Recipe list and detail/editor shell. |
| `/catalog/pricing` | `pricing_availability_matrix` | Exact | Pricing matrix layout. |
| `/catalog/availability` | `pricing_outlet_availability` | Exact | Outlet availability matrix. |
| `/catalog/promotions` | `pricing_availability_matrix` | Family | Promotion management shares pricing matrix family. |
| `/hr/employees` | `employee_directory_hr_management`, `employees_list_hr_workspace` | Family | Employee list workspace. |
| `/hr/employees/new` | `create_edit_employee_hr_portal` | Exact | Employee create portal. |
| `/hr/employees/:employeeId` | `employee_detail_hr_management`, `employee_detail_validation_security_states`, `employee_profile_hr_management` | Family | Detail, validation/security, and profile variants. |
| `/hr/contracts` | `contracts_master_list_hr_view`, `contracts_master_list_hr_repository` | Family | Contracts list and repository framing. |
| `/hr/contracts/new` | `contract_detail_lifecycle_management` | Family | Contract creation/edit detail. |
| `/hr/contracts/:contractId` | `contract_detail_active_record`, `contract_detail_lifecycle_management` | Family | Active record and lifecycle management detail. |
| `/hr/assignments/new` | `shift_assignments_outlet_scheduler` | Family | Assignment create flow follows scheduler family. |
| `/hr/attendance-summary` | `regional_attendance_summary_hr_oversight`, `regional_attendance_summary_oversight_hub` | Family | HR oversight summary. |
| `/hr/payroll-preparation` | `payroll_preparation_hr_workspace`, `payroll_preparation_draft_workflow` | Family | Workspace dashboard and draft workflow. |
| `/hr/payroll-draft-review/:runId` | `payroll_draft_review_reconciliation` | Exact | Payroll draft reconciliation review. |
| `/hr/shift-scheduling` | `shift_templates_operations_hub`, `shift_assignments_outlet_scheduler` | Family | Templates + assignment scheduler. |
| `/finance/suppliers` | `supplier_master_list` | Exact | Finance supplier list. |
| `/finance/suppliers/:supplierId` | `supplier_detail_admin`, `supplier_detail_read_only` | Family | Editable and readonly supplier detail states. |
| `/finance/payment-requests` | `payment_requests_queue_regional_finance` | Exact | Queue-first finance payment request workspace. |
| `/finance/payroll-periods` | `payroll_preparation_draft_workflow` | Family | Finance period workflow follows payroll-prep family. |
| `/finance/payroll-approvals` | `payroll_approval_workspace_detailed_review` | Exact | Approval queue workspace. |
| `/finance/payroll-approvals/:runId` | `payroll_approval_workspace_detailed_review`, `payroll_review_reconciliation_hub` | Family | Detail review and reconciliation panels. |
| `/finance/payroll-paid/:runId` | `payroll_record_paid_read_only` | Exact | Paid readonly state. |
| `/finance/supplier-invoices` | `vendor_approval_queue` | Family | Invoice queue family. |
| `/finance/supplier-invoices/new` | `vendor_approval_detail` | Family | New invoice flow aligns to approval detail family. |
| `/finance/supplier-invoices/:invoiceId` | `vendor_approval_detail` | Exact | Invoice detail review. |
| `/finance/supplier-payments` | `supplier_payment_schedule_dashboard` | Exact | Payment schedule dashboard. |
| `/finance/supplier-payments/new` | `supplier_payment_schedule_dashboard` | Family | New payment state inside payment dashboard family. |
| `/finance/exchange-rates` | `system_config_exchange_rates` | Exact | Exchange-rate configuration surface. |
| `/finance/config` | `system_config_exchange_rates` | Family | Finance config stays in system configuration family. |

## IAM, audit, and reports

| Frontend Surface | Canonical Stitch Source | Mapping Mode | Required states / notes |
| --- | --- | --- | --- |
| `/iam/users` | `user_management_system_admin_workspace` | Exact | User list and admin workspace chrome. |
| `/iam/users/:userId` | `user_detail_hr_system_scope`, `user_detail_reg_finance_region_scope`, `user_detail_staff_outlet_scope`, `user_detail_read_only_access_denied` | Family | Scope-aware user detail variants. |
| `/iam/assignments` | `system_permissions_scopes`, `create_user_wizard_step_1_identity`, `create_user_wizard_step_2_linkage_conflict`, `create_user_success_state` | Family | Assignment and permission-scope workflow states. |
| `/iam/effective-access/:userId` | `system_permissions_scopes` | Exact | Permission scope and effective access review. |
| `/audit/events` | `audit` forensic family | Inferred family | Forensic event list composition; no direct Stitch screen exists, keep shell/tokens aligned. |
| `/audit/events/:eventId` | `audit` forensic family | Inferred family | Event detail inside forensic detail family. |
| `/audit/security-events` | `audit` forensic family | Inferred family | Security queue density and alert emphasis. |
| `/audit/request-traces` | `audit` forensic family | Inferred family | Request trace list density. |
| `/audit/request-traces/:traceId` | `audit` forensic family | Inferred family | Trace detail investigation surface. |
| `/reports` | `regional_sales_dashboard` | Exact | Dashboard-first reports landing page. |
| `/reports/revenue` | `regional_sales_dashboard` | Family | Revenue report must inherit dashboard summary hierarchy. |
| `/reports/inventory` | `regional_sales_dashboard` | Family | Inventory report inherits dashboard/report summary family. |
| `/reports/payroll` | `regional_sales_dashboard` | Family | Payroll report inherits dashboard/report summary family. |
| `/reports/export-jobs` | `export_jobs_management` | Exact | Export jobs queue and management UI. |
| `/reports/export-jobs/:jobId` | `export_jobs_management` | Family | Job detail inside export management family. |
| `/reports/export-jobs/:jobId/preview` | `export_jobs_management` | Family | Preview stays inside export management shell. |
| `/reports/export-jobs/:jobId/download` | `export_jobs_management` | Family | Download confirmation state inside export management family. |
| `/reports/outlet-revenue` | `regional_sales_dashboard` | Family | Outlet revenue must not remain table-only; it follows report dashboard hierarchy. |

## Current implementation checkpoints

- Locked:
  - shared auth chrome target
  - shared app shell target
  - POS shell target
  - route-by-route canonical Stitch family mapping
- Execution rule:
  - shared shell and tokens first
  - then route family refactors
  - then parity QA against `screen.png`

## Final parity checklist

Audit date: `2026-04-03`

A family is marked `Ready for browser sign-off` when all of the following are true:
- shared Stitch foundation is applied
- scoped route family has no remaining `style={{...}}` drift in source modules
- representative route or workflow tests pass
- route family mapping above is locked and unchanged

| Family | Representative routes | Automated verification snapshot | Final status | Browser sign-off focus |
| --- | --- | --- | --- | --- |
| Auth and home | `/login`, `/session-expired`, `/unauthorized`, `/home` | `authRouting.test.tsx`, `homePagePermissions.test.tsx` | Ready for browser sign-off | Check auth loading/error variants, persona-aware action hub composition, desktop and narrow viewport shell chrome. |
| POS | `/pos`, `/pos/orders/:orderId`, `/pos/sessions`, `/pos/sessions/:sessionId` | `PosHomePage.test.tsx`, `PosOrderWorkflow.test.tsx`, `PosSessionWorkflow.test.tsx`, `PosResilience.test.tsx` | Ready for browser sign-off | Check terminal header, session state transitions, payment readonly states, and outlet-first mobile density. |
| Inventory and workforce-facing operations | `/inventory/stock-balances`, `/inventory/stock-count-sessions`, `/inventory/stock-count-sessions/:sessionId`, `/workforce/my-attendance`, `/workforce/attendance-approvals/:shiftAssignmentId` | `InventoryRoutes.test.tsx`, `MyAttendancePage.test.tsx`, `AttendanceWorkflow.test.tsx`, `WorkforcePermissions.test.tsx` | Ready for browser sign-off | Check count-session readonly posture, attendance hero state, approval detail hierarchy, and compact filter/table spacing. |
| Procurement, regional ops, and organization | `/procurement/purchase-orders`, `/procurement/purchase-orders/:purchaseOrderId`, `/procurement/goods-receipts/:goodsReceiptId`, `/regional-ops`, `/regional-ops/outlets/:outletId`, `/org/regions`, `/org/outlets/:outletId` | `PurchaseOrderListPage.test.tsx`, `PurchaseOrderWorkflow.test.tsx`, `GoodsReceiptWorkflow.test.tsx`, `SupplierPaymentListPage.test.tsx`, `RegionalDashboardPage.test.tsx`, `OutletSummaryPage.test.tsx`, `RegionalOpsRoutes.test.tsx`, `OutletsPage.test.tsx`, `RegionsPage.test.tsx`, `OutletDetailPage.test.tsx`, `RegionDetailPage.test.tsx`, `OrgRoutes.test.tsx` | Ready for browser sign-off | Check procurement KPI cards, queue/detail balance, regional dashboard hierarchy, org tree chrome, and supplier/payment dashboards. |
| Catalog, HR, and finance | `/catalog/products`, `/catalog/products/:productId`, `/catalog/ingredients`, `/catalog/availability`, `/hr/employees`, `/hr/employees/:employeeId`, `/hr/contracts`, `/hr/payroll-preparation`, `/finance/payment-requests`, `/finance/payroll-periods`, `/finance/config` | `CatalogRoutes.test.tsx`, `CatalogForms.test.tsx`, `ProductsPage.test.tsx`, `ProductDetailPage.test.tsx`, `IngredientsPage.test.tsx`, `PricingPage.test.tsx`, `RecipesPage.test.tsx`, `HrRoutes.test.tsx`, `EmployeesPage.test.tsx`, `EmployeeDetailPage.test.tsx`, `ContractsPage.test.tsx`, `AttendanceSummaryPage.test.tsx`, `PayrollPreparationPage.test.tsx`, `PayrollDraftReviewPage.test.tsx`, `FinanceRoutes.test.tsx`, `FinanceAdminPages.test.tsx`, `PaymentRequestsPage.test.tsx`, `PayrollApprovalPage.test.tsx`, `PayrollApprovalDetailPage.test.tsx`, `PayrollPaidPage.test.tsx`, `SuppliersPage.test.tsx` | Ready for browser sign-off | Check form rhythm, detail banners, payroll reconciliation density, config tabs/tables, and matrix-style catalog surfaces on desktop and tablet widths. |
| IAM, audit, and reports | `/iam/users`, `/iam/users/:userId`, `/iam/assignments`, `/iam/effective-access/:userId`, `/audit/events`, `/audit/request-traces/:traceId`, `/reports`, `/reports/outlet-revenue`, `/reports/export-jobs/:jobId` | `IamRoutes.test.tsx`, `UsersPage.test.tsx`, `UserDetailPage.test.tsx`, `AssignmentsPage.test.tsx`, `EffectiveAccessPage.test.tsx`, `AuditRoutes.test.tsx`, `AuditEventsPage.test.tsx`, `AuditEventDetailPage.test.tsx`, `SecurityEventsPage.test.tsx`, `RequestTracesPage.test.tsx`, `RequestTraceDetailPage.test.tsx`, `ReportsRoutes.test.tsx`, `ReportsDashboardPage.test.tsx`, `RevenueReportPage.test.tsx`, `InventoryReportPage.test.tsx`, `PayrollReportPage.test.tsx`, `ExportWorkflow.test.tsx` | Ready for browser sign-off | Check admin scope chips, forensic list/detail density, report dashboard hierarchy, and export center queue/detail chrome. |

## Acceptance notes

- Scoped Stitch families currently return no matches for `style={{...}}` across source modules:
  - `audit`
  - `catalog`
  - `finance`
  - `hr`
  - `iam`
  - `inventory`
  - `org`
  - `pos`
  - `procurement`
  - `regional-ops`
  - `reports`
  - `workforce`
- Remaining visual sign-off is manual browser comparison against `screen.png` for desktop first, then narrow viewport.
- If a new route or state variant is added later, update the mapping matrix first, then add at least one representative render or workflow test before calling parity restored.
