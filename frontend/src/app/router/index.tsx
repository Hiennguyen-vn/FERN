import type { ReactNode } from 'react'
import { lazy, useState } from 'react'
import { createBrowserRouter, Link, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AppShell } from '@app/layouts/AppShell'
import { AuthLayout } from '@app/layouts/AuthLayout'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { EmptyLayout } from '@app/layouts/EmptyLayout'
import { PosLayout } from '@app/layouts/PosLayout'
import { RequireAuth } from '@app/guards/RequireAuth'
import { RequireOutletContext } from '@app/guards/RequireOutletContext'
import { RequirePermission } from '@app/guards/RequirePermission'
import { RequireAnyPermission } from '@app/guards/RequireAnyPermission'
import { login } from '@core/auth/auth.service'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { permissionConstants } from '@core/permissions/permission.constants'
import { LazyRouteBoundary } from './LazyRouteBoundary'
import { useAuthStore } from '@core/auth/auth.store'
import { Button, Card, Input, ReadonlyBanner } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { buildNavigation } from '@shared/navigation/navigation.builder'
import {
  resolveAuditLandingPath,
  resolveCatalogLandingPath,
  resolveFinanceLandingPath,
  resolveHrLandingPath,
  resolveIamLandingPath,
  resolveInventoryLandingPath,
  resolveOrgLandingPath,
  resolveReportsLandingPath,
  resolveWorkforceLandingPath,
} from './moduleLanding.service'
import {
  canReadPurchaseOrders,
  canReadGoodsReceipts,
} from '@modules/procurement/services/procurementPermission.service'

// ─── Lazy imports: POS ────────────────────────────────────────────────────────
const PosHomePage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosHomePage })),
)
const PosOrderDetailPage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosOrderDetailPage })),
)
const PosSessionsPage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosSessionsPage })),
)
const PosSessionDetailPage = lazy(() =>
  import('@modules/pos/routes/posRoutes.bundle').then((m) => ({ default: m.PosSessionDetailPage })),
)

// ─── Lazy imports: Procurement ────────────────────────────────────────────────
const PurchaseOrderCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.PurchaseOrderCreatePage,
  })),
)
const PurchaseOrderDetailPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.PurchaseOrderDetailPage,
  })),
)
const GoodsReceiptCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.GoodsReceiptCreatePage,
  })),
)
const GoodsReceiptDetailPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.GoodsReceiptDetailPage,
  })),
)
const SupplierInvoiceCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierInvoiceCreatePage,
  })),
)
const SupplierInvoiceDetailPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierInvoiceDetailPage,
  })),
)
const SupplierPaymentCreatePage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierPaymentCreatePage,
  })),
)
const PurchaseOrderListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.PurchaseOrderListPage,
  })),
)
const GoodsReceiptListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.GoodsReceiptListPage,
  })),
)
const SupplierInvoiceListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierInvoiceListPage,
  })),
)
const SupplierPaymentListPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.SupplierPaymentListPage,
  })),
)
const ThreeWayMatchingPage = lazy(() =>
  import('@modules/procurement/routes/procurementRoutes.bundle').then((m) => ({
    default: m.ThreeWayMatchingPage,
  })),
)

// ─── Lazy imports: Inventory ──────────────────────────────────────────────────
const StockOverviewPage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({ default: m.StockOverviewPage })),
)
const InventoryTransactionsPage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.InventoryTransactionsPage,
  })),
)
const StockAdjustmentCreatePage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.StockAdjustmentCreatePage,
  })),
)
const WasteRecordCreatePage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.WasteRecordCreatePage,
  })),
)
const StockCountSessionCreatePage = lazy(() =>
  import('@modules/inventory/routes/inventoryRoutes.bundle').then((m) => ({
    default: m.StockCountSessionCreatePage,
  })),
)

// ─── Lazy imports: Workforce ──────────────────────────────────────────────────
const MyAttendancePage = lazy(() =>
  import('@modules/workforce/routes/workforceRoutes.bundle').then((m) => ({ default: m.MyAttendancePage })),
)
const AttendanceReviewPage = lazy(() =>
  import('@modules/workforce/routes/workforceRoutes.bundle').then((m) => ({
    default: m.AttendanceReviewPage,
  })),
)
const AttendanceDetailPage = lazy(() =>
  import('@modules/workforce/routes/workforceRoutes.bundle').then((m) => ({ default: m.AttendanceDetailPage })),
)

