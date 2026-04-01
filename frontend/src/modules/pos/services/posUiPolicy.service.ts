import { hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'
import type { FernPrincipal } from '@core/auth/auth.types'
import type { PosSession } from '../model/pos.types'

/** @deprecated Use permissionConstants.pos directly. */
export const POS_PERMISSIONS = {
  catalogPriceRead: permissionConstants.catalog.priceRead,
  catalogProductRead: permissionConstants.catalog.productRead,
  orderCancel: permissionConstants.pos.orderCancel,
  orderComplete: permissionConstants.pos.orderComplete,
  orderCreate: permissionConstants.pos.orderCreate,
  orderRead: permissionConstants.pos.orderRead,
  orderUpdate: permissionConstants.pos.orderUpdate,
  sessionClose: permissionConstants.pos.sessionClose,
  sessionOpen: permissionConstants.pos.sessionOpen,
  sessionRead: permissionConstants.pos.sessionRead,
  sessionReconcile: permissionConstants.pos.sessionReconcile,
} as const

export function canReadCatalog(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.catalogProductRead) && hasPermission(principal, POS_PERMISSIONS.catalogPriceRead)
}

export function canReadSessions(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.sessionRead)
}

export function canOpenSession(principal: FernPrincipal | null, isOnline: boolean) {
  return hasPermission(principal, POS_PERMISSIONS.sessionOpen) && isOnline
}

export function canReadOrders(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.orderRead)
}

export function canCreateOrderFromCart(
  principal: FernPrincipal | null,
  session: Pick<PosSession, 'status'> | null,
  itemCount: number,
  isOnline: boolean,
) {
  return hasPermission(principal, POS_PERMISSIONS.orderCreate) && String(session?.status).toUpperCase() === 'OPEN' && itemCount > 0 && isOnline
}

export function canManageCatalog(
  principal: FernPrincipal | null,
  session: Pick<PosSession, 'status'> | null,
  isOnline: boolean,
) {
  return canReadCatalog(principal) && canCreateOrderFromCart(principal, session, 1, isOnline)
}

export function canManageOrderUpdates(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.orderUpdate)
}

export function canCancelOrderAction(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.orderCancel)
}

export function canCompleteOrderAction(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.orderComplete)
}

export function canCloseSessionAction(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.sessionClose)
}

export function canReconcileSessionAction(principal: FernPrincipal | null) {
  return hasPermission(principal, POS_PERMISSIONS.sessionReconcile)
}
