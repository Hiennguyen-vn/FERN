import type { FernPrincipal } from '@core/auth/auth.types'
import { canReadOutlets, canReadRegions, canWriteOutlets } from './orgPermission.service'

export const orgUiPolicy = {
  canOpenRegionsPage(principal: FernPrincipal | null) {
    return canReadRegions(principal)
  },

  canOpenRegionDetail(principal: FernPrincipal | null) {
    return canReadRegions(principal)
  },

  canOpenOutletsPage(principal: FernPrincipal | null) {
    return canReadOutlets(principal)
  },

  canOpenOutletDetail(principal: FernPrincipal | null) {
    return canReadOutlets(principal)
  },

  canOpenOutletCreate(principal: FernPrincipal | null) {
    return canWriteOutlets(principal)
  },
}