// ─── Lazy imports: Catalog ───────────────────────────────────────────────────
const ProductsPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductsPage })),
)
const ProductCreatePage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductCreatePage })),
)
const ProductDetailPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductDetailPage })),
)
const ProductEditPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.ProductEditPage })),
)
const IngredientsPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.IngredientsPage })),
)
const IngredientCreatePage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.IngredientCreatePage })),
)
const IngredientEditPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.IngredientEditPage })),
)
const RecipesPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.RecipesPage })),
)
const PricingPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.PricingPage })),
)
const AvailabilityPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.AvailabilityPage })),
)
const PromotionsPage = lazy(() =>
  import('@modules/catalog/routes/catalogRoutes.bundle').then((m) => ({ default: m.PromotionsPage })),
)

// ─── Lazy imports: IAM ───────────────────────────────────────────────────────
const UsersPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.UsersPage })),
)
const UserDetailPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.UserDetailPage })),
)
const AssignmentsPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.AssignmentsPage })),
)
const EffectiveAccessPage = lazy(() =>
  import('@modules/iam/routes/iamRoutes.bundle').then((m) => ({ default: m.EffectiveAccessPage })),
)

// ─── Lazy imports: Audit ─────────────────────────────────────────────────────
const AuditEventsPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.AuditEventsPage })),
)
const AuditEventDetailPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.AuditEventDetailPage })),
)
const SecurityEventsPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.SecurityEventsPage })),
)
const RequestTracesPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.RequestTracesPage })),
)
const RequestTraceDetailPage = lazy(() =>
  import('@modules/audit/routes/auditRoutes.bundle').then((m) => ({ default: m.RequestTraceDetailPage })),
)

// ─── Lazy imports: Regional Ops ──────────────────────────────────────────────
const RegionalDashboardPage = lazy(() =>
  import('@modules/regional-ops/routes/regionalOpsRoutes.bundle').then((m) => ({ default: m.RegionalDashboardPage })),
)
const RegionalOutletSummaryPage = lazy(() =>
  import('@modules/regional-ops/routes/regionalOpsRoutes.bundle').then((m) => ({ default: m.OutletSummaryPage })),
)
const RegionalOutletDetailPage = lazy(() =>
  import('@modules/regional-ops/routes/regionalOpsRoutes.bundle').then((m) => ({ default: m.OutletDetailPage })),
)
const ExchangeRateManagementPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.ExchangeRateManagementPage })),
)
const OrgRegionsPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionsPage })),
)
const OrgRegionDetailPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionDetailPage })),
)
const OrgRegionCreatePage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionCreatePage })),
)
const OrgRegionEditPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.RegionEditPage })),
)
const OrgOutletsPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletsPage })),
)
const OrgOutletDetailPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletDetailPage })),
)
const OrgOutletCreatePage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletCreatePage })),
)
const OrgOutletEditPage = lazy(() =>
  import('@modules/org/routes/orgRoutes.bundle').then((m) => ({ default: m.OutletEditPage })),
)

// ─── Lazy imports: HR ────────────────────────────────────────────────────────
const EmployeesPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.EmployeesPage })),
)
const EmployeeCreatePage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.EmployeeCreatePage })),
)
const EmployeeDetailPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.EmployeeDetailPage })),
)
const ContractsPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ContractsPage })),
)
const ContractCreatePage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ContractCreatePage })),
)
const ContractDetailPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ContractDetailPage })),
)
const AssignmentCreatePage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.AssignmentCreatePage })),
)
const AttendanceSummaryPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.AttendanceSummaryPage })),
)
const PayrollPreparationPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.PayrollPreparationPage })),
)
const PayrollDraftReviewPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.PayrollDraftReviewPage })),
)
const ShiftSchedulingPage = lazy(() =>
  import('@modules/hr/routes/hrRoutes.bundle').then((m) => ({ default: m.ShiftSchedulingPage })),
)

// ─── Lazy imports: Finance ───────────────────────────────────────────────────
const SuppliersPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.SuppliersPage })),
)
const SupplierDetailPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.SupplierDetailPage })),
)
const PaymentRequestsPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PaymentRequestsPage })),
)
const PayrollApprovalPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollApprovalPage })),
)
const PayrollApprovalDetailPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollApprovalDetailPage })),
)
const PayrollPaidPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollPaidPage })),
)
const FinanceConfigPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.FinanceConfigPage })),
)
const PayrollPeriodsPage = lazy(() =>
  import('@modules/finance/routes/financeRoutes.bundle').then((m) => ({ default: m.PayrollPeriodsPage })),
)

