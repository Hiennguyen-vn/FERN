import type { FernPrincipal, ScopeRoots } from '@core/auth/auth.types'

/**
 * Permission mapping utilities.
 *
 * These convert backend permission/scope representations into
 * forms useful for UI decision-making. They do not grant or deny
 * permissions — they only interpret what is already present.
 */

/**
 * Returns the effective scope roots for a principal.
 * Prefers accessibleScope (delegate/subtree override) over scopeRoots.
 */
export function getEffectiveScope(principal: FernPrincipal | null): ScopeRoots | null {
  if (!principal) {
    return null
  }

  return principal.accessibleScope ?? principal.scopeRoots
}

/**
 * Returns true if the principal has system-wide scope access.
 */
export function hasSystemScope(principal: FernPrincipal | null): boolean {
  return Boolean(getEffectiveScope(principal)?.system)
}

/**
 * Returns true if the principal has access to the given regionId
 * (either directly or via system scope).
 */
export function hasRegionAccess(principal: FernPrincipal | null, regionId: number): boolean {
  const scope = getEffectiveScope(principal)

  if (!scope) {
    return false
  }

  return scope.system || scope.regions.includes(regionId)
}

/**
 * Returns true if the principal has access to the given outletId
 * (either directly or via system/region scope — outlets are a leaf scope).
 */
export function hasOutletAccess(principal: FernPrincipal | null, outletId: number): boolean {
  const scope = getEffectiveScope(principal)

  if (!scope) {
    return false
  }

  return scope.system || scope.outlets.includes(outletId)
}

/**
 * Returns all region IDs the principal has direct access to.
 * For system-scope principals, returns an empty array (they access all).
 */
export function getAccessibleRegionIds(principal: FernPrincipal | null): number[] {
  return getEffectiveScope(principal)?.regions ?? []
}

/**
 * Returns all outlet IDs the principal has direct access to.
 * For system-scope principals, returns an empty array (they access all).
 */
export function getAccessibleOutletIds(principal: FernPrincipal | null): number[] {
  return getEffectiveScope(principal)?.outlets ?? []
}
