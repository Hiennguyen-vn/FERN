import type { FinancePaymentRequest, RecentFinancePaymentRequestLookup } from '../model/finance.types'

const STORAGE_KEY = 'finance.recent-payment-requests'
const MAX_ITEMS = 12

function readStorage() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return []
  }

  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }

  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as RecentFinancePaymentRequestLookup[]) : []
  } catch {
    return []
  }
}

export function loadRecentFinancePaymentRequests() {
  return readStorage()
}

export function saveRecentFinancePaymentRequest(
  paymentRequest: FinancePaymentRequest,
  supplierContext: { supplierCode?: string | null; supplierName?: string | null } = {},
) {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }

  const items = readStorage().filter((item) => item.paymentRequest.id !== paymentRequest.id)
  items.unshift({
    paymentRequest,
    supplierCode: supplierContext.supplierCode,
    supplierName: supplierContext.supplierName,
  })
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_ITEMS)))
}

export function clearRecentFinancePaymentRequests() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }

  window.localStorage.removeItem(STORAGE_KEY)
}
