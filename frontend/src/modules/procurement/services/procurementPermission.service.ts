import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const procurementNavigationPermissions = [
  permissionConstants.procurement.supplierRead,
  permissionConstants.procurement.purchaseOrderRead,
  permissionConstants.procurement.goodsReceiptRead,
  permissionConstants.procurement.invoiceRead,
  permissionConstants.procurement.paymentRead,
]

export function canReadPurchaseOrders(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.purchaseOrderRead)
}

export function canCreatePurchaseOrder(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.purchaseOrderCreate)
}

export function canReadGoodsReceipts(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.goodsReceiptRead)
}

export function canCreateGoodsReceipt(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.goodsReceiptCreate)
}

export function canReadSuppliers(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.supplierRead)
}

export function canReadInvoices(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.invoiceRead)
}

export function canReadPayments(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.paymentRead)
}

export function canReviewInvoice(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.invoiceReview)
}

export function canApproveInvoice(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.invoiceApprove)
}

export function canDisputeInvoice(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.invoiceDispute)
}

export function canRecordPayment(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.procurement.paymentRecord)
}

export function canSeeProcurementNavigation(principal: FernPrincipal | null): boolean {
  return hasAnyPermissions(principal, procurementNavigationPermissions)
}
