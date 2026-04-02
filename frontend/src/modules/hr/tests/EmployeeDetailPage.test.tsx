import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { EmployeeDetailPage } from '../routes/EmployeeDetailPage'

const mocks = vi.hoisted(() => ({
  useHrAssignments: vi.fn(),
  useHrContracts: vi.fn(),
  useHrEmployee: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  useHrAssignments: mocks.useHrAssignments,
  useHrContracts: mocks.useHrContracts,
  useHrEmployee: mocks.useHrEmployee,
}))

describe('EmployeeDetailPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.hr.employeeRead,
          permissionConstants.hr.contractRead,
          permissionConstants.hr.contractDetailRead,
          permissionConstants.hr.shiftRead,
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
          baseSalary: 22000000,
          regionId: 1,
          taxCode: 'TX-200',
          contractStatus: 'ACTIVE',
          startDate: '2024-01-01',
          endDate: null,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrAssignments.mockReturnValue({
      data: [
        {
          id: 701,
          employeeId: 42,
          regionId: 1,
          outletId: 101,
          positionTitle: 'Area Trainer',
          startDate: '2024-01-01',
          endDate: null,
          primaryAssignment: true,
          status: 'ACTIVE',
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders employee overview, contracts, and assignments', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/hr/employees/:employeeId" element={<EmployeeDetailPage />} />
      </Routes>,
      { route: '/hr/employees/42' },
    )

    expect(await screen.findByText(/Employee ID:/)).toBeInTheDocument()
    expect(screen.getByText('Pham Thi C')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Contracts' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Assignments' })).toBeInTheDocument()
    expect(screen.getAllByText('#200').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Area Trainer').length).toBeGreaterThan(0)
  })

  it('shows section-level permission denial when contracts and assignments are unavailable', async () => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.hr.employeeRead],
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/hr/employees/:employeeId" element={<EmployeeDetailPage />} />
      </Routes>,
      { route: '/hr/employees/42' },
    )

    expect(await screen.findByText('You need hr.contract.read to inspect employee contracts.')).toBeInTheDocument()
    expect(screen.getByText('You need hr.shift.read to inspect employee assignments.')).toBeInTheDocument()
  })
})
