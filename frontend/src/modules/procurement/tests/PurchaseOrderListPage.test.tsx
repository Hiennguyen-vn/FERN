import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PurchaseOrderListPage } from '../routes/PurchaseOrderListPage'

const mocks = vi.hoisted(() => ({
  usePurchaseOrders: vi.fn(),
  useSuppliers: vi.fn(),
}))

vi.mock('../hooks/usePurchaseOrder', () => ({
  usePurchaseOrders: mocks.usePurchaseOrders,
}))

vi.mock('../hooks/useSuppliers', () => ({
  useSuppliers: mocks.useSuppliers,
}))

describe('PurchaseOrderListPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.procurement.purchaseOrderRead,
          permissionConstants.procurement.supplierRead,
        ],
      },
    })

    mocks.usePurchaseOrders.mockReturnValue({
      data: [
        {
          id: 100,
          poNumber: 'PO-100',
          regionId: 1,
          outletId: 101,
          supplierId: 20,
          orderDate: '2026-01-15',
          expectedDeliveryDate: '2026-01-18',
          status: 'SUBMITTED',
          subtotalAmount: 300000,
          taxAmount: 0,
          totalAmount: 300000,
          note: null,
          approvedAt: null,
          issuedAt: null,
          lines: [],
        },
        {
          id: 101,
          poNumber: 'PO-101',
          regionId: 1,
          outletId: 101,
          supplierId: 20,
          orderDate: '2026-02-12',
          expectedDeliveryDate: '2026-02-15',
          status: 'APPROVED',
          subtotalAmount: 450000,
          taxAmount: 0,
          totalAmount: 450000,
          note: null,
          approvedAt: '2026-02-13T08:00:00Z',
          issuedAt: null,
          lines: [],
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    mocks.useSuppliers.mockReturnValue({
      data: [
        {
          id: 20,
          supplierCode: 'SUP-20',
          name: 'Fresh Supply Co',
          taxCode: null,
          email: null,
          phone: null,
          address: null,
          defaultRegionId: 1,
          status: 'ACTIVE',
          approvedAt: null,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders procurement dashboard summaries and dynamic charts', async () => {
    const { container } = renderWithProviders(<PurchaseOrderListPage />)

    expect(screen.getByRole('heading', { name: 'Procurement Workspace' })).toBeInTheDocument()
    expect(screen.getAllByText('PO-100').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Fresh Supply Co').length).toBeGreaterThan(0)
    expect(screen.getByText('Pending approvals')).toBeInTheDocument()
    expect(screen.getByLabelText('Procurement trend chart')).toBeInTheDocument()
    expect(container.querySelector('.metric-meter-svg')).not.toBeNull()
    expect(container.querySelectorAll('.trend-bar').length).toBeGreaterThan(0)
  })
})
