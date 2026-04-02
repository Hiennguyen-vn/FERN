import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { SupplierPaymentListPage } from '../routes/SupplierPaymentListPage'

const mocks = vi.hoisted(() => ({
  useSupplierPayments: vi.fn(),
}))

vi.mock('../hooks/useSupplierPayment', () => ({
  useSupplierPayments: mocks.useSupplierPayments,
}))

describe('SupplierPaymentListPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.procurement.paymentRead],
      },
    })
    mocks.useSupplierPayments.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('passes undefined supplierId when the filter is empty', () => {
    renderWithProviders(<SupplierPaymentListPage />)

    expect(mocks.useSupplierPayments).toHaveBeenLastCalledWith({ supplierId: undefined, limit: 50 })
  })

  it('passes a positive supplierId when the filter is valid', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SupplierPaymentListPage />)

    await user.type(screen.getByLabelText('Supplier ID'), '12')

    expect(mocks.useSupplierPayments).toHaveBeenLastCalledWith({ supplierId: 12, limit: 50 })
  })

  it('maps cleared or invalid values back to undefined', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SupplierPaymentListPage />)
    const supplierInput = screen.getByLabelText('Supplier ID')

    await user.type(supplierInput, '12')
    expect(mocks.useSupplierPayments).toHaveBeenLastCalledWith({ supplierId: 12, limit: 50 })

    await user.clear(supplierInput)
    expect(mocks.useSupplierPayments).toHaveBeenLastCalledWith({ supplierId: undefined, limit: 50 })

    await user.type(supplierInput, '0')
    expect(mocks.useSupplierPayments).toHaveBeenLastCalledWith({ supplierId: undefined, limit: 50 })

    await user.clear(supplierInput)
    await user.type(supplierInput, '1.5')
    expect(mocks.useSupplierPayments).toHaveBeenLastCalledWith({ supplierId: undefined, limit: 50 })
  })
})
