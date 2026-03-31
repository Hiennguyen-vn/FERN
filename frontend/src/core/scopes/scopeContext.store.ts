import { create } from 'zustand'
import type { FernPrincipal } from '@core/auth/auth.types'
import type { ScopeContextState } from './scope.types'

function resolveAccessibleScope(principal: FernPrincipal | null) {
  return principal?.accessibleScope ?? principal?.scopeRoots ?? { system: false, regions: [], outlets: [] }
}

export const useScopeContextStore = create<ScopeContextState>((set) => ({
  selectedOutletId: null,
  selectedRegionId: null,
  outletIds: [],
  regionIds: [],
  hydrateFromPrincipal: (principal) => {
    const accessibleScope = resolveAccessibleScope(principal)

    return set({
      outletIds: accessibleScope.outlets,
      regionIds: accessibleScope.regions,
      selectedOutletId: accessibleScope.outlets[0] ?? null,
      selectedRegionId: accessibleScope.regions[0] ?? null,
    })
  },
  setSelectedOutletId: (selectedOutletId) => set({ selectedOutletId }),
  setSelectedRegionId: (selectedRegionId) => set({ selectedRegionId }),
}))
