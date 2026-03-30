import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { ContractDetailPage } from '../routes/ContractDetailPage'
import { ContractsPage } from '../routes/ContractsPage'

const mocks = vi.hoisted(() => ({
  useHrContracts: vi.fn(),
  useHrEmployee: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  useHrContracts: mocks.useHrContracts,
  useHrEmployee: mocks.useHrEmployee,
}))

describe('Contracts HR screens', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.hr.contractRead,
          permissionConstants.hr.employeeRead,
        ],
      },
    })
    mocks.useHrEmployee.mockReturnValue({
      data: {
        id: 42,
        employeeCode: 'EMP-042',
        fullName: 'Pham Thi C',
        dob: '1992-03-12',
        gender: 'FEMALE',
        email: 'pham@fern.local',
        phone: '0908',
        status: 'ACTIVE',
        hiredAt: '2023-07-10',
        userAccountId: 5,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrContracts.mockReturnValue({
      data: [
        {
          id: 200,
          employeeId: 42,
          employmentType: 'FULL_TIME',
          salaryType: 'MONTHLY',
          baseSalary: null,
          regionId: 1,
          taxCode: null,
          contractStatus: 'ACTIVE',
          startDate: '2024-01-01',
          endDate: null,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders employee-scoped contracts list', async () => {
    renderWithProviders(<ContractsPage />, { route: '/hr/contracts?employeeId=42' })

    expect(await screen.findByText('Current employee contract set')).toBeInTheDocument()
    expect(screen.getByText('EMP-042 · Pham Thi C')).toBeInTheDocument()
    expect(screen.getByText('#200')).toBeInTheDocument()
  })

  it('renders masked contract detail when contract detail permission is missing', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/hr/contracts/:contractId" element={<ContractDetailPage />} />
      </Routes>,
      { route: '/hr/contracts/200?employeeId=42' },
    )

    expect(await screen.findByText('Contract #200')).toBeInTheDocument()
    expect(screen.getAllByText('••••••••').length).toBeGreaterThan(0)
    expect(
      screen.getByText('Backend đã masked baseSalary vì thiếu hr.contract.detail.read.'),
    ).toBeInTheDocument()
  })
})
