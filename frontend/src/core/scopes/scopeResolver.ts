import type { FernPrincipal } from '@core/auth/auth.types'

export function resolveDefaultOutlet(principal: FernPrincipal | null): number | null {
  return principal?.scopeRoots.outlets[0] ?? null
}
