import type { FernPrincipal } from '@core/auth/auth.types'
import { canReadContractDetail, canReadContracts } from './hrPermission.service'

const TERMINAL_CONTRACT_STATUSES = new Set(['EXPIRED', 'TERMINATED', 'CANCELLED', 'INACTIVE'])

export const contractUiPolicy = {
  canOpenContractsPage(principal: FernPrincipal | null) {
    return canReadContracts(principal)
  },

  canViewSensitiveFields(principal: FernPrincipal | null) {
    return canReadContractDetail(principal)
  },

  isTerminal(status: string) {
    return TERMINAL_CONTRACT_STATUSES.has(status.toUpperCase())
  },
}
