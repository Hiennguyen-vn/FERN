import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PaymentRequestsPage } from '../routes/PaymentRequestsPage'

const mocks = vi.hoisted(() => ({
  useFinanceSuppliers: vi.fn(),
  usePaymentRequest: vi.fn(),
  useRecentPaymentRequests: vi.fn(),
}))

vi.mock('../hooks/useFinance', () => ({
  useFinanceSuppliers: mocks.useFinanceSuppliers,
  usePaymentRequest: mocks.usePaymentRequest,
  useRecentPaymentRequests: mocks.useRecentPaymentRequests,
}))

describe('PaymentRequestsPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.procurement.invoiceRead, permissionConstants.procurement.supplierRead],
      },
    })
    mocks.useFinanceSuppliers.mockReturnValue({
      data: [
        {
          id: 12,
          supplierCode: 'SUP-012',
          name: 'Lotus Foods',
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useRecentPaymentRequests.mockReturnValue({
      items: [],
      clear: vi.fn(),
      refresh: vi.fn(),
      save: vi.fn(),
    })
    mocks.usePaymentRequest.mockReturnValue({
      data: undefined,
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders empty recent state when no lookup has been made', async () => {
    renderWithProviders(<PaymentRequestsPage />)

    expect(await screen.findByText('No recent payment requests')).toBeInTheDocument()
  })

  it('supports invoice lookup and renders request detail', async () => {
    const user = userEvent.setup()

    mocks.usePaymentRequest.mockImplementation((invoiceId: number) => ({
      data:
        invoiceId === 45
          ? {
              id: 45,
              supplierId: 12,
              regionId: 1,
              outletId: 101,
              currencyCode: 'VND',
              invoiceNumber: 'INV-45',
              invoiceDate: '2026-03-20',
              dueDate: '2026-03-27',
              subtotal: 1000000,
              taxAmount: 100000,
              totalAmount: 1100000,
              status: 'APPROVED',
              note: 'Ready for payment',
              approvedAt: '2026-03-25T08:00:00Z',
              lines: [],
            }
          : undefined,
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    }))

    renderWithProviders(<PaymentRequestsPage />)

    await user.type(screen.getByLabelText('Invoice ID'), '45')
    await user.click(screen.getByRole('button', { name: 'Lookup invoice' }))

    expect(await screen.findByText('INV-45 · #45')).toBeInTheDocument()
    expect(screen.getByText('Request summary')).toBeInTheDocument()
  })

  it('shows permission denied without invoice read permissions', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(<PaymentRequestsPage />)

    expect(screen.getByText('Bạn cần quyền procurement.invoice.read hoặc permission payables liên quan để mở payment requests.')).toBeInTheDocument()
  })
})
