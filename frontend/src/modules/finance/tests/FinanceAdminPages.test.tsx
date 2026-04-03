import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { FinanceConfigPage } from '../routes/FinanceConfigPage'
import { PayrollPeriodsPage } from '../routes/PayrollPeriodsPage'

const mocks = vi.hoisted(() => ({
  createPayrollPeriod: vi.fn(),
  listPayrollPeriods: vi.fn(),
  useNumberingRule: vi.fn(),
  usePutNumberingRule: vi.fn(),
  usePutSystemPolicy: vi.fn(),
  useSystemPolicy: vi.fn(),
}))

vi.mock('../../hr/api/hr.api', () => ({
  hrApi: {
    createPayrollPeriod: mocks.createPayrollPeriod,
    listPayrollPeriods: mocks.listPayrollPeriods,
  },
}))

vi.mock('../hooks/useFinance', () => ({
  useNumberingRule: mocks.useNumberingRule,
  usePutNumberingRule: mocks.usePutNumberingRule,
  usePutSystemPolicy: mocks.usePutSystemPolicy,
  useSystemPolicy: mocks.useSystemPolicy,
}))

describe('Finance admin surfaces', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.finance.configRead,
          permissionConstants.finance.configWrite,
          permissionConstants.finance.payrollRead,
        ],
      },
    })

    mocks.listPayrollPeriods.mockResolvedValue([
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
    ])
    mocks.createPayrollPeriod.mockResolvedValue({ id: 12, regionId: 1 })

    mocks.useNumberingRule.mockImplementation((documentType: string) => ({
      data: {
        id: 1,
        documentType,
        prefix: `${documentType.slice(0, 3)}-`,
        regionId: null,
        outletId: null,
        nextNumber: 42,
        resetPeriod: 'MONTHLY',
        formatPattern: '{prefix}{year}{seq:04}',
        active: true,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    }))
    mocks.usePutNumberingRule.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
      error: null,
    })
    mocks.useSystemPolicy.mockImplementation((policyKey: string) => ({
      data: {
        policyKey,
        policyValue: policyKey === 'DEFAULT_CURRENCY' ? 'VND' : true,
        description: `Description for ${policyKey}`,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    }))
    mocks.usePutSystemPolicy.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
      error: null,
    })
  })

  it('renders payroll periods list and create toggle', async () => {
    const user = userEvent.setup()

    renderWithProviders(<PayrollPeriodsPage />)

    expect(await screen.findByText('PER-202603')).toBeInTheDocument()
    expect(screen.getByText('Payroll March 2026')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tạo Payroll Period' }))

    expect(screen.getByText('Tạo Payroll Period mới')).toBeInTheDocument()
    expect(screen.getByLabelText('Tên kỳ lương')).toBeInTheDocument()
  })

  it('renders finance config tabs and policy table', async () => {
    const user = userEvent.setup()

    renderWithProviders(<FinanceConfigPage />)

    expect(screen.getByText('Document Numbering Rules')).toBeInTheDocument()
    expect(screen.getByText('PAYROLL_RUN')).toBeInTheDocument()
    expect(screen.getByText('PUR-')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'System Policies' }))

    expect(screen.getByRole('heading', { name: 'System Policies' })).toBeInTheDocument()
    expect(screen.getByText('DEFAULT_CURRENCY')).toBeInTheDocument()
    expect(screen.getByText('Description for DEFAULT_CURRENCY')).toBeInTheDocument()
  })
})
