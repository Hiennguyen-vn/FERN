import { hasPermission } from '@core/permissions/permission.checker'
import type { FernPrincipal } from '@core/auth/auth.types'
import type { PosSession } from '../model/pos.types'

export const POS_PERMISSIONS = {
  catalogPriceRead: 'catalog.price.read',
  catalogProductRead: 'catalog.product.read',
  orderCancel: 'pos.order.cancel',
  orderComplete: 'pos.order.complete',
  orderCreate: 'pos.order.create',
  orderRead: 'pos.order.read',
  orderUpdate: 'pos.order.update',
  sessionClose: 'pos.session.close',
  sessionOpen: 'pos.session.open',
  sessionRead: 'pos.session.read',
  sessionReconcile: 'pos.session.reconcile',
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
