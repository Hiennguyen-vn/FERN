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

  if (requirement.regionId !== undefined) {
    return accessibleScope.regions.includes(requirement.regionId)
  }

  if (requirement.outletId !== undefined) {
    return accessibleScope.outlets.includes(requirement.outletId)
  }

  return true
}
