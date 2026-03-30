import type { FernPrincipal } from '@core/auth/auth.types'
import type { PermissionCode } from './permission.types'

export function hasPermission(principal: FernPrincipal | null, permission: PermissionCode): boolean {
  return Boolean(principal?.permissions.includes(permission))
}

export function hasAllPermissions(principal: FernPrincipal | null, permissions: PermissionCode[]): boolean {
  if (permissions.length === 0) {
    return true
  }

  return permissions.every((permission) => hasPermission(principal, permission))
}

export function hasAnyPermissions(principal: FernPrincipal | null, permissions: PermissionCode[]): boolean {
  if (permissions.length === 0) {
    return true
  }

  return permissions.some((permission) => hasPermission(principal, permission))
}
