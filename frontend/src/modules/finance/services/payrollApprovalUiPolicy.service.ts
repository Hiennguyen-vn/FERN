import type { FernPrincipal } from '@core/auth/auth.types'
import {
  canApprovePayroll,
  canPayPayroll,
  canPreparePayroll,
  canReadPayroll,
  canReadPayrollDetail,
} from './financePermission.service'

const TERMINAL_STATUSES = new Set(['PAID', 'CANCELLED'])

export const payrollApprovalUiPolicy = {
  canOpenPayrollQueue(principal: FernPrincipal | null) {
    return canReadPayroll(principal) || canApprovePayroll(principal) || canPayPayroll(principal)
  },

  canOpenPayrollDetail(principal: FernPrincipal | null) {
    return canReadPayroll(principal)
  },

  canOpenPayrollPaid(principal: FernPrincipal | null) {
    return canReadPayroll(principal) || canPayPayroll(principal)
  },

  canReadDetails(principal: FernPrincipal | null) {
    return canReadPayrollDetail(principal)
  },

  canApprove(principal: FernPrincipal | null, status: string) {
    return canApprovePayroll(principal) && status.toUpperCase() === 'SUBMITTED'
  },

  canReject(principal: FernPrincipal | null, status: string) {
    return canApprovePayroll(principal) && status.toUpperCase() === 'SUBMITTED'
  },

  canCancel(principal: FernPrincipal | null, status: string) {
    const normalized = status.toUpperCase()
    return canPreparePayroll(principal) && (normalized === 'DRAFT' || normalized === 'REJECTED')
  },

  canMarkPaid(principal: FernPrincipal | null, status: string) {
    return canPayPayroll(principal) && status.toUpperCase() === 'APPROVED'
  },

  isTerminal(status: string) {
    return TERMINAL_STATUSES.has(status.toUpperCase())
  },

  isDecisionReadonly(status: string) {
    return status.toUpperCase() !== 'SUBMITTED'
  },
}
