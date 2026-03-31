import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RequireAnyPermission } from '@app/guards/RequireAnyPermission'
import { permissionConstants } from '@core/permissions/permission.constants'

// ─── Test component ──────────────────────────────────────────────────────────

function ProtectedContent() {
  return <div>module content</div>
}

function UnauthorizedPage() {
  return <div>unauthorized</div>
}

// ─── Catalog module ──────────────────────────────────────────────────────────
// Catalog nav visibility: hasAnyPermissions([
//   productRead, ingredientRead, recipeRead, priceRead
// ])
//
// Current router guard: RequirePermission([productRead]) — TOO NARROW
// Fix: Should be RequireAnyPermission([productRead, ingredientRead, recipeRead, priceRead])

describe('catalog module — router guard vs navigation visibility', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('allows user with productRead', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.catalog.productRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/catalog"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.catalog.productRead,
                permissionConstants.catalog.ingredientRead,
                permissionConstants.catalog.recipeRead,
                permissionConstants.catalog.priceRead,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/catalog' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })

  it('allows user with only ingredientRead (previously blocked by productRead-only guard)', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.catalog.ingredientRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/catalog"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.catalog.productRead,
                permissionConstants.catalog.ingredientRead,
                permissionConstants.catalog.recipeRead,
                permissionConstants.catalog.priceRead,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/catalog' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })

  it('allows user with only recipeRead', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.catalog.recipeRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/catalog"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.catalog.productRead,
                permissionConstants.catalog.ingredientRead,
                permissionConstants.catalog.recipeRead,
                permissionConstants.catalog.priceRead,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/catalog' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })

  it('blocks user with no catalog permissions', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.report.read] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/catalog"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.catalog.productRead,
                permissionConstants.catalog.ingredientRead,
                permissionConstants.catalog.recipeRead,
                permissionConstants.catalog.priceRead,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/catalog' },
    )

    expect(screen.getByText('unauthorized')).toBeInTheDocument()
  })
})

// ─── IAM module ──────────────────────────────────────────────────────────────
// IAM nav visibility: hasAnyPermissions([
//   userRead, roleRead, permissionRead, permissionOverrideRead
// ])
//
// Current router guard: RequirePermission([userRead]) — TOO NARROW
// Fix: Should be RequireAnyPermission([userRead, roleRead, permissionRead, permissionOverrideRead])

describe('iam module — router guard vs navigation visibility', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('allows user with userRead', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.iam.userRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/iam"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.iam.userRead,
                permissionConstants.iam.roleRead,
                permissionConstants.iam.permissionRead,
                permissionConstants.iam.permissionOverrideRead,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/iam' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })

  it('allows user with only roleRead (previously blocked by userRead-only guard)', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.iam.roleRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/iam"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.iam.userRead,
                permissionConstants.iam.roleRead,
                permissionConstants.iam.permissionRead,
                permissionConstants.iam.permissionOverrideRead,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/iam' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })

  it('allows user with only permissionRead', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.iam.permissionRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/iam"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.iam.userRead,
                permissionConstants.iam.roleRead,
                permissionConstants.iam.permissionRead,
                permissionConstants.iam.permissionOverrideRead,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/iam' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })
})

// ─── Finance module ──────────────────────────────────────────────────────────
// Finance nav visibility: hasAnyPermissions([
//   supplierRead, invoiceRead, invoiceReview, invoiceApprove, invoiceDispute,
//   paymentRead, paymentRecord, payrollRead, payrollApprove, payrollPay
// ])
//
// Current router guard: RequirePermission([payrollRead]) — TOO NARROW
// Fix: Should be RequireAnyPermission([...all finance navigation permissions])

describe('finance module — router guard vs navigation visibility', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('allows user with payrollRead', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.finance.payrollRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/finance"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.procurement.supplierRead,
                permissionConstants.procurement.invoiceRead,
                permissionConstants.procurement.invoiceReview,
                permissionConstants.procurement.invoiceApprove,
                permissionConstants.procurement.invoiceDispute,
                permissionConstants.procurement.paymentRead,
                permissionConstants.procurement.paymentRecord,
                permissionConstants.finance.payrollRead,
                permissionConstants.finance.payrollApprove,
                permissionConstants.finance.payrollPay,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/finance' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })

  it('allows user with only supplierRead (previously blocked by payrollRead-only guard)', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.procurement.supplierRead] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/finance"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.procurement.supplierRead,
                permissionConstants.procurement.invoiceRead,
                permissionConstants.procurement.invoiceReview,
                permissionConstants.procurement.invoiceApprove,
                permissionConstants.procurement.invoiceDispute,
                permissionConstants.procurement.paymentRead,
                permissionConstants.procurement.paymentRecord,
                permissionConstants.finance.payrollRead,
                permissionConstants.finance.payrollApprove,
                permissionConstants.finance.payrollPay,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/finance' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })

  it('allows user with only invoiceReview', () => {
    setAuthenticatedSession({
      principal: { permissions: [permissionConstants.procurement.invoiceReview] },
    })

    renderWithProviders(
      <Routes>
        <Route
          path="/finance"
          element={
            <RequireAnyPermission
              permissions={[
                permissionConstants.procurement.supplierRead,
                permissionConstants.procurement.invoiceRead,
                permissionConstants.procurement.invoiceReview,
                permissionConstants.procurement.invoiceApprove,
                permissionConstants.procurement.invoiceDispute,
                permissionConstants.procurement.paymentRead,
                permissionConstants.procurement.paymentRecord,
                permissionConstants.finance.payrollRead,
                permissionConstants.finance.payrollApprove,
                permissionConstants.finance.payrollPay,
              ]}
            />
          }
        >
          <Route index element={<ProtectedContent />} />
        </Route>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
      </Routes>,
      { route: '/finance' },
    )

    expect(screen.getByText('module content')).toBeInTheDocument()
  })
})