// ─── Lazy imports: Reports ────────────────────────────────────────────────────
const ReportsDashboardPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ReportsDashboardPage })),
)
const RevenueReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.RevenueReportPage })),
)
const InventoryReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.InventoryReportPage })),
)
const PayrollReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.PayrollReportPage })),
)
const ExportJobsPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportJobsPage })),
)
const ExportJobDetailPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportJobDetailPage })),
)
const ExportPreviewPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportPreviewPage })),
)
const ExportDownloadPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.ExportDownloadPage })),
)
const OutletRevenueReportPage = lazy(() =>
  import('@modules/reports/routes/reportsRoutes.bundle').then((m) => ({ default: m.OutletRevenueReportPage })),
)

// ─── Helpers ──────────────────────────────────────────────────────────────────
function AuthPageFrame({ children }: { children: ReactNode }) {
  return (
    <EmptyLayout>
      <AuthLayout>{children}</AuthLayout>
    </EmptyLayout>
  )
}

// Redirects /procurement to the appropriate landing page based on permissions.
function ProcurementLandingRedirect() {
  const principal = useAuthStore((state) => state.principal)
  if (canReadPurchaseOrders(principal)) {
    return <Navigate replace to="purchase-orders" />
  }
  if (canReadGoodsReceipts(principal)) {
    return <Navigate replace to="goods-receipts" />
  }
  // Fallback: let the module guard handle the redirect to /unauthorized.
  return <Navigate replace to="/unauthorized" />
}

function ModuleLandingRedirect({
  resolve,
}: {
  resolve: (principal: ReturnType<typeof useAuthStore.getState>['principal']) => string | null
}) {
  const principal = useAuthStore((state) => state.principal)
  const path = resolve(principal)
  return path ? <Navigate replace to={path} /> : <Navigate replace to="/unauthorized" />
}

function ReportsModuleIndex() {
  const principal = useAuthStore((state) => state.principal)
  const path = resolveReportsLandingPath(principal)
  return path ? <Navigate replace to={path} /> : <ReportsDashboardPage />
}

// ─── Auth pages (inline — small, always needed) ───────────────────────────────
function LoginPage() {
  usePageTitle('Login')
  const navigate = useNavigate()
  const location = useLocation()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const [username, setUsername] = useState(import.meta.env.DEV ? 'bootstrap-admin' : '')
  const [password, setPassword] = useState(import.meta.env.DEV ? 'Admin123!' : '')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (isAuthenticated) {
    return <Navigate replace to="/home" />
  }

  return (
    <AuthPageFrame>
      <div className="page-stack">
        <div>
          <p className="eyebrow">FERN Platform</p>
          <h1>Đăng nhập</h1>
          <p className="muted-text">Quản lý chuỗi F&B đa chi nhánh.</p>
        </div>
        <form
          className="page-stack"
          onSubmit={async (event) => {
            event.preventDefault()
            setSubmitting(true)
            setError(null)
            try {
              await login({ username, password })
              const nextPath = (location.state as { from?: string } | null)?.from ?? '/home'
              navigate(nextPath, { replace: true })
            } catch (loginError) {
              setError(loginError instanceof Error ? loginError.message : 'Login failed')
            } finally {
              setSubmitting(false)
            }
          }}
        >
          <Input
            autoComplete="username"
            label="Username"
            onChange={(event) => setUsername(event.target.value)}
            placeholder="bootstrap-admin"
            value={username}
          />
          <Input
            autoComplete="current-password"
            label="Password"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="••••••••"
            type="password"
            value={password}
          />
          {error ? <p className="error-text">{error}</p> : null}
          <Button loading={submitting} type="submit">
            Sign in
          </Button>
        </form>
      </div>
    </AuthPageFrame>
  )
}

function SessionExpiredPage() {
  usePageTitle('Session Expired')
  return (
    <AuthPageFrame>
      <div className="page-stack">
        <div>
          <p className="eyebrow">Security</p>
          <h1>Session expired</h1>
          <p className="muted-text">Refresh token failed or access token expired.</p>
        </div>
        <Button asChild>
          <Link to="/login">Back to login</Link>
        </Button>
      </div>
    </AuthPageFrame>
  )
}

function UnauthorizedPage() {
  usePageTitle('Unauthorized')
  return (
    <AuthPageFrame>
      <div className="page-stack">
        <div>
          <p className="eyebrow">Access denied</p>
          <h1>Không có quyền truy cập</h1>
          <p className="muted-text">Bạn không có permission cho trang này.</p>
        </div>
        <Button asChild variant="secondary">
          <Link to="/home">Go to home</Link>
        </Button>
      </div>
    </AuthPageFrame>
  )
}

