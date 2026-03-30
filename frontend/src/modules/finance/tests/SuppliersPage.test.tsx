import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { SupplierDetailPage } from '../routes/SupplierDetailPage'
import { SuppliersPage } from '../routes/SuppliersPage'

const mocks = vi.hoisted(() => ({
  useFinanceSupplier: vi.fn(),
  useFinanceSuppliers: vi.fn(),
}))

vi.mock('../hooks/useFinance', () => ({
  useFinanceSupplier: mocks.useFinanceSupplier,
  useFinanceSuppliers: mocks.useFinanceSuppliers,
}))

describe('Finance supplier screens', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.procurement.supplierRead],
      },
    })
    mocks.useFinanceSuppliers.mockReturnValue({
      data: [
        {
          id: 12,
          supplierCode: 'SUP-012',
          name: 'Lotus Foods',
          taxCode: 'TAX-12',
          email: 'lotus@fern.local',
          phone: '0901',
          address: '1 Nguyen Hue',
          defaultRegionId: 1,
          status: 'ACTIVE',
          approvedAt: '2026-03-29T09:00:00Z',
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useFinanceSupplier.mockReturnValue({
      data: {
        id: 12,
        supplierCode: 'SUP-012',
        name: 'Lotus Foods',
        taxCode: 'TAX-12',
        email: 'lotus@fern.local',
        phone: '0901',
        address: '1 Nguyen Hue',
        defaultRegionId: 1,
        status: 'ACTIVE',
        approvedAt: '2026-03-29T09:00:00Z',
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders suppliers list', async () => {
    renderWithProviders(<SuppliersPage />)

    expect(await screen.findByText('Lotus Foods')).toBeInTheDocument()
    expect(screen.getByText('SUP-012')).toBeInTheDocument()
  })

  it('renders supplier detail from the supplier list-backed query', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/finance/suppliers/:supplierId" element={<SupplierDetailPage />} />
      </Routes>,
      { route: '/finance/suppliers/12' },
    )

    expect(await screen.findByText('Lotus Foods')).toBeInTheDocument()
    expect(screen.getByText('Payables context')).toBeInTheDocument()
  })
})
