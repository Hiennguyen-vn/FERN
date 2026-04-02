import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const inventoryNavigationPermissions = [
  permissionConstants.inventory.balanceRead,
  permissionConstants.inventory.ledgerRead,
  permissionConstants.inventory.adjustmentWrite,
  permissionConstants.inventory.wasteWrite,
  permissionConstants.inventory.stockCountWrite,
  permissionConstants.inventory.stockCountPost,
]

export function canReadStockBalances(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.balanceRead)
}

export function canReadInventoryLedger(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.ledgerRead)
}

export function canCreateStockAdjustments(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.adjustmentWrite)
}

export function canCreateWasteRecords(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.wasteWrite)
}

export function canCreateStockCountSessions(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.stockCountWrite)
}

export function canPostStockCountSessions(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.inventory.stockCountPost)
}

export function canSeeInventoryNavigation(principal: FernPrincipal | null): boolean {
  return hasAnyPermissions(principal, inventoryNavigationPermissions)
}
