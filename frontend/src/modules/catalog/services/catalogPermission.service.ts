import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const catalogReadPermissions = [
  permissionConstants.catalog.productRead,
  permissionConstants.catalog.ingredientRead,
  permissionConstants.catalog.recipeRead,
  permissionConstants.catalog.priceRead,
  permissionConstants.catalog.promotionRead,
]

const catalogWritePermissions = [
  permissionConstants.catalog.productWrite,
  permissionConstants.catalog.ingredientWrite,
  permissionConstants.catalog.recipeWrite,
  permissionConstants.catalog.priceWrite,
  permissionConstants.catalog.promotionWrite,
]

export function canReadProducts(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.productRead)
}

export function canWriteProducts(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.productWrite)
}

export function canReadIngredients(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.ingredientRead)
}

export function canWriteIngredients(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.ingredientWrite)
}

export function canReadRecipes(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.recipeRead)
}

export function canWriteRecipes(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.recipeWrite)
}

export function canReadPrices(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.priceRead)
}

export function canWritePrices(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.priceWrite)
}

export function canReadPromotions(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.promotionRead)
}

export function canWritePromotions(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.catalog.promotionWrite)
}

export function canSeeCatalogNavigation(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, [...catalogReadPermissions, ...catalogWritePermissions])
}
