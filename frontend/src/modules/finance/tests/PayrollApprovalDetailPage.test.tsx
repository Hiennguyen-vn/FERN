import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PayrollApprovalDetailPage } from '../routes/PayrollApprovalDetailPage'

const mocks = vi.hoisted(() => ({
  useApprovePayrollRun: vi.fn(),
  useCancelPayrollRun: vi.fn(),
  usePayrollRun: vi.fn(),
  useRejectPayrollRun: vi.fn(),
}))

vi.mock('../hooks/useFinance', () => ({
  useApprovePayrollRun: mocks.useApprovePayrollRun,
  useCancelPayrollRun: mocks.useCancelPayrollRun,
  usePayrollRun: mocks.usePayrollRun,
  useRejectPayrollRun: mocks.useRejectPayrollRun,
}))

describe('PayrollApprovalDetailPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.finance.payrollRead,
          permissionConstants.finance.payrollApprove,
          permissionConstants.finance.payrollDetailRead,
        ],
      },
    })
    mocks.usePayrollRun.mockReturnValue({
      data: {
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
        employees: [
          {
            id: 1,
            employeeId: 101,
            contractId: 201,
            outletId: 301,
            grossPay: 22000000,
            deductionAmount: 1000000,
            taxAmount: 2000000,
            netPay: 19000000,
            workDays: 26,
            workHours: 208,
            overtimeHours: 8,
            paymentStatus: 'UNPAID',
            exceptionMessage: 'Missing overtime approval',
            lines: [],
            allocations: [],
          },
        ],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useApprovePayrollRun.mockReturnValue({ mutateAsync: vi.fn().mockResolvedValue({ id: 55 }), isPending: false })
    mocks.useRejectPayrollRun.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
    mocks.useCancelPayrollRun.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
  })

  it('renders approval workspace summary and exceptions', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/finance/payroll-approvals/:runId" element={<PayrollApprovalDetailPage />} />
      </Routes>,
      { route: '/finance/payroll-approvals/55' },
    )

    expect(await screen.findByText('RUN-000055')).toBeInTheDocument()
    expect(screen.getByText('Decision panel')).toBeInTheDocument()
    expect(screen.getByText('Exception review')).toBeInTheDocument()
    expect(screen.getByText('Missing overtime approval')).toBeInTheDocument()
  })

  it('supports decision presentation for approve action', async () => {
    const user = userEvent.setup()
    const mutateAsync = vi.fn().mockResolvedValue({ id: 55 })
    mocks.useApprovePayrollRun.mockReturnValue({ mutateAsync, isPending: false })

    renderWithProviders(
      <Routes>
        <Route path="/finance/payroll-approvals/:runId" element={<PayrollApprovalDetailPage />} />
      </Routes>,
      { route: '/finance/payroll-approvals/55' },
    )

    await user.click(await screen.findByRole('button', { name: 'Approve' }))
    await user.click(screen.getByRole('button', { name: 'Approve run' }))

    expect(mutateAsync).toHaveBeenCalledWith({
      payload: { note: undefined },
      runId: 55,
    })
  })
})
