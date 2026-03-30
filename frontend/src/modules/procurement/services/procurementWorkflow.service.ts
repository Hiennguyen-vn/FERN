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
