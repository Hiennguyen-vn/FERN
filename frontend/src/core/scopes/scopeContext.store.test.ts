import { beforeEach, describe, expect, it } from 'vitest'
import type { FernPrincipal } from '@core/auth/auth.types'
import { useScopeContextStore } from './scopeContext.store'

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

describe('scopeContext.store', () => {
  beforeEach(() => {
    useScopeContextStore.setState({
      selectedOutletId: null,
      selectedRegionId: null,
      outletIds: [],
      regionIds: [],
    })
  })

  it('hydrates from expanded accessible scope for region-subtree users', () => {
    useScopeContextStore.getState().hydrateFromPrincipal(principal())

    expect(useScopeContextStore.getState()).toMatchObject({
      outletIds: [101, 102],
      regionIds: [10, 11],
      selectedOutletId: 101,
      selectedRegionId: 10,
    })
  })
})
