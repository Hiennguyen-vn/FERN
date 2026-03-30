import type { FernPrincipal } from '@core/auth/auth.types'
import {
  canReadAssignments,
  canReadContracts,
  canReadEmployees,
} from './hrPermission.service'

export const employeeUiPolicy = {
  canOpenEmployeesPage(principal: FernPrincipal | null) {
    return canReadEmployees(principal)
  },

  canOpenEmployeeDetail(principal: FernPrincipal | null) {
    return canReadEmployees(principal)
  },

  canViewContracts(principal: FernPrincipal | null) {
    return canReadContracts(principal)
  },

  canViewAssignments(principal: FernPrincipal | null) {
    return canReadAssignments(principal)
  },
}
