import { describe, expect, it } from 'vitest'
import {
  validateGoodsReceiptHeader,
  validateGoodsReceiptLines,
  validatePurchaseOrderLines,
  validateSupplierInvoiceHeader,
  validateSupplierInvoiceLines,
  validateSupplierPaymentHeader,
  validatePaymentAllocations,
} from '../services/procurementValidation.service'

// ─── GoodsReceiptCreatePage ────────────────────────────────────────────────────

describe('validateGoodsReceiptHeader', () => {
  const valid = {
    purchaseOrderId: '42',
    receiptTime: '2026-04-01T08:00',
    businessDate: '2026-04-01',
  }

  it('returns null for valid header', () => {
    expect(validateGoodsReceiptHeader(valid)).toBeNull()
  })

  // Backend: purchaseOrderId @NotNull (Long)
  it('rejects empty purchaseOrderId', () => {
    expect(validateGoodsReceiptHeader({ ...valid, purchaseOrderId: '' })).toMatch(/purchase order id/i)
  })

  it('rejects non-numeric purchaseOrderId', () => {
    expect(validateGoodsReceiptHeader({ ...valid, purchaseOrderId: 'abc' })).toMatch(/purchase order id/i)
  })

  it('rejects zero purchaseOrderId', () => {
    expect(validateGoodsReceiptHeader({ ...valid, purchaseOrderId: '0' })).toMatch(/purchase order id/i)
  })

  it('rejects negative purchaseOrderId', () => {
    expect(validateGoodsReceiptHeader({ ...valid, purchaseOrderId: '-1' })).toMatch(/purchase order id/i)
  })

  // Backend: businessDate @NotNull LocalDate
  it('rejects empty businessDate', () => {
    expect(validateGoodsReceiptHeader({ ...valid, businessDate: '' })).toMatch(/business date/i)
  })

  // Backend: receiptTime @NotNull Instant
  it('rejects empty receiptTime', () => {
    expect(validateGoodsReceiptHeader({ ...valid, receiptTime: '' })).toMatch(/receipt time/i)
  })

  it('rejects invalid receiptTime', () => {
    expect(validateGoodsReceiptHeader({ ...valid, receiptTime: 'not-a-date' })).toMatch(/receipt time/i)
  })
})

describe('validateGoodsReceiptLines', () => {
  const validLine = {
    purchaseOrderLineId: '10',
    ingredientId: '5',
    uomCode: 'KG',
    qtyReceived: '2',
    unitCost: '50.00',
    note: '',
  }

  it('returns null for valid single line', () => {
    expect(validateGoodsReceiptLines([validLine])).toBeNull()
  })

  it('rejects empty lines array', () => {
    expect(validateGoodsReceiptLines([])).toMatch(/at least one/i)
  })

  // Backend: ingredientId @NotNull Long
  it('rejects empty ingredientId', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, ingredientId: '' }])).toMatch(/ingredient id/i)
  })

  it('rejects non-numeric ingredientId', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, ingredientId: 'abc' }])).toMatch(/ingredient id/i)
  })

  // Backend: uomCode @NotNull
  it('rejects empty uomCode', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, uomCode: '' }])).toMatch(/uom code/i)
  })

  // Backend: qtyReceived @NotNull @DecimalMin("0.0001")
  it('rejects zero qtyReceived', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, qtyReceived: '0' }])).toMatch(/qty received/i)
  })

  it('rejects negative qtyReceived', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, qtyReceived: '-1' }])).toMatch(/qty received/i)
  })

  it('rejects NaN qtyReceived', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, qtyReceived: 'bad' }])).toMatch(/qty received/i)
  })

  it('allows minimum qtyReceived of 0.0001', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, qtyReceived: '0.0001' }])).toBeNull()
  })

  // Backend: unitCost @NotNull @DecimalMin("0.00") — zero is valid
  it('allows zero unitCost', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, unitCost: '0' }])).toBeNull()
  })

  it('rejects negative unitCost', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, unitCost: '-0.01' }])).toMatch(/unit cost/i)
  })

  it('rejects NaN unitCost (non-numeric string)', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, unitCost: 'abc' }])).toMatch(/unit cost/i)
  })

  it('allows empty unitCost (maps to 0 which is >= @DecimalMin("0.00"))', () => {
    expect(validateGoodsReceiptLines([{ ...validLine, unitCost: '' }])).toBeNull()
  })

  it('reports line number in error', () => {
    const result = validateGoodsReceiptLines([
      validLine,
      { ...validLine, ingredientId: '' },
    ])
    expect(result).toMatch(/line 2/i)
  })
})

// ─── SupplierInvoiceCreatePage ─────────────────────────────────────────────────

