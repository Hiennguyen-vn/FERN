import { describe, expect, it } from 'vitest'
import type { FernPrincipal } from '@core/auth/auth.types'
import { permissionConstants } from '@core/permissions/permission.constants'
import { buildNavigation } from './navigation.builder'

function createPrincipal(permissions: string[]): FernPrincipal {
  return {
    userId: 26,
    username: 'hr-operator',
    displayName: 'HR Operator',
    roles: ['hr'],
    permissions,
    scopeRoots: {
      system: false,
      regions: [1],
      outlets: [],
    },
    policyVersion: 1,
    scopeVersion: 1,
  }
}

describe('buildNavigation', () => {
  it('shows only modules allowed for an HR principal', () => {
    const principal = createPrincipal([
      permissionConstants.hr.employeeRead,
      permissionConstants.hr.contractRead,
      permissionConstants.hr.shiftRead,
      permissionConstants.hr.attendanceWrite,
      permissionConstants.hr.attendanceReview,
      permissionConstants.finance.payrollRead,
      permissionConstants.finance.payrollPrepare,
      permissionConstants.finance.payrollDetailRead,
      permissionConstants.hr.contractDetailRead,
    ])

    expect(buildNavigation(principal)).toEqual([
      { label: 'Home', to: '/home' },
      { label: 'HR', to: '/hr/employees', visible: expect.any(Function) },
      { label: 'Finance', to: '/finance/payroll-approvals', visible: expect.any(Function) },
      { label: 'Workforce', to: '/workforce/my-attendance', visible: expect.any(Function) },
    ])
  })

  it('hides module entries when principal lacks matching permissions', () => {
    const principal = createPrincipal([])

    expect(buildNavigation(principal)).toEqual([{ label: 'Home', to: '/home' }])
  })
})
