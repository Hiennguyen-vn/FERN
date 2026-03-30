import { useEffect } from 'react'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContextStore } from './scopeContext.store'

export function useScopeContext() {
  const principal = usePrincipal()
  const selectedOutletId = useScopeContextStore((state) => state.selectedOutletId)
  const selectedRegionId = useScopeContextStore((state) => state.selectedRegionId)
  const outletIds = useScopeContextStore((state) => state.outletIds)
  const regionIds = useScopeContextStore((state) => state.regionIds)
  const hydrateFromPrincipal = useScopeContextStore((state) => state.hydrateFromPrincipal)
  const setSelectedOutletId = useScopeContextStore((state) => state.setSelectedOutletId)
  const setSelectedRegionId = useScopeContextStore((state) => state.setSelectedRegionId)

  useEffect(() => {
    hydrateFromPrincipal(principal)
  }, [hydrateFromPrincipal, principal])

  return {
    selectedOutletId,
    selectedRegionId,
    outletIds,
    regionIds,
    setSelectedOutletId,
    setSelectedRegionId,
  }
}