describe('validateSupplierInvoiceHeader', () => {
  const valid = {
    supplierId: '1',
    regionId: '2',
    outletId: '101',
    currencyCode: 'VND',
    invoiceNumber: 'INV-001',
    invoiceDate: '2026-04-01',
  }

  it('returns null for valid header', () => {
    expect(validateSupplierInvoiceHeader(valid)).toBeNull()
  })

  // Backend: supplierId @NotNull Long
  it('rejects non-numeric supplierId', () => {
    expect(validateSupplierInvoiceHeader({ ...valid, supplierId: 'abc' })).toMatch(/supplier id/i)
  })

  it('rejects zero supplierId', () => {
    expect(validateSupplierInvoiceHeader({ ...valid, supplierId: '0' })).toMatch(/supplier id/i)
  })

  // Backend: regionId @NotNull Long
  it('rejects empty regionId', () => {
    expect(validateSupplierInvoiceHeader({ ...valid, regionId: '' })).toMatch(/region id/i)
  })

  // Backend: outletId @NotNull Long
  it('rejects negative outletId', () => {
    expect(validateSupplierInvoiceHeader({ ...valid, outletId: '-5' })).toMatch(/outlet id/i)
  })

  // Backend: currencyCode @NotNull
  it('rejects empty currencyCode', () => {
    expect(validateSupplierInvoiceHeader({ ...valid, currencyCode: '' })).toMatch(/currency/i)
  })

  // Backend: invoiceNumber @NotNull
  it('rejects empty invoiceNumber', () => {
    expect(validateSupplierInvoiceHeader({ ...valid, invoiceNumber: '' })).toMatch(/invoice number/i)
  })

  // Backend: invoiceDate @NotNull LocalDate
  it('rejects empty invoiceDate', () => {
    expect(validateSupplierInvoiceHeader({ ...valid, invoiceDate: '' })).toMatch(/invoice date/i)
  })
})

describe('validateSupplierInvoiceLines', () => {
  const validLine = { lineType: 'STOCK', lineTotal: '100.00' }

  it('returns null for valid line', () => {
    expect(validateSupplierInvoiceLines([validLine])).toBeNull()
  })

  it('rejects empty lines array', () => {
    expect(validateSupplierInvoiceLines([])).toMatch(/at least one/i)
  })

  // Backend: lineTotal @NotNull @DecimalMin("0.00") — zero is valid
  it('allows zero lineTotal', () => {
    expect(validateSupplierInvoiceLines([{ ...validLine, lineTotal: '0' }])).toBeNull()
  })

  it('rejects negative lineTotal', () => {
    expect(validateSupplierInvoiceLines([{ ...validLine, lineTotal: '-1' }])).toMatch(/line total/i)
  })

  it('allows empty lineTotal (maps to 0 which is >= @DecimalMin("0.00"))', () => {
    expect(validateSupplierInvoiceLines([{ ...validLine, lineTotal: '' }])).toBeNull()
  })

  it('rejects non-numeric lineTotal', () => {
    expect(validateSupplierInvoiceLines([{ ...validLine, lineTotal: 'abc' }])).toMatch(/line total/i)
  })

  it('reports line number in error for non-numeric lineTotal', () => {
    const result = validateSupplierInvoiceLines([validLine, { ...validLine, lineTotal: 'abc' }])
    expect(result).toMatch(/line 2/i)
  })
})

// ─── SupplierPaymentCreatePage ─────────────────────────────────────────────────

describe('validateSupplierPaymentHeader', () => {
  const valid = {
    supplierId: '3',
    amount: '500000',
    paymentTime: '2026-04-01T10:00',
  }

  it('returns null for valid header', () => {
    expect(validateSupplierPaymentHeader(valid)).toBeNull()
  })

  // Backend: supplierId @NotNull Long
  it('rejects non-numeric supplierId', () => {
    expect(validateSupplierPaymentHeader({ ...valid, supplierId: 'abc' })).toMatch(/supplier id/i)
  })

  it('rejects zero supplierId', () => {
    expect(validateSupplierPaymentHeader({ ...valid, supplierId: '0' })).toMatch(/supplier id/i)
  })

  // Backend: amount @NotNull @DecimalMin("0.01")
  it('rejects zero amount', () => {
    expect(validateSupplierPaymentHeader({ ...valid, amount: '0' })).toMatch(/amount/i)
  })

  it('rejects amount below 0.01', () => {
    expect(validateSupplierPaymentHeader({ ...valid, amount: '0.009' })).toMatch(/amount/i)
  })

  it('rejects NaN amount', () => {
    expect(validateSupplierPaymentHeader({ ...valid, amount: 'bad' })).toMatch(/amount/i)
  })

  it('allows minimum amount of 0.01', () => {
    expect(validateSupplierPaymentHeader({ ...valid, amount: '0.01' })).toBeNull()
  })

  // Backend: paymentTime @NotNull Instant
  it('rejects empty paymentTime', () => {
    expect(validateSupplierPaymentHeader({ ...valid, paymentTime: '' })).toMatch(/payment time/i)
  })

  it('rejects invalid paymentTime', () => {
    expect(validateSupplierPaymentHeader({ ...valid, paymentTime: 'not-a-date' })).toMatch(/payment time/i)
  })
})

