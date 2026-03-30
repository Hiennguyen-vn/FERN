import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PayrollReportPage } from '../routes/PayrollReportPage'

const mocks = vi.hoisted(() => ({
  useCreateExportJob: vi.fn(),
  usePayrollReport: vi.fn(),
}))

vi.mock('../hooks/useCreateExportJob', () => ({
  useCreateExportJob: mocks.useCreateExportJob,
}))

vi.mock('../hooks/usePayrollReport', () => ({
  usePayrollReport: mocks.usePayrollReport,
}))

describe('PayrollReportPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('renders payroll summary and masks employee amounts without detail permission', async () => {
    const user = userEvent.setup()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.payrollRead, permissionConstants.report.payrollExport],
      },
    })
    mocks.useCreateExportJob.mockReturnValue({ isPending: false, mutateAsync: vi.fn() })
    mocks.usePayrollReport.mockReturnValue({
      runDetailQuery: {
        data: {
          allocations: [{ outletId: 101, totalAmount: 1000000 }],
          employees: [
            {
              businessDate: '2026-03-29',
              employeeId: 8,
              grossPay: 15000000,
              netPay: 12000000,
              outletId: 101,
              taxAmount: 1000000,
            },
          ],
          payrollRunId: 55,
        },
        error: null,
        isLoading: false,
        refetch: vi.fn(),
      },
      runsQuery: {
        data: [
          {
            approvedAt: null,
            id: 55,
            note: 'March payroll',
            paidAt: null,
            paymentRef: null,
            payrollPeriodId: 11,
            runCode: 'RUN-55',
            runDate: '2026-03-30',
            status: 'SUBMITTED',
            submittedAt: '2026-03-30T08:00:00.000Z',
            totalAmount: 12000000,
          },
        ],
        error: null,
        isLoading: false,
        refetch: vi.fn(),
      },
      summaryQuery: {
        data: {
          fromDate: '2026-03-01',
          regionId: 1,
          runCount: 1,
          toDate: '2026-03-31',
          totalExpense: 12000000,
          totalGrossPay: 15000000,
          totalNetPay: 12000000,
          totalTax: 1000000,
        },
        error: null,
        isLoading: false,
        refetch: vi.fn(),
      },
    })

    renderWithProviders(<PayrollReportPage />)

    expect(screen.getByRole('heading', { name: 'Payroll Report' })).toBeInTheDocument()
    expect(screen.getByText('Payroll runs')).toBeInTheDocument()
    expect(screen.getByText('Employee-level payroll amounts đang bị masked vì thiếu finance.payroll.detail.read.')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Payroll run'), '55')
    expect(screen.getByText('Employee results')).toBeInTheDocument()
    expect(screen.getAllByText('••••••').length).toBeGreaterThan(0)
  })

  it('shows permission denied without payroll report permissions', () => {
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useCreateExportJob.mockReturnValue({ isPending: false, mutateAsync: vi.fn() })
    mocks.usePayrollReport.mockReturnValue({
      runDetailQuery: { data: null, error: null, isLoading: false, refetch: vi.fn() },
      runsQuery: { data: [], error: null, isLoading: false, refetch: vi.fn() },
      summaryQuery: { data: null, error: null, isLoading: false, refetch: vi.fn() },
    })

    renderWithProviders(<PayrollReportPage />)

    expect(screen.getByText('Permission denied')).toBeInTheDocument()
  })
})
