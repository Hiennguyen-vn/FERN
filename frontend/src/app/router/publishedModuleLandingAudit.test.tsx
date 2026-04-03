import { Navigate, Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { RequireAnyPermission } from '@app/guards/RequireAnyPermission'
import { RequirePermission } from '@app/guards/RequirePermission'
import { useAuthStore } from '@core/auth/auth.store'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import type { FernPrincipal } from '@core/auth/auth.types'
import {
  resolveAuditLandingPath,
  resolveCatalogLandingPath,
  resolveFinanceLandingPath,
  resolveHrLandingPath,
  resolveInventoryLandingPath,
  resolveOrgLandingPath,
  resolveProcurementLandingPath,
  resolveWorkforceLandingPath,
} from './moduleLanding.service'

function ModuleLandingRoute({ resolvePath }: { resolvePath: (principal: FernPrincipal | null) => string | null }) {
  const principal = useAuthStore((state) => state.principal)
  const target = resolvePath(principal)
  return target ? <Navigate replace to={target} /> : <Navigate replace to="/unauthorized" />
}

function HrHarness() {
  return (
    <Routes>
      <Route
        path="/hr"
        element={
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
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveHrLandingPath} />} />
        <Route path="employees" element={<h1>employees</h1>} />
        <Route path="employees/new" element={<h1>employee-create</h1>} />
        <Route path="contracts" element={<h1>contracts</h1>} />
        <Route path="contracts/new" element={<h1>contract-create</h1>} />
        <Route path="attendance-summary" element={<h1>attendance-summary</h1>} />
        <Route path="payroll-preparation" element={<h1>payroll-preparation</h1>} />
        <Route path="shift-scheduling" element={<h1>shift-scheduling</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

function ProcurementHarness() {
  return (
    <Routes>
      <Route
        path="/procurement"
        element={
          <RequireAnyPermission
            permissions={[
              permissionConstants.procurement.purchaseOrderRead,
              permissionConstants.procurement.purchaseOrderCreate,
              permissionConstants.procurement.goodsReceiptRead,
              permissionConstants.procurement.goodsReceiptCreate,
            ]}
          />
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveProcurementLandingPath} />} />
        <Route path="purchase-orders/new" element={<h1>po-create</h1>} />
        <Route path="purchase-orders/:purchaseOrderId" element={<h1>po-detail</h1>} />
        <Route path="goods-receipts/new" element={<h1>gr-create</h1>} />
        <Route path="goods-receipts/:goodsReceiptId" element={<h1>gr-detail</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

function InventoryHarness() {
  return (
    <Routes>
      <Route
        path="/inventory"
        element={
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
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveInventoryLandingPath} />} />
        <Route path="stock-balances" element={<h1>stock-balances</h1>} />
        <Route path="transactions" element={<h1>transactions</h1>} />
        <Route path="stock-adjustments/new" element={<h1>stock-adjustment-create</h1>} />
        <Route path="waste-records/new" element={<h1>waste-record-create</h1>} />
        <Route path="stock-count-sessions" element={<h1>stock-count-sessions</h1>} />
        <Route path="stock-count-sessions/new" element={<h1>stock-count-create</h1>} />
        <Route path="stock-count-sessions/:sessionId" element={<h1>stock-count-detail</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
      <Route path="*" element={<h1>not-found</h1>} />
    </Routes>
  )
}

function CatalogHarness() {
  return (
    <Routes>
      <Route
        path="/catalog"
        element={
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
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveCatalogLandingPath} />} />
        <Route path="products" element={<h1>products</h1>} />
        <Route path="products/new" element={<h1>product-create</h1>} />
        <Route path="ingredients/new" element={<h1>ingredient-create</h1>} />
        <Route path="promotions" element={<h1>promotions</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

function FinanceHarness() {
  return (
    <Routes>
      <Route
        path="/finance"
        element={
          <RequireAnyPermission
            permissions={[
              permissionConstants.procurement.supplierRead,
              permissionConstants.procurement.supplierWrite,
              permissionConstants.procurement.invoiceRead,
              permissionConstants.procurement.invoiceReview,
              permissionConstants.procurement.invoiceApprove,
              permissionConstants.procurement.invoiceDispute,
              permissionConstants.procurement.paymentRead,
              permissionConstants.procurement.paymentRecord,
              permissionConstants.finance.payrollRead,
              permissionConstants.finance.payrollPrepare,
              permissionConstants.finance.payrollApprove,
              permissionConstants.finance.payrollPay,
              permissionConstants.finance.configRead,
              permissionConstants.finance.configWrite,
            ]}
          />
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveFinanceLandingPath} />} />
        <Route path="suppliers" element={<h1>suppliers</h1>} />
        <Route path="payroll-periods" element={<h1>payroll-periods</h1>} />
        <Route path="config" element={<h1>finance-config</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

function OrgHarness() {
  return (
    <Routes>
      <Route
        path="/org"
        element={
          <RequireAnyPermission
            permissions={[
              permissionConstants.org.regionRead,
              permissionConstants.org.outletRead,
              permissionConstants.org.regionWrite,
              permissionConstants.org.outletWrite,
            ]}
          />
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveOrgLandingPath} />} />
        <Route path="regions" element={<h1>regions</h1>} />
        <Route path="regions/new" element={<h1>region-create</h1>} />
        <Route path="outlets" element={<h1>outlets</h1>} />
        <Route path="outlets/new" element={<h1>outlet-create</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

function WorkforceHarness() {
  return (
    <Routes>
      <Route
        path="/workforce"
        element={
          <RequireAnyPermission
            permissions={[
              permissionConstants.hr.attendanceWrite,
              permissionConstants.hr.attendanceReview,
            ]}
          />
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveWorkforceLandingPath} />} />
        <Route path="my-attendance" element={<h1>my-attendance</h1>} />
        <Route path="attendance-approvals" element={<h1>attendance-approvals</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

function RegionalOpsHarness() {
  return (
    <Routes>
      <Route
        path="/regional-ops"
        element={
          <RequireAnyPermission
            permissions={[
              permissionConstants.org.regionRead,
              permissionConstants.org.outletRead,
            ]}
          />
        }
      >
        <Route index element={<h1>regional-dashboard</h1>} />
        <Route path="outlets" element={<h1>outlet-summary</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

function AuditHarness() {
  return (
    <Routes>
      <Route path="/audit" element={<RequirePermission permissions={[permissionConstants.audit.read]} />}>
        <Route index element={<ModuleLandingRoute resolvePath={resolveAuditLandingPath} />} />
        <Route path="events" element={<h1>audit-events</h1>} />
        <Route path="request-traces" element={<h1>request-traces</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
    </Routes>
  )
}

describe('published module landing audit', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('lands contract-read HR users on contracts', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.hr.contractRead] } })
    renderWithProviders(<HrHarness />, { route: '/hr' })
    expect(await screen.findByRole('heading', { name: 'contracts' })).toBeInTheDocument()
  })

  it('lands employee-write HR users on employee create', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.hr.employeeWrite] } })
    renderWithProviders(<HrHarness />, { route: '/hr' })
    expect(await screen.findByRole('heading', { name: 'employee-create' })).toBeInTheDocument()
  })

  it('lands payroll-prepare HR users on payroll preparation', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.finance.payrollPrepare] } })
    renderWithProviders(<HrHarness />, { route: '/hr' })
    expect(await screen.findByRole('heading', { name: 'payroll-preparation' })).toBeInTheDocument()
  })

  it('lands shift-read HR users on shift scheduling', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.hr.shiftRead] } })
    renderWithProviders(<HrHarness />, { route: '/hr' })
    expect(await screen.findByRole('heading', { name: 'shift-scheduling' })).toBeInTheDocument()
  })

  it('lands procurement create users on the first creatable workflow', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.procurement.purchaseOrderCreate] } })
    renderWithProviders(<ProcurementHarness />, { route: '/procurement' })
    expect(await screen.findByRole('heading', { name: 'po-create' })).toBeInTheDocument()
  })

  it('lands product-write catalog users on product create', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.catalog.productWrite] } })
    renderWithProviders(<CatalogHarness />, { route: '/catalog' })
    expect(await screen.findByRole('heading', { name: 'product-create' })).toBeInTheDocument()
  })

  it('lands promotion-only catalog users on promotions', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.catalog.promotionRead] } })
    renderWithProviders(<CatalogHarness />, { route: '/catalog' })
    expect(await screen.findByRole('heading', { name: 'promotions' })).toBeInTheDocument()
  })

  it('keeps procurement read-only users off the module root while preserving PO detail deep links', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.procurement.purchaseOrderRead] } })
    const rootRender = renderWithProviders(<ProcurementHarness />, { route: '/procurement' })
    expect(await screen.findByRole('heading', { name: 'unauthorized' })).toBeInTheDocument()
    rootRender.unmount()

    renderWithProviders(<ProcurementHarness />, { route: '/procurement/purchase-orders/77' })
    expect(await screen.findByRole('heading', { name: 'po-detail' })).toBeInTheDocument()
  })

  it('lands ledger-only inventory users on transactions', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.inventory.ledgerRead] } })
    renderWithProviders(<InventoryHarness />, { route: '/inventory' })
    expect(await screen.findByRole('heading', { name: 'transactions' })).toBeInTheDocument()
  })

  it('lands stock-adjustment writers on the create workflow', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.inventory.adjustmentWrite] } })
    renderWithProviders(<InventoryHarness />, { route: '/inventory' })
    expect(await screen.findByRole('heading', { name: 'stock-adjustment-create' })).toBeInTheDocument()
  })

  it('does not publish the removed inter-outlet transfer route in V1', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.inventory.balanceRead,
          permissionConstants.inventory.ledgerRead,
        ],
      },
    })
    renderWithProviders(<InventoryHarness />, { route: '/inventory/transfers' })
    expect(await screen.findByRole('heading', { name: 'not-found' })).toBeInTheDocument()
  })

  it('lands review-only workforce users on attendance approvals', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.hr.attendanceReview] } })
    renderWithProviders(<WorkforceHarness />, { route: '/workforce' })
    expect(await screen.findByRole('heading', { name: 'attendance-approvals' })).toBeInTheDocument()
  })

  it('allows outlet-read regional ops users to open the module root', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.org.outletRead] } })
    renderWithProviders(<RegionalOpsHarness />, { route: '/regional-ops' })
    expect(await screen.findByRole('heading', { name: 'regional-dashboard' })).toBeInTheDocument()
  })

  it('lands region-write org users on region create', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.org.regionWrite] } })
    renderWithProviders(<OrgHarness />, { route: '/org' })
    expect(await screen.findByRole('heading', { name: 'region-create' })).toBeInTheDocument()
  })

  it('lands config-only finance users on finance config', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.finance.configRead] } })
    renderWithProviders(<FinanceHarness />, { route: '/finance' })
    expect(await screen.findByRole('heading', { name: 'finance-config' })).toBeInTheDocument()
  })

  it('lands payroll-prepare finance users on payroll periods', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.finance.payrollPrepare] } })
    renderWithProviders(<FinanceHarness />, { route: '/finance' })
    expect(await screen.findByRole('heading', { name: 'payroll-periods' })).toBeInTheDocument()
  })

  it('lands audit users on audit events from the module root', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.audit.read] } })
    renderWithProviders(<AuditHarness />, { route: '/audit' })
    expect(await screen.findByRole('heading', { name: 'audit-events' })).toBeInTheDocument()
  })

  it('blocks users with no matching module permission', async () => {
    setAuthenticatedSession({ principal: { permissions: [] } })
    renderWithProviders(<InventoryHarness />, { route: '/inventory' })
    expect(await screen.findByRole('heading', { name: 'unauthorized' })).toBeInTheDocument()
  })
})
