import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { EmployeesPage } from '../routes/EmployeesPage'

const mocks = vi.hoisted(() => ({
  useHrEmployees: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  useHrEmployees: mocks.useHrEmployees,
}))

describe('EmployeesPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.hr.employeeRead],
      },
    })
    mocks.useHrEmployees.mockReturnValue({
      data: {
        items: [
          {
            id: 101,
            employeeCode: 'EMP-101',
            fullName: 'Tran Thi B',
            dob: '1995-05-12',
            gender: 'FEMALE',
            email: 'b@fern.local',
            phone: '0912',
            status: 'ACTIVE',
            hiredAt: '2025-02-01',
            userAccountId: null,
          },
        ],
        page: 0,
        size: 50,
        hasMore: false,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders employees from the browse query', () => {
    renderWithProviders(<EmployeesPage />)

    expect(screen.getByText('EMP-101')).toBeInTheDocument()
    expect(screen.getByText('Tran Thi B')).toBeInTheDocument()
  })

  it('shows permission denied without hr.employee.read', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(<EmployeesPage />)

    expect(screen.getByText('You need hr.employee.read to open the employee master workspace.')).toBeInTheDocument()
  })
})
