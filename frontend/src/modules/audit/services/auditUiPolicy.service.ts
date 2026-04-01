import type { FernPrincipal } from '@core/auth/auth.types'
import { hasPermission } from '@core/permissions/permission.checker'
import { canViewAuditEventDetail } from '@core/permissions/fieldAccess.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

export function canReadAudit(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.audit.read)
}

export function canReadAuditDetails(principal: FernPrincipal | null) {
  // Delegates to fieldAccess.checker — single source of truth for audit.detail.read.
  return canViewAuditEventDetail(principal)
}

export function canSeeAuditNavigation(principal: FernPrincipal | null) {
  return canReadAudit(principal)
}
