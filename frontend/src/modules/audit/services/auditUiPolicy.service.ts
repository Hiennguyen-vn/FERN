import type { FernPrincipal } from '@core/auth/auth.types'
import { hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

export function canReadAudit(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.audit.read)
}

export function canReadAuditDetails(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.audit.detailRead)
}

export function canSeeAuditNavigation(principal: FernPrincipal | null) {
  return canReadAudit(principal)
}
