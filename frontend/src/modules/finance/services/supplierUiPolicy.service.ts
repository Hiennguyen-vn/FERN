import type { FernPrincipal } from '@core/auth/auth.types'
import {
  canReadPaymentRequests,
  canReadSuppliers,
} from './financePermission.service'

export const supplierUiPolicy = {
  canOpenSuppliersPage(principal: FernPrincipal | null) {
    return canReadSuppliers(principal)
  },

  canOpenSupplierDetail(principal: FernPrincipal | null) {
    return canReadSuppliers(principal)
  },

  canOpenPaymentRequestsPage(principal: FernPrincipal | null) {
    return canReadPaymentRequests(principal)
  },

  canOpenSupplierLinkedPaymentRequests(principal: FernPrincipal | null) {
    return canReadPaymentRequests(principal)
  },

  isReadonly() {
    return true
  },
}
