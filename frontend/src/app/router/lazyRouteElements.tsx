import { lazy } from 'react'

// ─── Lazy imports: POS ────────────────────────────────────────────────────────
export const PosHomePage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosHomePage })),
)
export const PosOrderDetailPage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosOrderDetailPage })),
)
export const PosSessionsPage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosSessionsPage })),
)
export const PosSessionDetailPage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosSessionDetailPage })),
)

// ─── Lazy imports: Procurement ────────────────────────────────────────────────
export const SupplierListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierListPage,
  })),
)
export const SupplierCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierCreatePage,
  })),
)
export const PurchaseOrderCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.PurchaseOrderCreatePage,
  })),
)
export const PurchaseOrderDetailPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.PurchaseOrderDetailPage,
  })),
)
export const GoodsReceiptCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.GoodsReceiptCreatePage,
  })),
)
export const GoodsReceiptDetailPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.GoodsReceiptDetailPage,
  })),
)
export const SupplierInvoiceCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierInvoiceCreatePage,
  })),
)
export const SupplierInvoiceDetailPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierInvoiceDetailPage,
  })),
)
export const SupplierPaymentCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierPaymentCreatePage,
  })),
)
export const PurchaseOrderListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.PurchaseOrderListPage,
  })),
)
export const GoodsReceiptListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.GoodsReceiptListPage,
  })),
)
export const SupplierInvoiceListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierInvoiceListPage,
  })),
)
export const SupplierPaymentListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierPaymentListPage,
  })),
)
export const ThreeWayMatchingPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.ThreeWayMatchingPage,
  })),
)

// ─── Lazy imports: Inventory ──────────────────────────────────────────────────
export const StockOverviewPage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({ default: m.StockOverviewPage })),
)
export const InventoryTransactionsPage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.InventoryTransactionsPage,
  })),
)
export const StockAdjustmentCreatePage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.StockAdjustmentCreatePage,
  })),
)
export const WasteRecordCreatePage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.WasteRecordCreatePage,
  })),
)
export const StockCountSessionCreatePage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.StockCountSessionCreatePage,
  })),
)
export const StockCountSessionsPage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({ default: m.StockCountSessionsPage })),
)
export const StockCountSessionDetailPage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.StockCountSessionDetailPage,
  })),
)

// ─── Lazy imports: Workforce ──────────────────────────────────────────────────
export const MyAttendancePage = lazy(() =>
  import('@modules/workforce/routes/workforceRoutes.bundle').then((m) => ({ default: m.MyAttendancePage })),
)
export const AttendanceReviewPage = lazy(() =>
  import('@modules/workforce/routes/workforceRoutes.bundle').then((m) => ({
    default: m.AttendanceReviewPage,
  })),
)
export const AttendanceDetailPage = lazy(() =>
  import('@modules/workforce/routes/workforceRoutes.bundle').then((m) => ({ default: m.AttendanceDetailPage })),
)

// ─── Lazy imports: Catalog ───────────────────────────────────────────────────
export const ProductsPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductsPage })),
)
export const ProductCreatePage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductCreatePage })),
)
export const ProductDetailPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductDetailPage })),
)
export const ProductEditPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductEditPage })),
)
export const IngredientsPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.IngredientsPage })),
)
export const IngredientCreatePage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.IngredientCreatePage })),
)
export const IngredientEditPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.IngredientEditPage })),
)
export const RecipesPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.RecipesPage })),
)
export const PricingPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.PricingPage })),
)
export const AvailabilityPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.AvailabilityPage })),
)
export const PromotionsPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.PromotionsPage })),
)

// ─── Lazy imports: IAM ───────────────────────────────────────────────────────
export const UsersPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.UsersPage })),
)
export const UserDetailPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.UserDetailPage })),
)
export const AssignmentsPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.AssignmentsPage })),
)
export const EffectiveAccessPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.EffectiveAccessPage })),
)

// ─── Lazy imports: Audit ─────────────────────────────────────────────────────
export const AuditEventsPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.AuditEventsPage })),
)
export const AuditEventDetailPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.AuditEventDetailPage })),
)
export const SecurityEventsPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.SecurityEventsPage })),
)
export const RequestTracesPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.RequestTracesPage })),
)
export const RequestTraceDetailPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.RequestTraceDetailPage })),
)

// ─── Lazy imports: Regional Ops ──────────────────────────────────────────────
export const RegionalDashboardPage = lazy(() =>
  import('@modules/regional-ops/routes/regionalOpsRoutes.bundle').then((m) => ({ default: m.RegionalDashboardPage })),
)
export const RegionalOutletSummaryPage = lazy(() =>
  import('@modules/regional-ops/routes/regionalOpsRoutes.bundle').then((m) => ({ default: m.OutletSummaryPage })),
)
export const RegionalOutletDetailPage = lazy(() =>
  import('@modules/regional-ops/routes/regionalOpsRoutes.bundle').then((m) => ({ default: m.OutletDetailPage })),
)
export const ExchangeRateManagementPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.ExchangeRateManagementPage })),
)
export const OrgRegionsPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionsPage })),
)
export const OrgRegionDetailPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionDetailPage })),
)
export const OrgRegionCreatePage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionCreatePage })),
)
export const OrgRegionEditPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionEditPage })),
)
export const OrgOutletsPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletsPage })),
)
export const OrgOutletDetailPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletDetailPage })),
)
export const OrgOutletCreatePage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletCreatePage })),
)
export const OrgOutletEditPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletEditPage })),
)

// ─── Lazy imports: HR ────────────────────────────────────────────────────────
export const EmployeesPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.EmployeesPage })),
)
export const EmployeeCreatePage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.EmployeeCreatePage })),
)
export const EmployeeDetailPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.EmployeeDetailPage })),
)
export const ContractsPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ContractsPage })),
)
export const ContractCreatePage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ContractCreatePage })),
)
export const ContractDetailPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ContractDetailPage })),
)
export const AssignmentCreatePage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.AssignmentCreatePage })),
)
export const AttendanceSummaryPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.AttendanceSummaryPage })),
)
export const PayrollPreparationPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.PayrollPreparationPage })),
)
export const PayrollDraftReviewPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.PayrollDraftReviewPage })),
)
export const ShiftSchedulingPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ShiftSchedulingPage })),
)

// ─── Lazy imports: Finance ───────────────────────────────────────────────────
export const SuppliersPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.SuppliersPage })),
)
export const SupplierDetailPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.SupplierDetailPage })),
)
export const PaymentRequestsPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PaymentRequestsPage })),
)
export const PayrollApprovalPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollApprovalPage })),
)
export const PayrollApprovalDetailPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollApprovalDetailPage })),
)
export const PayrollPaidPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollPaidPage })),
)
export const FinanceConfigPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.FinanceConfigPage })),
)
export const PayrollPeriodsPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollPeriodsPage })),
)

// ─── Lazy imports: Reports ────────────────────────────────────────────────────
export const ReportsDashboardPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ReportsDashboardPage })),
)
export const RevenueReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.RevenueReportPage })),
)
export const InventoryReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.InventoryReportPage })),
)
export const PayrollReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.PayrollReportPage })),
)
export const ExportJobsPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportJobsPage })),
)
export const ExportJobDetailPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportJobDetailPage })),
)
export const ExportPreviewPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportPreviewPage })),
)
export const ExportDownloadPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportDownloadPage })),
)
export const OutletRevenueReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.OutletRevenueReportPage })),
)
