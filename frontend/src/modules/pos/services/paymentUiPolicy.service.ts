import type { SaleOrder, SalePayment } from '../model/pos.types'

export const posPaymentStatusOptions = [
  { label: 'Success', value: 'SUCCESS' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Cancelled', value: 'CANCELLED' },
] as const

export const posPaymentMethodOptions = [
  { label: 'Cash', value: 'CASH' },
  { label: 'Card', value: 'CARD' },
  { label: 'E-wallet', value: 'EWALLET' },
  { label: 'Bank transfer', value: 'BANK_TRANSFER' },
  { label: 'Cheque', value: 'CHEQUE' },
  { label: 'Voucher', value: 'VOUCHER' },
] as const

export function isSuccessfulPayment(payment: Pick<SalePayment, 'status'>) {
  return String(payment.status).toUpperCase() === 'SUCCESS'
}

export function getSuccessfulPaymentTotal(order: Pick<SaleOrder, 'payments'>) {
  return order.payments.reduce((total, payment) => total + (isSuccessfulPayment(payment) ? Number(payment.amount) : 0), 0)
}

export function calculateOutstandingAmount(order: Pick<SaleOrder, 'payments' | 'totalAmount'>) {
  return Math.max(Number(order.totalAmount) - getSuccessfulPaymentTotal(order as Pick<SaleOrder, 'payments'>), 0)
}

export function canAddPayment(order: Pick<SaleOrder, 'status'>, _isOnline: boolean) {
  return String(order.status).toUpperCase() === 'OPEN'
}
