import { describe, expect, it } from 'vitest'
import type { FernPrincipal } from '@core/auth/auth.types'
import { hasScopeAccess } from './scope.checker'

function principal(overrides: Partial<FernPrincipal> = {}): FernPrincipal {
  return {
    userId: 1,
    username: 'regional-manager',
    roles: ['REGIONAL_MANAGER'],
    permissions: [],
    scopeRoots: { system: false, regions: [10], outlets: [] },
    accessibleScope: { system: false, regions: [10, 11], outlets: [101, 102] },
    policyVersion: 1,
    scopeVersion: 1,
    ...overrides,
  }
}

describe('hasScopeAccess', () => {
  it('uses expanded accessible scope for descendant outlet checks', () => {
    expect(hasScopeAccess(principal(), { outletId: 102 })).toBe(true)
  })

  it('keeps outlet-only users outlet-limited', () => {
    expect(
      hasScopeAccess(
        principal({
          scopeRoots: { system: false, regions: [], outlets: [101] },
          accessibleScope: { system: false, regions: [], outlets: [101] },
        }),
        { outletId: 102 },
      ),
    ).toBe(false)
  })
})
