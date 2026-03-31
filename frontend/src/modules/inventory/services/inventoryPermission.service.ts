import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const inventoryNavigationPermissions = [
  permissionConstants.inventory.balanceRead,
  permissionConstants.inventory.ledgerRead,
]

export function canReadStockBalances(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.balanceRead)
}

export function canReadInventoryLedger(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.ledgerRead)
}

export function canSeeInventoryNavigation(principal: FernPrincipal | null): boolean {
  return hasAnyPermissions(principal, inventoryNavigationPermissions)
}
