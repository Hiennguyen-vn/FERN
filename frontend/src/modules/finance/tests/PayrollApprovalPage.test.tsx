import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PayrollApprovalPage } from '../routes/PayrollApprovalPage'

const mocks = vi.hoisted(() => ({
  usePayrollApprovalQueue: vi.fn(),
}))

vi.mock('../hooks/useFinance', () => ({
  usePayrollApprovalQueue: mocks.usePayrollApprovalQueue,
}))

describe('PayrollApprovalPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.finance.payrollRead, permissionConstants.finance.payrollApprove],
      },
    })
    mocks.usePayrollApprovalQueue.mockReturnValue({
      data: [
        {
          id: 55,
          payrollPeriodId: 11,
          runCode: 'RUN-000055',
          runDate: '2026-03-30',
          status: 'SUBMITTED',
          totalAmount: 45000000,
          paymentRef: null,
          note: 'March payroll',
          submittedAt: '2026-03-30T08:00:00Z',
          approvedAt: null,
          paidAt: null,
          employees: [],
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders actionable submitted payroll runs', async () => {
    renderWithProviders(<PayrollApprovalPage />)

    expect(await screen.findByText('RUN-000055')).toBeInTheDocument()
    expect(screen.getByText('Submitted: 1')).toBeInTheDocument()
  })
})
