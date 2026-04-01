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
  resolveHrLandingPath,
  resolveInventoryLandingPath,
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
              permissionConstants.hr.contractRead,
              permissionConstants.hr.attendanceReview,
              permissionConstants.finance.payrollRead,
              permissionConstants.finance.payrollPrepare,
            ]}
          />
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveHrLandingPath} />} />
        <Route path="employees" element={<h1>employees</h1>} />
        <Route path="contracts" element={<h1>contracts</h1>} />
        <Route path="attendance-summary" element={<h1>attendance-summary</h1>} />
        <Route path="payroll-preparation" element={<h1>payroll-preparation</h1>} />
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
            ]}
          />
        }
      >
        <Route index element={<ModuleLandingRoute resolvePath={resolveInventoryLandingPath} />} />
        <Route path="stock-balances" element={<h1>stock-balances</h1>} />
        <Route path="transactions" element={<h1>transactions</h1>} />
      </Route>
      <Route path="/unauthorized" element={<h1>unauthorized</h1>} />
      <Route path="*" element={<h1>not-found</h1>} />
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

  it('lands payroll-prepare HR users on payroll preparation', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.finance.payrollPrepare] } })
    renderWithProviders(<HrHarness />, { route: '/hr' })
    expect(await screen.findByRole('heading', { name: 'payroll-preparation' })).toBeInTheDocument()
  })

  it('lands procurement create users on the first creatable workflow', async () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.procurement.purchaseOrderCreate] } })
    renderWithProviders(<ProcurementHarness />, { route: '/procurement' })
    expect(await screen.findByRole('heading', { name: 'po-create' })).toBeInTheDocument()
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
