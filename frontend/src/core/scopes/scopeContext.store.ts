import { create } from 'zustand'
import type { ScopeContextState } from './scope.types'

export const useScopeContextStore = create<ScopeContextState>((set) => ({
  selectedOutletId: null,
  selectedRegionId: null,
  outletIds: [],
  regionIds: [],
  hydrateFromPrincipal: (principal) =>
    set({
      outletIds: principal?.scopeRoots.outlets ?? [],
      regionIds: principal?.scopeRoots.regions ?? [],
      selectedOutletId: principal?.scopeRoots.outlets[0] ?? null,
      selectedRegionId: principal?.scopeRoots.regions[0] ?? null,
    }),
  setSelectedOutletId: (selectedOutletId) => set({ selectedOutletId }),
  setSelectedRegionId: (selectedRegionId) => set({ selectedRegionId }),
}))
