import type { InvoiceLineType } from '../model/procurement.types'

export function createDefaultInvoiceLine() {
  return {
    lineType: 'STOCK' as InvoiceLineType,
    goodsReceiptLineId: '',
    description: '',
    qtyInvoiced: '',
    unitPrice: '',
    taxPercent: '',
    taxAmount: '',
    lineTotal: '',
    note: '',
  }
}

export function createDefaultPaymentAllocation() {
  return {
    supplierInvoiceId: '',
    allocatedAmount: '',
    note: '',
  }
}

export function canApproveInvoice(status: string) {
  return ['RECEIVED', 'MATCHED'].includes(status.toUpperCase())
}

export function canDisputeInvoice(status: string) {
  return ['RECEIVED', 'MATCHED', 'APPROVED'].includes(status.toUpperCase())
}

export function createDefaultPurchaseOrderLine() {
  return {
    ingredientId: '',
    uomCode: 'KG',
    qtyOrdered: '',
    expectedUnitPrice: '',
    taxPercent: '',
    note: '',
  }
}

export function createDefaultGoodsReceiptLine() {
  return {
    purchaseOrderLineId: '',
    ingredientId: '',
    uomCode: 'KG',
    qtyReceived: '',
    unitCost: '',
    note: '',
  }
}
