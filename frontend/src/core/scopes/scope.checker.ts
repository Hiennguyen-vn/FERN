import type { FernPrincipal } from '@core/auth/auth.types'
import type { ScopeAccessRequirement } from './scope.types'

export function hasScopeAccess(principal: FernPrincipal | null, requirement: ScopeAccessRequirement): boolean {
  if (!principal) {
    return false
  }

  if (requirement.system) {
    return principal.scopeRoots.system
  }

  if (requirement.regionId !== undefined) {
    return principal.scopeRoots.regions.includes(requirement.regionId)
  }

  if (requirement.outletId !== undefined) {
    return principal.scopeRoots.outlets.includes(requirement.outletId)
  }

  return true
}
