import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const catalogReadPermissions = [
  permissionConstants.catalog.productRead,
  permissionConstants.catalog.ingredientRead,
  permissionConstants.catalog.recipeRead,
  permissionConstants.catalog.priceRead,
]

export function canReadProducts(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.productRead)
}

export function canReadIngredients(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.ingredientRead)
}

export function canReadRecipes(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.recipeRead)
}

export function canReadPrices(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.priceRead)
}

export function canSeeCatalogNavigation(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, catalogReadPermissions)
}