export function HomePage() {
  usePageTitle('Home — FERN Platform')
  const principal = useAuthStore((state) => state.principal)
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const visibleNavigation = buildNavigation(principal)
  const visibleModulePaths = new Set(visibleNavigation.map((item) => item.to))
  const canSeeIam = visibleModulePaths.has('/iam')
  const canSeeAudit = visibleModulePaths.has('/audit')
  const canSeeRegionalOps = visibleModulePaths.has('/regional-ops')
  const canSeeHr = visibleModulePaths.has('/hr')
  const canSeeFinance = visibleModulePaths.has('/finance')
  const modules = [
    { to: '/pos', title: 'POS', description: 'Bán hàng, thu tiền, quản lý session.', status: 'live' as const },
    { to: '/catalog', title: 'Catalog', description: 'Sản phẩm, công thức, bảng giá.', status: 'live' as const },
    { to: '/iam', title: 'IAM', description: 'Users, assignments, effective access.', status: 'live' as const },
    { to: '/audit', title: 'Audit', description: 'Audit events, security events, request traces.', status: 'live' as const },
    { to: '/regional-ops', title: 'Regional Ops', description: 'Regional dashboard, outlet scan và drill-down oversight.', status: 'live' as const },
    { to: '/hr', title: 'HR', description: 'Nhân viên, hợp đồng, chấm công tổng hợp, payroll prep.', status: 'live' as const },
    { to: '/finance', title: 'Finance', description: 'Nhà cung cấp, payment requests, payroll approvals, mark paid.', status: 'live' as const },
    { to: '/procurement', title: 'Procurement', description: 'Đặt hàng nhà cung cấp, nhận hàng.', status: 'live' as const },
    { to: '/inventory', title: 'Inventory', description: 'Tồn kho, giao dịch kho.', status: 'live' as const },
    { to: '/workforce', title: 'Workforce', description: 'Chấm công, duyệt ca.', status: 'live' as const },
    { to: '/reports', title: 'Reports', description: 'Dashboard, revenue, inventory, payroll và export jobs.', status: 'live' as const },
  ].filter((module) => visibleModulePaths.has(module.to))
  const quickActions = [
    {
      to: '/pos',
      title: 'Open POS workspace',
      description: 'Bắt đầu ca bán hàng, cart và payment flow.',
    },
    {
      to: '/procurement',
      title: 'Create purchase order',
      description: 'Tạo PO mới theo outlet hiện tại.',
    },
    {
      to: '/inventory',
      title: 'Check stock balances',
      description: 'Kiểm tra tồn kho trước khi mua hàng hoặc điều phối.',
    },
    {
      to: '/workforce',
      title: 'Review attendance',
      description: 'Mở queue phê duyệt chấm công.',
    },
    {
      to: '/reports',
      title: 'Open reports center',
      description: 'Mở dashboard báo cáo, report filters và export jobs.',
    },
    {
      to: '/audit/events',
      title: 'Investigate audit trail',
      description: 'Mở audit events và request traces để điều tra correlation, scope và actions.',
    },
    {
      to: '/regional-ops/outlets',
      title: 'Review regional outlets',
      description: 'Mở summary các outlet trong region hiện tại để scan status và contact readiness.',
    },
  ].filter((action) => {
    if (action.to.startsWith('/pos')) {
      return visibleModulePaths.has('/pos')
    }
    if (action.to.startsWith('/procurement')) {
      return visibleModulePaths.has('/procurement')
    }
    if (action.to.startsWith('/inventory')) {
      return visibleModulePaths.has('/inventory')
    }
    if (action.to.startsWith('/workforce')) {
      return visibleModulePaths.has('/workforce')
    }
    if (action.to.startsWith('/reports')) {
      return visibleModulePaths.has('/reports')
    }
    if (action.to.startsWith('/audit')) {
      return visibleModulePaths.has('/audit')
    }
    if (action.to.startsWith('/regional-ops')) {
      return visibleModulePaths.has('/regional-ops')
    }
    if (action.to.startsWith('/finance')) {
      return visibleModulePaths.has('/finance')
    }

    return true
  })
  if (canSeeHr) {
    quickActions.push({
      to: '/hr/payroll-preparation',
      title: 'Prepare payroll draft',
      description: 'Tạo payroll period và draft run cho region hiện tại.',
    })
  }
  if (canSeeIam) {
    quickActions.push({
      to: '/iam',
      title: 'Open IAM console',
      description: 'Kiểm tra user assignments và effective access.',
    })
  }
  if (canSeeAudit) {
    quickActions.push({
      to: '/audit/request-traces',
      title: 'Inspect request traces',
      description: 'Đi từ correlation ID sang trace detail để điều tra request flow và security incidents.',
    })
  }
  if (canSeeRegionalOps) {
    quickActions.push({
      to: '/regional-ops',
      title: 'Open regional command center',
      description: 'Xem overview signal của region hiện tại và drill-down vào outlet summary.',
    })
  }
  if (canSeeFinance) {
    quickActions.push(
      {
        to: '/finance',
        title: 'Review payroll approvals',
        description: 'Mở approval queue để kiểm tra payroll runs đang chờ Finance decision.',
      },
      {
        to: '/finance/suppliers',
        title: 'Browse suppliers',
        description: 'Kiểm tra supplier master và payment request context.',
      },
      {
        to: '/finance/payroll-periods',
        title: 'Manage payroll periods',
        description: 'Tạo và quản lý các kỳ lương.',
      },
    )
  }

  return (
    <DashboardLayout
      title="FERN Platform"
      description="Quản lý chuỗi F&B đa chi nhánh — chọn module để bắt đầu."
    >
      {!selectedRegionId || !selectedOutletId ? (
        <ReadonlyBanner message="Chọn region và outlet ở app shell để các module vận hành dùng đúng scope ngay từ đầu." />
      ) : (
        <Card className="dashboard-context-card" title="Current operating context">
          <div className="meta-grid">
            <span>Region: #{selectedRegionId}</span>
            <span>Outlet: #{selectedOutletId}</span>
            <span>Mode: Operational</span>
          </div>
        </Card>
      )}

      <div className="card-grid dashboard-actions-grid">
        {quickActions.map((action) => (
          <Card className="dashboard-action-card" key={action.to} title={action.title}>
            <p className="muted-text">{action.description}</p>
            <Button asChild size="sm">
              <Link to={action.to}>Open</Link>
            </Button>
          </Card>
        ))}
      </div>

      <div className="card-grid dashboard-module-grid">
        {modules.map((mod) => (
          <Card className="dashboard-module-card" key={mod.to} title={mod.title}>
            <p className="muted-text">{mod.description}</p>
            {mod.status === 'live' ? (
              <Button asChild size="sm" variant="secondary">
                <Link to={mod.to}>Mở</Link>
              </Button>
            ) : (
              <span className="badge badge-neutral">Sắp ra mắt</span>
            )}
          </Card>
        ))}
      </div>
    </DashboardLayout>
  )
}

