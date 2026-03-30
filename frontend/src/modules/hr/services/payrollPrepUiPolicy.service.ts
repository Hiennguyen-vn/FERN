import type { FernPrincipal } from '@core/auth/auth.types'
import {
  canPreparePayroll,
  canReadPayroll,
  canReadPayrollDetail,
} from './hrPermission.service'

const PAYROLL_TERMINAL_STATUSES = new Set(['APPROVED', 'PAID', 'CANCELLED'])

export const payrollPrepUiPolicy = {
  canOpenPayrollPreparation(principal: FernPrincipal | null) {
    return canReadPayroll(principal) || canPreparePayroll(principal)
  },

  canPrepare(principal: FernPrincipal | null) {
    return canPreparePayroll(principal)
  },

  canReadDetails(principal: FernPrincipal | null) {
    return canReadPayrollDetail(principal)
  },

  isRunReadonly(status: string) {
    return PAYROLL_TERMINAL_STATUSES.has(status.toUpperCase())
  },
}