describe('validatePaymentAllocations', () => {
  const validAlloc = { supplierInvoiceId: '10', allocatedAmount: '100' }

  it('returns null for valid allocations', () => {
    expect(validatePaymentAllocations([validAlloc])).toBeNull()
  })

  it('rejects empty allocations array', () => {
    expect(validatePaymentAllocations([])).toMatch(/at least one/i)
  })

  // Backend: supplierInvoiceId @NotNull Long
  it('rejects empty supplierInvoiceId', () => {
    expect(validatePaymentAllocations([{ ...validAlloc, supplierInvoiceId: '' }])).toMatch(/invoice id/i)
  })

  it('rejects non-numeric supplierInvoiceId', () => {
    expect(validatePaymentAllocations([{ ...validAlloc, supplierInvoiceId: 'abc' }])).toMatch(/invoice id/i)
  })

  // Backend: allocatedAmount @NotNull @DecimalMin("0.01")
  it('rejects zero allocatedAmount', () => {
    expect(validatePaymentAllocations([{ ...validAlloc, allocatedAmount: '0' }])).toMatch(/allocated amount/i)
  })

  it('rejects NaN allocatedAmount', () => {
    expect(validatePaymentAllocations([{ ...validAlloc, allocatedAmount: 'bad' }])).toMatch(/allocated amount/i)
  })

  it('allows minimum allocatedAmount of 0.01', () => {
    expect(validatePaymentAllocations([{ ...validAlloc, allocatedAmount: '0.01' }])).toBeNull()
  })

  it('reports allocation number in error', () => {
    const result = validatePaymentAllocations([validAlloc, { ...validAlloc, allocatedAmount: '0' }])
    expect(result).toMatch(/allocation 2/i)
  })
})

// ─── PurchaseOrderCreatePage ───────────────────────────────────────────────────
// Contract: ProcurementCommands.PurchaseOrderLineInput
//   ingredientId @NotNull Long
//   uomCode @NotNull String
//   qtyOrdered @NotNull @DecimalMin("0.0001") BigDecimal

describe('validatePurchaseOrderLines', () => {
  const validLine = { ingredientId: '7', uomCode: 'KG', qtyOrdered: '1.5' }

  it('returns null for a valid single line', () => {
    expect(validatePurchaseOrderLines([validLine])).toBeNull()
  })

  it('returns null for multiple valid lines', () => {
    expect(validatePurchaseOrderLines([validLine, { ingredientId: '8', uomCode: 'L', qtyOrdered: '0.0001' }])).toBeNull()
  })

  it('rejects empty lines array (@NotEmpty)', () => {
    expect(validatePurchaseOrderLines([])).toMatch(/at least one/i)
  })

  // ingredientId @NotNull Long — must be positive integer
  it('rejects empty ingredientId', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, ingredientId: '' }])).toMatch(/ingredient id/i)
  })

  it('rejects decimal ingredientId (not a valid Long)', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, ingredientId: '1.5' }])).toMatch(/ingredient id/i)
  })

  it('rejects non-numeric ingredientId', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, ingredientId: 'abc' }])).toMatch(/ingredient id/i)
  })

  // uomCode @NotNull String
  it('rejects blank uomCode', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, uomCode: '' }])).toMatch(/uom code/i)
  })

  it('rejects whitespace-only uomCode', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, uomCode: '   ' }])).toMatch(/uom code/i)
  })

  // qtyOrdered @DecimalMin("0.0001") — must be >= 0.0001
  it('rejects zero qtyOrdered (below @DecimalMin("0.0001"))', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, qtyOrdered: '0' }])).toMatch(/qty ordered/i)
  })

  it('rejects 0.00009 (below @DecimalMin("0.0001"))', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, qtyOrdered: '0.00009' }])).toMatch(/qty ordered/i)
  })

  it('accepts exactly 0.0001 (the @DecimalMin boundary)', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, qtyOrdered: '0.0001' }])).toBeNull()
  })

  it('rejects non-numeric qtyOrdered', () => {
    expect(validatePurchaseOrderLines([{ ...validLine, qtyOrdered: 'bad' }])).toMatch(/qty ordered/i)
  })

  it('reports line number in error message', () => {
    const result = validatePurchaseOrderLines([validLine, { ...validLine, ingredientId: '' }])
    expect(result).toMatch(/line 2/i)
  })
})
