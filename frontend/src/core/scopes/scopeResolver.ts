import type { FernPrincipal } from '@core/auth/auth.types'

export function resolveDefaultOutlet(principal: FernPrincipal | null): number | null {
  return principal?.accessibleScope?.outlets[0] ?? principal?.scopeRoots.outlets[0] ?? null
}
