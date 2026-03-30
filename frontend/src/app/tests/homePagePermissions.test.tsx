import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { HomePage } from '@app/router/index'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'

describe('HomePage permissions', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('shows only HR, Finance, and Workforce module surfaces for an HR principal with payroll permissions', async () => {
    setAuthenticatedSession({
      principal: {
        username: 'hr-operator',
        roles: ['hr'],
        permissions: [
          permissionConstants.hr.employeeRead,
          permissionConstants.hr.contractRead,
          permissionConstants.hr.shiftRead,
          permissionConstants.hr.attendanceWrite,
          permissionConstants.hr.attendanceReview,
          permissionConstants.finance.payrollRead,
          permissionConstants.finance.payrollPrepare,
          permissionConstants.finance.payrollDetailRead,
          permissionConstants.hr.contractDetailRead,
        ],
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [],
        },
      },
      user: {
        username: 'hr-operator',
        roleCodes: ['hr'],
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [],
        },
      },
    })

    renderWithProviders(<HomePage />, { route: '/home' })

    expect(screen.getByText('HR')).toBeInTheDocument()
    expect(screen.getByText('Finance')).toBeInTheDocument()
    expect(screen.getByText('Workforce')).toBeInTheDocument()
    expect(screen.getByText('Prepare payroll draft')).toBeInTheDocument()
    expect(screen.getByText('Review attendance')).toBeInTheDocument()
    expect(screen.getByText('Review payroll approvals')).toBeInTheDocument()
    expect(screen.getByText('Browse suppliers')).toBeInTheDocument()

    expect(screen.queryByText('POS')).not.toBeInTheDocument()
    expect(screen.queryByText('Catalog')).not.toBeInTheDocument()
    expect(screen.queryByText('IAM')).not.toBeInTheDocument()
    expect(screen.queryByText('Procurement')).not.toBeInTheDocument()
    expect(screen.queryByText('Inventory')).not.toBeInTheDocument()
    expect(screen.queryByText('Reports')).not.toBeInTheDocument()
    expect(screen.queryByText('Open POS workspace')).not.toBeInTheDocument()
    expect(screen.queryByText('Create purchase order')).not.toBeInTheDocument()
    expect(screen.queryByText('Check stock balances')).not.toBeInTheDocument()
    expect(screen.queryByText('Queue export job')).not.toBeInTheDocument()
    expect(screen.queryByText('Open IAM console')).not.toBeInTheDocument()
  })
})
