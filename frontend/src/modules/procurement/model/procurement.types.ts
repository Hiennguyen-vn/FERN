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
  qtyOrdered: string
  qtyReceived: string
  expectedUnitPrice: string | null
  taxPercent: string | null
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
  subtotalAmount: string
  taxAmount: string
  totalAmount: string
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
  qtyReceived: string
  unitCost: string
  lineTotal: string
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
  totalAmount: string
  supplierLotNumber: string | null
  note: string | null
  receivedAt: string | null
  postedAt: string | null
  lines: GoodsReceiptLine[]
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
    note?: string
  }>
}
