import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'
import type { IamRoleStatus, IamUserStatus } from '../model/iam.types'

const iamReadPermissions = [
  permissionConstants.iam.userRead,
  permissionConstants.iam.roleRead,
  permissionConstants.iam.permissionRead,
  permissionConstants.iam.permissionOverrideRead,
]

export const iamUiPolicy = {
  canReadUsers(principal: FernPrincipal | null) {
    return hasPermission(principal, permissionConstants.iam.userRead)
  },

  canReadRoles(principal: FernPrincipal | null) {
    return hasPermission(principal, permissionConstants.iam.roleRead)
  },

  canReadPermissions(principal: FernPrincipal | null) {
    return hasPermission(principal, permissionConstants.iam.permissionRead)
  },

  canReadPermissionOverrides(principal: FernPrincipal | null) {
    return hasPermission(principal, permissionConstants.iam.permissionOverrideRead)
  },

  canSeeIamNavigation(principal: FernPrincipal | null) {
    return hasAnyPermissions(principal, iamReadPermissions)
  },

  canOpenAssignmentsPage(principal: FernPrincipal | null) {
    return hasAnyPermissions(principal, [
      permissionConstants.iam.userRead,
      permissionConstants.iam.roleRead,
      permissionConstants.iam.permissionRead,
      permissionConstants.iam.permissionOverrideRead,
    ])
  },

  canOpenEffectiveAccess(principal: FernPrincipal | null) {
    return hasPermission(principal, permissionConstants.iam.userRead)
  },

  userStatusTone(status: IamUserStatus) {
    switch (status) {
      case 'ACTIVE':
        return 'badge badge-success'
      case 'LOCKED':
      case 'SUSPENDED':
        return 'badge badge-danger'
      case 'INACTIVE':
      default:
        return 'badge badge-neutral'
    }
  },

  roleStatusTone(status: IamRoleStatus) {
    return status === 'ACTIVE' ? 'badge badge-success' : 'badge badge-neutral'
  },
}
