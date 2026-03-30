import type { FernPrincipal } from '@core/auth/auth.types'

export interface ScopeAccessRequirement {
  system?: boolean
  regionId?: number
  outletId?: number
}

export interface ScopeContextState {
  selectedOutletId: number | null
  selectedRegionId: number | null
  outletIds: number[]
  regionIds: number[]
  hydrateFromPrincipal: (principal: FernPrincipal | null) => void
  setSelectedOutletId: (outletId: number | null) => void
  setSelectedRegionId: (regionId: number | null) => void
}
