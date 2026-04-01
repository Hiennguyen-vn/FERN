export type PosSessionStatus = 'OPEN' | 'CLOSED' | 'RECONCILED' | 'CANCELLED'
export type SaleOrderStatus = 'OPEN' | 'COMPLETING' | 'COMPLETED' | 'CANCELLED' | 'REFUNDED' | 'PARTIALLY_REFUNDED' | 'VOIDED'
export type SaleOrderPaymentStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'REFUNDED'
export type SalePaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'REFUNDED'
export type PosOrderType = 'DINE_IN' | 'TAKEAWAY'
export type PosPaymentMethod = 'CASH' | 'CARD' | 'EWALLET' | 'BANK_TRANSFER' | 'CHEQUE' | 'VOUCHER'
export type PriceScopeType = 'GLOBAL' | 'COUNTRY' | 'REGION' | 'OUTLET'
export type CatalogPriceType = 'RETAIL' | 'DINE_IN' | 'TAKEAWAY' | 'DELIVERY' | 'WHOLESALE'

// Backend serialises Java LocalDate as [year, month, day] array or ISO string
export type BackendDate = number[] | string | null
// Backend serialises Java Instant as epoch seconds (float) or ISO string  
export type BackendInstant = number | string | null

export interface PosSession {
  id: number
  sessionCode: string
  regionId: number
  outletId: number
  terminalId: string | null
  currencyCode: string
  cashierUserId: number | null
  managerUserId: number | null
  businessDate: BackendDate
  status: PosSessionStatus
  note: string | null
  openedAt: BackendInstant
  closedAt: BackendInstant
  reconciledAt: BackendInstant
  expectedCashAmount: number | string | null
  countedCashAmount: number | string | null
  discrepancyAmount: number | string | null
}

export interface SaleOrderLine {
  lineNumber: number
  productId: number
  productCode: string
  productNameSnapshot: string
  unitPrice: string
  qty: string
  discountAmount: string
  taxAmount: string
  lineTotal: string
  note: string | null
}

export interface SalePayment {
  id: number
  paymentMethod: PosPaymentMethod | string
  amount: number | string
  status: SalePaymentStatus | string
  paymentTime: BackendInstant
  transactionRef: string | null
}

export interface SaleOrder {
  id: number
  orderNumber: string
  regionId: number
  outletId: number
  posSessionId: number
  currencyCode: string
  orderType: PosOrderType | string
  status: SaleOrderStatus | string
  paymentStatus: SaleOrderPaymentStatus | string
  subtotal: number | string
  discountAmount: number | string
  taxAmount: number | string
  totalAmount: number | string
  note: string | null
  createdAt: BackendInstant
  completedAt: BackendInstant
  lines: SaleOrderLine[]
  payments: SalePayment[]
}

export interface Product {
  id: number
  code: string
  name: string
  categoryCode: string
  status: string
  imageUrl: string | null
  description: string | null
}

export interface ProductPrice {
  id: number
  productId: number
  scopeType: PriceScopeType | string
  scopeId: number | null
  priceType: CatalogPriceType | string
  currencyCode: string
  priceValue: string
  effectiveFrom: string | null
  effectiveTo: string | null
}

export interface ProductAvailability {
  productId: number
  outletId: number
  available: boolean
}

export interface ResolvedMenuItem {
  productId: number
  code: string
  name: string
  categoryCode: string
  description: string | null
  imageUrl: string | null
  currencyCode: string
  priceValue: string
  priceType: CatalogPriceType | string
  scopeType: PriceScopeType | string
}

export interface OpenPosSessionPayload {
  regionId: number
  outletId: number
  currencyCode: string
  businessDate: string
  note?: string
}

export interface ReconcilePosSessionPayload {
  countedCashAmount: number
  note?: string
}

export interface CreateSaleOrderPayload {
  posSessionId: number
  orderType: PosOrderType
  currencyCode?: string
  note?: string
  lines: Array<{
    productId: number
    qty: number
    note?: string
  }>
}

export interface UpdateSaleOrderPayload {
  note?: string
  lines: Array<{
    productId: number
    qty: number
    note?: string
  }>
}

export interface AddSalePaymentPayload {
  paymentMethod: PosPaymentMethod
  amount: number
  paymentTime?: string
  transactionRef?: string
  note?: string
  status?: 'SUCCESS' | 'FAILED' | 'CANCELLED'
}

export interface OpenSessionResult {
  session: PosSession
  sessionExisted: boolean
}

export interface PosSessionFilters {
  outletId: number
  terminalId?: string | null
  businessDate?: string
  status?: PosSessionStatus | ''
}

export interface PosCatalogFilters {
  businessDate: string
  orderType: PosOrderType
  outletId: number
  regionId?: number
}

export interface CartItemDraft {
  productId: number
  productCode: string
  productName: string
  categoryCode: string
  currencyCode: string
  unitPrice: string
  qty: string
  note: string
}

export interface CartDraft {
  outletId: number
  orderType: PosOrderType
  orderNote: string
  items: CartItemDraft[]
}

export interface OrderLineDraft {
  productId: number
  productCode: string
  productNameSnapshot: string
  qty: string
  note: string
  unitPrice: string
  lineTotal: string
  taxAmount: string
}
