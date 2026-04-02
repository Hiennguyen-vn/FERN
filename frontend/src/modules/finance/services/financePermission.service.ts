import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const financeNavigationPermissions = [
  permissionConstants.procurement.supplierRead,
  permissionConstants.procurement.supplierWrite,
  permissionConstants.procurement.invoiceRead,
  permissionConstants.procurement.invoiceReview,
  permissionConstants.procurement.invoiceApprove,
  permissionConstants.procurement.invoiceDispute,
  permissionConstants.procurement.paymentRead,
  permissionConstants.procurement.paymentRecord,
  permissionConstants.finance.payrollRead,
  permissionConstants.finance.payrollPrepare,
  permissionConstants.finance.payrollApprove,
  permissionConstants.finance.payrollPay,
  permissionConstants.finance.configRead,
  permissionConstants.finance.configWrite,
]

const paymentRequestPermissions = [
  permissionConstants.procurement.invoiceRead,
  permissionConstants.procurement.invoiceReview,
  permissionConstants.procurement.invoiceApprove,
  permissionConstants.procurement.invoiceDispute,
  permissionConstants.procurement.paymentRead,
  permissionConstants.procurement.paymentRecord,
]

export function canReadSuppliers(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.procurement.supplierRead)
}

export function canReadPaymentRequests(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, paymentRequestPermissions)
}

export function canReadPayroll(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollRead)
}

export function canReadPayrollDetail(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollDetailRead)
}

export function canApprovePayroll(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollApprove)
}

export function canPreparePayroll(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollPrepare)
}

export function canPayPayroll(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollPay)
}

export function canReadFinanceConfig(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.configRead)
}

export function canWriteFinanceConfig(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.configWrite)
}

export function canSeeFinanceNavigation(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, financeNavigationPermissions)
}
