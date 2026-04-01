import type { FernPrincipal } from '@core/auth/auth.types'
import { canViewContractSensitiveFields } from '@core/permissions/fieldAccess.checker'
import { canReadContracts } from './hrPermission.service'

const TERMINAL_CONTRACT_STATUSES = new Set(['EXPIRED', 'TERMINATED', 'CANCELLED', 'INACTIVE'])

export const contractUiPolicy = {
  canOpenContractsPage(principal: FernPrincipal | null) {
    return canReadContracts(principal)
  },

  canViewSensitiveFields(principal: FernPrincipal | null) {
    // Delegates to fieldAccess.checker — single source of truth for hr.contract.detail.read.
    return canViewContractSensitiveFields(principal)
  },

  isTerminal(status: string) {
    return TERMINAL_CONTRACT_STATUSES.has(status.toUpperCase())
  },
}
