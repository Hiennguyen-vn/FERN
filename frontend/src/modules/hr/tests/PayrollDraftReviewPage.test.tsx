import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PayrollDraftReviewPage } from '../routes/PayrollDraftReviewPage'

const mocks = vi.hoisted(() => ({
  usePayrollRun: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  usePayrollRun: mocks.usePayrollRun,
}))

describe('PayrollDraftReviewPage', () => {
  beforeEach(() => {
    resetTestStores()
    mocks.usePayrollRun.mockReturnValue({
      data: {
        id: 88,
        payrollPeriodId: 21,
        runCode: 'RUN-202603',
        runDate: '2026-03-30',
        status: 'DRAFT',
        totalAmount: 45000000,
        paymentRef: null,
        note: 'Draft run',
        submittedAt: null,
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
            paymentStatus: 'PENDING',
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
  })

  it('renders summary, detail, and exception areas', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.finance.payrollRead,
          permissionConstants.finance.payrollDetailRead,
        ],
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/hr/payroll-draft-review/:runId" element={<PayrollDraftReviewPage />} />
      </Routes>,
      { route: '/hr/payroll-draft-review/88' },
    )

    expect(await screen.findByText('RUN-202603')).toBeInTheDocument()
    expect(screen.getByText('Employee results')).toBeInTheDocument()
    expect(screen.getByText('Exception review')).toBeInTheDocument()
    expect(screen.getByText('Missing overtime approval')).toBeInTheDocument()
  })

  it('masks financial detail without finance.payroll.detail.read', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.finance.payrollRead],
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/hr/payroll-draft-review/:runId" element={<PayrollDraftReviewPage />} />
      </Routes>,
      { route: '/hr/payroll-draft-review/88' },
    )

    expect(await screen.findByText('Employee-level gross/net/tax details được backend masked vì thiếu finance.payroll.detail.read.')).toBeInTheDocument()
    expect(screen.getAllByText('••••••••').length).toBeGreaterThan(0)
  })
})
