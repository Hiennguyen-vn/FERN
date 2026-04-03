export interface Supplier {
  id: number
  supplierCode: string
  name: string
  taxCode: string | null
  email: string | null
  phone: string | null
  address: string | null
  defaultRegionId: number | null
  status: string
  approvedAt: string | null
}

export interface PurchaseOrderLine {
  id: number
  lineNumber: number
  ingredientId: number
  uomCode: string
  qtyOrdered: number
  qtyReceived: number
  expectedUnitPrice: number | null
  taxPercent: number | null
  status: string
  note: string | null
}

export interface PurchaseOrder {
  id: number
  poNumber: string
  regionId: number
  outletId: number
  supplierId: number
  orderDate: string
  expectedDeliveryDate: string | null
  status: string
  subtotalAmount: number
  taxAmount: number
  totalAmount: number
  note: string | null
  approvedAt: string | null
  issuedAt: string | null
  lines: PurchaseOrderLine[]
}

export interface GoodsReceiptLine {
  id: number
  purchaseOrderLineId: number | null
  ingredientId: number
  uomCode: string
  qtyReceived: number
  unitCost: number
  lineTotal: number
  note: string | null
}

export interface GoodsReceipt {
  id: number
  receiptNumber: string
  purchaseOrderId: number
  regionId: number
  outletId: number
  supplierId: number
  receiptTime: string
  businessDate: string
  status: string
  totalAmount: number
  supplierLotNumber: string | null
  note: string | null
  receivedAt: string | null
  postedAt: string | null
  lines: GoodsReceiptLine[]
}

export type SupplierStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'

export interface SupplierUpsertPayload {
  supplierCode: string
  name: string
  taxCode?: string | null
  email?: string | null
  phone?: string | null
  address?: string | null
  defaultRegionId?: number | null
  status?: SupplierStatus
}

export interface CreatePurchaseOrderPayload {
  regionId: number
  outletId: number
  supplierId: number
  orderDate: string
  expectedDeliveryDate?: string
  note?: string
  lines: Array<{
    ingredientId: number
    uomCode: string
    qtyOrdered: number
    expectedUnitPrice?: number
    taxPercent?: number
    note?: string
  }>
}

export interface UpdatePurchaseOrderPayload {
  expectedDeliveryDate?: string
  note?: string
  lines: Array<{
    ingredientId: number
    uomCode: string
    qtyOrdered: number
    expectedUnitPrice?: number
    taxPercent?: number
    note?: string
  }>
}

export interface CreateGoodsReceiptPayload {
  purchaseOrderId: number
  receiptTime: string
  businessDate: string
  supplierLotNumber?: string
  note?: string
  lines: Array<{
    purchaseOrderLineId?: number
    ingredientId: number
    uomCode: string
    qtyReceived: number
    unitCost: number
    manufactureDate?: string
    expiryDate?: string
    note?: string
  }>
}

// ─── Supplier invoice ─────────────────────────────────────────────────────────
export type SupplierInvoiceStatus = 'RECEIVED' | 'MATCHED' | 'APPROVED' | 'DISPUTED' | 'CANCELLED'
export type InvoiceLineType = 'STOCK' | 'PARTIAL_MATCH' | 'NON_PO_RECEIPT' | 'NON_STOCK'

export interface SupplierInvoiceLine {
  id: number
  lineNumber: number
  lineType: InvoiceLineType
  goodsReceiptLineId: number | null
  description: string | null
  qtyInvoiced: number | null
  unitPrice: number | null
  taxPercent: number | null
  taxAmount: number | null
  lineTotal: number
  note: string | null
}

export interface SupplierInvoice {
  id: number
  supplierId: number
  regionId: number
  outletId: number
  currencyCode: string
  invoiceNumber: string
  invoiceDate: string
  dueDate: string | null
  subtotal: number
  taxAmount: number
  totalAmount: number
  status: SupplierInvoiceStatus
  note: string | null
  approvedAt: string | null
  lines: SupplierInvoiceLine[]
}

export interface CreateSupplierInvoicePayload {
  supplierId: number
  regionId: number
  outletId: number
  currencyCode: string
  invoiceNumber: string
  invoiceDate: string
  dueDate?: string | null
  note?: string | null
  lines: Array<{
    lineType: InvoiceLineType
    goodsReceiptLineId?: number | null
    description?: string | null
    qtyInvoiced?: number | null
    unitPrice?: number | null
    taxPercent?: number | null
    taxAmount?: number | null
    lineTotal: number
    note?: string | null
  }>
}

// ─── Supplier payment ─────────────────────────────────────────────────────────
export type PaymentMethod = 'CASH' | 'CARD' | 'EWALLET' | 'BANK_TRANSFER' | 'CHEQUE' | 'VOUCHER'

export interface SupplierPaymentAllocationResponse {
  supplierInvoiceId: number
  allocatedAmount: number
  note: string | null
}

export interface SupplierPayment {
  id: number
  paymentNumber: string
  supplierId: number
  currencyCode: string
  paymentMethod: PaymentMethod
  amount: number
  paymentTime: string
  transactionRef: string | null
  note: string | null
  invoiceAllocations: SupplierPaymentAllocationResponse[]
}

export interface CreateSupplierPaymentPayload {
  supplierId: number
  currencyCode: string
  paymentMethod: PaymentMethod
  amount: number
  paymentTime: string
  transactionRef?: string | null
  note?: string | null
  invoiceAllocations: Array<{
    supplierInvoiceId: number
    allocatedAmount: number
    note?: string | null
  }>
}
