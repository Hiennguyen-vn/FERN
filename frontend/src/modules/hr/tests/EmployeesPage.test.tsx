import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { EmployeesPage } from '../routes/EmployeesPage'
import { saveRecentHrEmployee } from '../services/recentHrLookups.service'

const mocks = vi.hoisted(() => ({
  useHrEmployee: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  useHrEmployee: mocks.useHrEmployee,
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
    mocks.useHrEmployee.mockReturnValue({
      data: undefined,
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders recent employees from local history', () => {
    saveRecentHrEmployee({
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
    })

    renderWithProviders(<EmployeesPage />)

    expect(screen.getByText('EMP-101')).toBeInTheDocument()
    expect(screen.getByText('Tran Thi B')).toBeInTheDocument()
  })

  it('shows permission denied without hr.employee.read', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(<EmployeesPage />)

    expect(screen.getByText('Bạn cần quyền hr.employee.read để tra cứu và inspect hồ sơ nhân viên.')).toBeInTheDocument()
  })
})