// ─── Shells ───────────────────────────────────────────────────────────────────
function ProtectedShell() {
  return (
    <RequireAuth>
      <AppShell />
    </RequireAuth>
  )
}

function PosShell() {
  return (
    <RequireOutletContext>
      <PosLayout title="POS Workspace">
        <Outlet />
      </PosLayout>
    </RequireOutletContext>
  )
}

// ─── Router ───────────────────────────────────────────────────────────────────
export const router = createBrowserRouter([
  { path: '/', element: <Navigate replace to="/home" /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/session-expired', element: <SessionExpiredPage /> },
  { path: '/unauthorized', element: <UnauthorizedPage /> },
  {
    path: '/',
    element: <ProtectedShell />,
    children: [
      // ── Home ──────────────────────────────────────────────
      { path: 'home', element: <HomePage /> },

      // ── Catalog ───────────────────────────────────────────────
      // Guard accepts any of the 4 catalog read permissions, matching the
      // canSeeCatalogNavigation policy which allows users with only ingredientRead,
      // recipeRead, or priceRead to access the module.
      {
        path: 'catalog',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.catalog.productRead,
              permissionConstants.catalog.productWrite,
              permissionConstants.catalog.ingredientRead,
              permissionConstants.catalog.ingredientWrite,
              permissionConstants.catalog.recipeRead,
              permissionConstants.catalog.recipeWrite,
              permissionConstants.catalog.priceRead,
              permissionConstants.catalog.priceWrite,
              permissionConstants.catalog.promotionRead,
              permissionConstants.catalog.promotionWrite,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Catalog" label="Loading catalog" />,
            children: [
          {
            index: true,
            element: <ModuleLandingRedirect resolve={resolveCatalogLandingPath} />,
          },
          {
            path: 'products',
            element: <ProductsPage />,
          },
          {
            path: 'products/new',
            element: <ProductCreatePage />,
          },
          {
            path: 'products/:productId',
            element: <ProductDetailPage />,
          },
          {
            path: 'products/:productId/edit',
            element: <ProductEditPage />,
          },
          {
            path: 'ingredients',
            element: <IngredientsPage />,
          },
          {
            path: 'ingredients/new',
            element: <IngredientCreatePage />,
          },
          {
            path: 'ingredients/:ingredientId/edit',
            element: <IngredientEditPage />,
          },
          {
            path: 'recipes',
            element: <RecipesPage />,
          },
          {
            path: 'pricing',
            element: <PricingPage />,
          },
          {
            path: 'availability',
            element: <AvailabilityPage />,
          },
          {
            path: 'promotions',
            element: <PromotionsPage />,
          },
            ],
          },
        ],
      },

      // ── IAM ───────────────────────────────────────────────
      // Guard accepts any of the 4 IAM read permissions, matching the
      // canSeeIamNavigation policy which allows users with roleRead,
      // permissionRead, or permissionOverrideRead alone.
      {
        path: 'iam',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.iam.userRead,
              permissionConstants.iam.roleRead,
              permissionConstants.iam.permissionRead,
              permissionConstants.iam.permissionOverrideRead,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="IAM" label="Loading IAM console" />,
            children: [
          {
            index: true,
            element: <ModuleLandingRedirect resolve={resolveIamLandingPath} />,
          },
          {
            path: 'users',
            element: <UsersPage />,
          },
          {
            path: 'users/:userId',
            element: <UserDetailPage />,
          },
          {
            path: 'assignments',
            element: <AssignmentsPage />,
          },
          {
            path: 'effective-access/:userId',
            element: <EffectiveAccessPage />,
          },
            ],
          },
        ],
      },

      // ── Audit ────────────────────────────────────────────────
      {
        path: 'audit',
        element: <RequirePermission permissions={[permissionConstants.audit.read]} />,
        children: [
          {
            element: <LazyRouteBoundary moduleName="Audit" label="Loading audit console" />,
            children: [
          {
            index: true,
            element: <ModuleLandingRedirect resolve={resolveAuditLandingPath} />,
          },
          {
            path: 'events',
            element: <AuditEventsPage />,
          },
          {
            path: 'events/:eventId',
            element: <AuditEventDetailPage />,
          },
          {
            path: 'security-events',
            element: <SecurityEventsPage />,
          },
          {
            path: 'request-traces',
            element: <RequestTracesPage />,
          },
          {
            path: 'request-traces/:traceId',
            element: <RequestTraceDetailPage />,
          },
            ],
          },
        ],
      },

      // ── Org (admin management) ────────────────────────────
      {
        path: 'org',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.org.regionRead,
              permissionConstants.org.outletRead,
              permissionConstants.org.regionWrite,
              permissionConstants.org.outletWrite,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Org" label="Loading org management" />,
            children: [
              {
                index: true,
                element: <ModuleLandingRedirect resolve={resolveOrgLandingPath} />,
              },
              {
                path: 'regions',
                element: <OrgRegionsPage />,
              },
              {
                path: 'regions/new',
                element: <OrgRegionCreatePage />,
              },
              {
                path: 'regions/:regionId',
                element: <OrgRegionDetailPage />,
              },
              {
                path: 'regions/:regionId/edit',
                element: <OrgRegionEditPage />,
              },
              {
                path: 'outlets',
                element: <OrgOutletsPage />,
              },
              {
                path: 'outlets/new',
                element: <OrgOutletCreatePage />,
              },
              {
                path: 'outlets/:outletId',
                element: <OrgOutletDetailPage />,
              },
              {
                path: 'outlets/:outletId/edit',
                element: <OrgOutletEditPage />,
              },
            ],
          },
        ],
      },

      // ── Regional Ops ────────────────────────────────────────
      {
        path: 'regional-ops',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.org.regionRead,
              permissionConstants.org.outletRead,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Regional Ops" label="Loading regional ops workspace" />,
            children: [
          {
            index: true,
            element: <RegionalDashboardPage />,
          },
          {
            path: 'outlets',
            element: <RegionalOutletSummaryPage />,
          },
          {
            path: 'outlets/:outletId',
            element: <RegionalOutletDetailPage />,
          },
            ],
          },
        ],
      },

      // ── HR ────────────────────────────────────────────────
      {
        path: 'hr',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.hr.employeeRead,
              permissionConstants.hr.employeeWrite,
              permissionConstants.hr.contractRead,
              permissionConstants.hr.contractWrite,
              permissionConstants.hr.shiftRead,
              permissionConstants.hr.shiftWrite,
              permissionConstants.hr.payrollPrepare,
              permissionConstants.hr.attendanceReview,
              permissionConstants.finance.payrollRead,
              permissionConstants.finance.payrollPrepare,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="HR" label="Loading HR workspace" />,
            children: [
          {
            index: true,
            element: <ModuleLandingRedirect resolve={resolveHrLandingPath} />,
          },
          {
            path: 'employees',
            element: <EmployeesPage />,
          },
          {
            path: 'employees/new',
            element: <EmployeeCreatePage />,
          },
          {
            path: 'employees/:employeeId',
            element: <EmployeeDetailPage />,
          },
          {
            path: 'contracts',
            element: <ContractsPage />,
          },
          {
            path: 'contracts/new',
            element: <ContractCreatePage />,
          },
          {
            path: 'contracts/:contractId',
            element: <ContractDetailPage />,
          },
          {
            path: 'assignments/new',
            element: <AssignmentCreatePage />,
          },
          {
            path: 'attendance-summary',
            element: <AttendanceSummaryPage />,
          },
          {
            path: 'payroll-preparation',
            element: <PayrollPreparationPage />,
          },
          {
            path: 'payroll-draft-review/:runId',
            element: <PayrollDraftReviewPage />,
          },
          {
            path: 'shift-scheduling',
            element: <ShiftSchedulingPage />,
          },
            ],
          },
        ],
      },

      // ── Finance ───────────────────────────────────────────
      // Guard accepts any of the finance navigation permissions, matching the
      // canSeeFinanceNavigation policy. This allows users with supplier, invoice,
      // or payment permissions (not just payroll) to access the module.
      {
        path: 'finance',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.procurement.supplierRead,
              permissionConstants.procurement.invoiceRead,
              permissionConstants.procurement.invoiceReview,
              permissionConstants.procurement.invoiceApprove,
              permissionConstants.procurement.invoiceDispute,
              permissionConstants.procurement.paymentRead,
              permissionConstants.procurement.paymentRecord,
              permissionConstants.procurement.supplierWrite,
              permissionConstants.finance.payrollRead,
              permissionConstants.finance.payrollPrepare,
              permissionConstants.finance.payrollApprove,
              permissionConstants.finance.payrollPay,
              permissionConstants.finance.configRead,
              permissionConstants.finance.configWrite,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Finance" label="Loading finance workspace" />,
            children: [
          {
            index: true,
            element: <ModuleLandingRedirect resolve={resolveFinanceLandingPath} />,
          },
          {
            path: 'suppliers',
            element: <SuppliersPage />,
          },
          {
            path: 'suppliers/:supplierId',
            element: <SupplierDetailPage />,
          },
          {
            path: 'payment-requests',
            element: <PaymentRequestsPage />,
          },
          {
            path: 'payroll-periods',
            element: <PayrollPeriodsPage />,
          },
          {
            path: 'payroll-approvals',
            element: <PayrollApprovalPage />,
          },
          {
            path: 'payroll-approvals/:runId',
            element: <PayrollApprovalDetailPage />,
          },
          {
            path: 'payroll-paid/:runId',
            element: <PayrollPaidPage />,
          },
          {
            path: 'supplier-invoices',
            element: <SupplierInvoiceListPage />,
          },
          {
            path: 'supplier-invoices/new',
            element: <SupplierInvoiceCreatePage />,
          },
          {
            path: 'supplier-invoices/:invoiceId',
            element: <SupplierInvoiceDetailPage />,
          },
          {
            path: 'supplier-payments',
            element: <SupplierPaymentListPage />,
          },
          {
            path: 'supplier-payments/new',
            element: <SupplierPaymentCreatePage />,
          },
          {
            path: 'exchange-rates',
            element: <ExchangeRateManagementPage />,
          },
          {
            path: 'config',
            element: <FinanceConfigPage />,
          },
            ],
          },
        ],
      },

      // ── POS ───────────────────────────────────────────────
      {
        path: 'pos',
        element: <RequirePermission permissions={[permissionConstants.pos.sessionRead]} />,
        children: [
          {
            element: <PosShell />,
            children: [
              {
                element: <LazyRouteBoundary moduleName="POS" label="Loading POS workspace" />,
                children: [
                  {
                    index: true,
                    element: <PosHomePage />,
                  },
                  {
                    path: 'orders/:orderId',
                    element: <PosOrderDetailPage />,
                  },
                  {
                    path: 'sessions',
                    element: <PosSessionsPage />,
                  },
                  {
                    path: 'sessions/:sessionId',
                    element: <PosSessionDetailPage />,
                  },
                ],
              },
            ],
          },
        ],
      },

      // ── Procurement ───────────────────────────────────────
      // Guard accepts any procurement read/write permission.
      // Individual pages apply stricter action checks (e.g. po.create, gr.create).
      {
        path: 'procurement',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.procurement.purchaseOrderRead,
              permissionConstants.procurement.purchaseOrderCreate,
              permissionConstants.procurement.goodsReceiptRead,
              permissionConstants.procurement.goodsReceiptCreate,
              permissionConstants.procurement.invoiceRead,
              permissionConstants.procurement.invoiceReview,
              permissionConstants.procurement.invoiceApprove,
              permissionConstants.procurement.paymentRead,
              permissionConstants.procurement.paymentRecord,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Procurement" label="Loading procurement" />,
            children: [
          {
            index: true,
            element: <ProcurementLandingRedirect />,
          },
          {
            path: 'purchase-orders',
            element: <PurchaseOrderListPage />,
          },
          {
            path: 'purchase-orders/new',
            element: <PurchaseOrderCreatePage />,
          },
          {
            path: 'purchase-orders/:purchaseOrderId',
            element: <PurchaseOrderDetailPage />,
          },
          {
            path: 'goods-receipts',
            element: <GoodsReceiptListPage />,
          },
          {
            path: 'goods-receipts/new',
            element: <GoodsReceiptCreatePage />,
          },
          {
            path: 'goods-receipts/:goodsReceiptId',
            element: <GoodsReceiptDetailPage />,
          },
          {
            path: 'supplier-invoices',
            element: <SupplierInvoiceListPage />,
          },
          {
            path: 'supplier-invoices/new',
            element: <SupplierInvoiceCreatePage />,
          },
          {
            path: 'supplier-invoices/:invoiceId',
            element: <SupplierInvoiceDetailPage />,
          },
          {
            path: 'supplier-payments',
            element: <SupplierPaymentListPage />,
          },
          {
            path: 'supplier-payments/new',
            element: <SupplierPaymentCreatePage />,
          },
          {
            path: 'three-way-matching',
            element: <ThreeWayMatchingPage />,
          },
            ],
          },
        ],
      },

      // ── Inventory ─────────────────────────────────────────
      {
        path: 'inventory',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.inventory.balanceRead,
              permissionConstants.inventory.ledgerRead,
              permissionConstants.inventory.adjustmentWrite,
              permissionConstants.inventory.wasteWrite,
              permissionConstants.inventory.stockCountWrite,
              permissionConstants.inventory.stockCountPost,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Inventory" label="Loading inventory" />,
            children: [
              {
                index: true,
                element: <ModuleLandingRedirect resolve={resolveInventoryLandingPath} />,
              },
              {
                path: 'stock-balances',
                element: <StockOverviewPage />,
              },
              {
                path: 'transactions',
                element: <InventoryTransactionsPage />,
              },
              {
                path: 'stock-adjustments/new',
                element: <StockAdjustmentCreatePage />,
              },
              {
                path: 'waste-records/new',
                element: <WasteRecordCreatePage />,
              },
              {
                path: 'stock-count-sessions/new',
                element: <StockCountSessionCreatePage />,
              },
            ],
          },
        ],
      },

      // ── Workforce ─────────────────────────────────────────
      // Guard accepts attendance.write (clock-in/out staff) OR attendance.review
      // (supervisors reviewing approvals). Individual pages enforce stricter
      // action-level checks via canRecordAttendance / canApproveAttendance.
      {
        path: 'workforce',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.hr.attendanceWrite,
              permissionConstants.hr.attendanceReview,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Workforce" label="Loading workforce" />,
            children: [
              {
                index: true,
                element: <ModuleLandingRedirect resolve={resolveWorkforceLandingPath} />,
              },
              {
                path: 'my-attendance',
                element: <MyAttendancePage />,
              },
              {
                path: 'attendance-approvals',
                element: <AttendanceReviewPage />,
              },
              {
                path: 'attendance-approvals/:shiftAssignmentId',
                element: <AttendanceDetailPage />,
              },
            ],
          },
        ],
      },

      // ── Reports ───────────────────────────────────────────
      {
        path: 'reports',
        element: (
          <RequireAnyPermission
            permissions={[
              permissionConstants.report.read,
              permissionConstants.report.export,
              permissionConstants.report.payrollRead,
              permissionConstants.report.payrollExport,
            ]}
          />
        ),
        children: [
          {
            element: <LazyRouteBoundary moduleName="Reports" label="Loading reports" />,
            children: [
          {
            index: true,
            element: <ReportsModuleIndex />,
          },
          {
            path: 'revenue',
            element: <RevenueReportPage />,
          },
          {
            path: 'inventory',
            element: <InventoryReportPage />,
          },
          {
            path: 'payroll',
            element: <PayrollReportPage />,
          },
          {
            path: 'export-jobs',
            element: <ExportJobsPage />,
          },
          {
            path: 'export-jobs/:jobId',
            element: <ExportJobDetailPage />,
          },
          {
            path: 'export-jobs/:jobId/preview',
            element: <ExportPreviewPage />,
          },
          {
            path: 'export-jobs/:jobId/download',
            element: <ExportDownloadPage />,
          },
          {
            path: 'outlet-revenue',
            element: <OutletRevenueReportPage />,
          },
            ],
          },
        ],
      },
    ],
  },
])
