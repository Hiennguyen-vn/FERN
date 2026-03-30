import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PayrollPaidPage } from '../routes/PayrollPaidPage'

const mocks = vi.hoisted(() => ({
  useMarkPayrollPaid: vi.fn(),
  usePayrollRun: vi.fn(),
}))

vi.mock('../hooks/useFinance', () => ({
  useMarkPayrollPaid: mocks.useMarkPayrollPaid,
  usePayrollRun: mocks.usePayrollRun,
}))

describe('PayrollPaidPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.finance.payrollRead,
          permissionConstants.finance.payrollPay,
          permissionConstants.finance.payrollDetailRead,
        ],
      },
    })
    mocks.useMarkPayrollPaid.mockReturnValue({ mutateAsync: vi.fn().mockResolvedValue({ id: 55 }), isPending: false })
  })

  it('renders terminal readonly messaging for paid payroll runs', async () => {
    mocks.usePayrollRun.mockReturnValue({
      data: {
        id: 55,
        payrollPeriodId: 11,
        runCode: 'RUN-000055',
        runDate: '2026-03-30',
        status: 'PAID',
        totalAmount: 45000000,
        paymentRef: 'PAY-55',
        note: 'Paid',
        submittedAt: '2026-03-30T08:00:00Z',
        approvedAt: '2026-03-30T09:00:00Z',
        paidAt: '2026-03-30T10:00:00Z',
        employees: [],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(
      <Routes>
        <Route path="/finance/payroll-paid/:runId" element={<PayrollPaidPage />} />
      </Routes>,
      { route: '/finance/payroll-paid/55' },
    )

    expect(await screen.findByText('PAY-55')).toBeInTheDocument()
    expect(screen.getByText('Terminal readonly state')).toBeInTheDocument()
  })

  it('submits mark-paid workflow for approved runs', async () => {
    const user = userEvent.setup()
    const mutateAsync = vi.fn().mockResolvedValue({ id: 55 })
    mocks.useMarkPayrollPaid.mockReturnValue({ mutateAsync, isPending: false })
    mocks.usePayrollRun.mockReturnValue({
      data: {
        id: 55,
        payrollPeriodId: 11,
        runCode: 'RUN-000055',
        runDate: '2026-03-30',
        status: 'APPROVED',
        totalAmount: 45000000,
        paymentRef: null,
        note: 'Approved',
        submittedAt: '2026-03-30T08:00:00Z',
        approvedAt: '2026-03-30T09:00:00Z',
        paidAt: null,
        employees: [],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(
      <Routes>
        <Route path="/finance/payroll-paid/:runId" element={<PayrollPaidPage />} />
      </Routes>,
      { route: '/finance/payroll-paid/55' },
    )

    await user.type(await screen.findByLabelText('Payment reference'), 'PAY-202603-055')
    await user.click(screen.getByRole('button', { name: 'Mark payroll as paid' }))
    await user.click(screen.getByRole('button', { name: 'Mark as paid' }))

    expect(mutateAsync).toHaveBeenCalledWith({
      payload: {
        note: undefined,
        paymentReference: 'PAY-202603-055',
      },
      runId: 55,
    })
  })
})
