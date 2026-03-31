import type { FernPrincipal } from '@core/auth/auth.types'
import type { ScopeAccessRequirement } from './scope.types'

function resolveAccessibleScope(principal: FernPrincipal) {
  return principal.accessibleScope ?? principal.scopeRoots
}

export function hasScopeAccess(principal: FernPrincipal | null, requirement: ScopeAccessRequirement): boolean {
  if (!principal) {
    return false
  }

  const accessibleScope = resolveAccessibleScope(principal)

  if (requirement.system) {
    return accessibleScope.system
  }

  // System-scope principals have access to all regions and outlets.
  if (requirement.regionId !== undefined) {
    return accessibleScope.system || accessibleScope.regions.includes(requirement.regionId)
  }

  if (requirement.outletId !== undefined) {
    return accessibleScope.system || accessibleScope.outlets.includes(requirement.outletId)
  }

  return true
}
