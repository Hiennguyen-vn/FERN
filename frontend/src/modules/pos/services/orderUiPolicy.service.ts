import type { SaleOrder } from '../model/pos.types'
import { calculateOutstandingAmount, getSuccessfulPaymentTotal } from './paymentUiPolicy.service'

export function canEditOrder(order: Pick<SaleOrder, 'status' | 'payments'>) {
  return String(order.status).toUpperCase() === 'OPEN' && getSuccessfulPaymentTotal(order as Pick<SaleOrder, 'payments'>) === 0
}

export function canUpdateOrder(order: Pick<SaleOrder, 'status' | 'payments'>, isOnline: boolean) {
  return canEditOrder(order) && isOnline
}

export function canCancelOrder(order: Pick<SaleOrder, 'status' | 'payments'>, isOnline: boolean) {
  return canEditOrder(order) && isOnline
}

export function canCompleteOrder(order: Pick<SaleOrder, 'status' | 'payments' | 'totalAmount'>, isOnline: boolean) {
  return String(order.status).toUpperCase() === 'OPEN' && calculateOutstandingAmount(order) <= 0 && isOnline
}

export function isOrderReadonly(order: Pick<SaleOrder, 'status' | 'payments'>) {
  return !canEditOrder(order)
}
