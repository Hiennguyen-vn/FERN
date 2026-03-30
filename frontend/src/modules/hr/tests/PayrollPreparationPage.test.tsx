import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PayrollDraftReviewPage } from '../routes/PayrollDraftReviewPage'
import { PayrollPreparationPage } from '../routes/PayrollPreparationPage'

const mocks = vi.hoisted(() => ({
  useCreatePayrollPeriod: vi.fn(),
  useCreatePayrollRun: vi.fn(),
  usePayrollPeriods: vi.fn(),
  usePayrollRun: vi.fn(),
  usePayrollRuns: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  useCreatePayrollPeriod: mocks.useCreatePayrollPeriod,
  useCreatePayrollRun: mocks.useCreatePayrollRun,
  usePayrollPeriods: mocks.usePayrollPeriods,
  usePayrollRun: mocks.usePayrollRun,
  usePayrollRuns: mocks.usePayrollRuns,
}))

describe('PayrollPreparationPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.finance.payrollRead,
          permissionConstants.finance.payrollPrepare,
          permissionConstants.finance.payrollDetailRead,
        ],
      },
    })
    mocks.usePayrollPeriods.mockReturnValue({
      data: [
        {
          id: 11,
          regionId: 1,
          referenceCode: 'PER-202603',
          name: 'Payroll March 2026',
          startDate: '2026-03-01',
          endDate: '2026-03-31',
          payDate: '2026-04-05',
          status: 'OPEN',
          note: null,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.usePayrollRuns.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.usePayrollRun.mockReturnValue({
      data: {
        id: 77,
        payrollPeriodId: 11,
        runCode: 'RUN-77',
        runDate: '2026-03-30',
        status: 'DRAFT',
        totalAmount: 0,
        paymentRef: null,
        note: null,
        submittedAt: null,
        approvedAt: null,
        paidAt: null,
        employees: [],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useCreatePayrollPeriod.mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn().mockResolvedValue({
        id: 11,
        regionId: 1,
      }),
    })
    mocks.useCreatePayrollRun.mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn().mockResolvedValue({
        id: 77,
      }),
    })
  })

  it('validates required payroll period fields', async () => {
    const user = userEvent.setup()
    renderWithProviders(<PayrollPreparationPage />)

    await user.click(screen.getByRole('button', { name: 'Create payroll period' }))

    expect(await screen.findByText('Name, start date và end date là bắt buộc.')).toBeInTheDocument()
  })

  it('prepares a draft run and navigates to review', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <Routes>
        <Route path="/hr/payroll-preparation" element={<PayrollPreparationPage />} />
        <Route path="/hr/payroll-draft-review/:runId" element={<PayrollDraftReviewPage />} />
      </Routes>,
      { route: '/hr/payroll-preparation' },
    )

    await user.selectOptions(screen.getByLabelText('Payroll period'), '11')
    await user.type(screen.getByLabelText('Preparation note'), 'Prepare end-of-month draft')
    await user.click(screen.getByRole('button', { name: 'Prepare draft run' }))

    expect(await screen.findByText('RUN-77')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Payroll Draft Review' })).toBeInTheDocument()
  })
})
