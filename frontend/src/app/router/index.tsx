import type { ReactNode } from 'react'
import { useState } from 'react'
import { createBrowserRouter, Link, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { AppShell } from '@app/layouts/AppShell'
import { AuthLayout } from '@app/layouts/AuthLayout'
import { EmptyLayout } from '@app/layouts/EmptyLayout'
import { PosLayout } from '@app/layouts/PosLayout'
import { RequireAuth } from '@app/guards/RequireAuth'
import { RequireOutletContext } from '@app/guards/RequireOutletContext'
import { RequirePermission } from '@app/guards/RequirePermission'
import { RequireAnyPermission } from '@app/guards/RequireAnyPermission'
import { login } from '@core/auth/auth.service'
import { permissionConstants } from '@core/permissions/permission.constants'
import { LazyRouteBoundary } from './LazyRouteBoundary'
import { useAuthStore } from '@core/auth/auth.store'
import { Button, Input } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { HomeActionHub } from '@app/surfaces/HomeActionHub'
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
import { canReadGoodsReceipts, canReadPurchaseOrders } from '@core/permissions/procurement.permissions'

import {
  PosHomePage,
  PosOrderDetailPage,
  PosSessionsPage,
  PosSessionDetailPage,
  SupplierListPage,
  SupplierCreatePage,
  PurchaseOrderCreatePage,
  PurchaseOrderDetailPage,
  GoodsReceiptCreatePage,
  GoodsReceiptDetailPage,
  SupplierInvoiceCreatePage,
  SupplierInvoiceDetailPage,
  SupplierPaymentCreatePage,
  PurchaseOrderListPage,
  GoodsReceiptListPage,
  SupplierInvoiceListPage,
  SupplierPaymentListPage,
  ThreeWayMatchingPage,
  StockOverviewPage,
  InventoryTransactionsPage,
  StockAdjustmentCreatePage,
  WasteRecordCreatePage,
  StockCountSessionCreatePage,
  StockCountSessionsPage,
  StockCountSessionDetailPage,
  MyAttendancePage,
  AttendanceReviewPage,
  AttendanceDetailPage,
  ProductsPage,
  ProductCreatePage,
  ProductDetailPage,
  ProductEditPage,
  IngredientsPage,
  IngredientCreatePage,
  IngredientEditPage,
  RecipesPage,
  PricingPage,
  AvailabilityPage,
  PromotionsPage,
  UsersPage,
  UserDetailPage,
  AssignmentsPage,
  EffectiveAccessPage,
  AuditEventsPage,
  AuditEventDetailPage,
  SecurityEventsPage,
  RequestTracesPage,
  RequestTraceDetailPage,
  RegionalDashboardPage,
  RegionalOutletSummaryPage,
  RegionalOutletDetailPage,
  ExchangeRateManagementPage,
  OrgRegionsPage,
  OrgRegionDetailPage,
  OrgRegionCreatePage,
  OrgRegionEditPage,
  OrgOutletsPage,
  OrgOutletDetailPage,
  OrgOutletCreatePage,
  OrgOutletEditPage,
  EmployeesPage,
  EmployeeCreatePage,
  EmployeeDetailPage,
  ContractsPage,
  ContractCreatePage,
  ContractDetailPage,
  AssignmentCreatePage,
  AttendanceSummaryPage,
  PayrollPreparationPage,
  PayrollDraftReviewPage,
  ShiftSchedulingPage,
  SuppliersPage,
  SupplierDetailPage,
  PaymentRequestsPage,
  PayrollApprovalPage,
  PayrollApprovalDetailPage,
  PayrollPaidPage,
  FinanceConfigPage,
  PayrollPeriodsPage,
  ReportsDashboardPage,
  RevenueReportPage,
  InventoryReportPage,
  PayrollReportPage,
  ExportJobsPage,
  ExportJobDetailPage,
  ExportPreviewPage,
  ExportDownloadPage,
  OutletRevenueReportPage,
} from './lazyRouteElements'


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
          <p className="eyebrow">Secure sign-in</p>
          <h1>Welcome back</h1>
          <p className="muted-text">Sign in to the operational shell for live F&amp;B command, control, and reporting.</p>
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
            placeholder="Enter your username"
            value={username}
          />
          <Input
            autoComplete="current-password"
            label="Password"
            onChange={(event) => setPassword(event.target.value)}
            placeholder="Enter your password"
            type="password"
            value={password}
          />
          {error ? <p className="error-text">{error}</p> : null}
          <Button loading={submitting} type="submit">
            {!submitting ? <AppIcon name="login" size="sm" /> : null}
            Sign in
          </Button>
        </form>
        <div className="auth-footnote">
          <span>Access rights are determined by your assigned role and scope.</span>
          <span>Auth, refresh, and permission contracts remain unchanged.</span>
        </div>
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
          <p className="eyebrow">Security boundary</p>
          <h1>Session expired</h1>
          <p className="muted-text">The access token is no longer valid or refresh could not complete.</p>
        </div>
        <Button asChild>
          <Link to="/login">
            <AppIcon name="arrow_back" size="sm" />
            Back to login
          </Link>
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
          <h1>Access unavailable</h1>
          <p className="muted-text">The current role or scope does not publish access to this workspace.</p>
        </div>
        <Button asChild variant="secondary">
          <Link to="/home">
            <AppIcon name="home" size="sm" />
            Go to home
          </Link>
        </Button>
      </div>
    </AuthPageFrame>
  )
}

export function HomePage() {
  usePageTitle('Action Hub — FERN Platform')
  return <HomeActionHub />
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
                path: 'suppliers',
                element: <SupplierListPage />,
              },
              {
                path: 'suppliers/new',
                element: <SupplierCreatePage />,
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
                path: 'stock-count-sessions',
                element: <StockCountSessionsPage />,
              },
              {
                path: 'stock-count-sessions/new',
                element: <StockCountSessionCreatePage />,
              },
              {
                path: 'stock-count-sessions/:sessionId',
                element: <StockCountSessionDetailPage />,
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
