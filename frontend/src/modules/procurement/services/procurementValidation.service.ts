/**
 * Runtime validation functions for procurement write flows, derived directly from
 * backend Bean Validation annotations in ProcurementCommands.java.
 *
 * Each function returns null on success or a user-facing error string on failure.
 * Numeric parsing relies on shared helpers from `@shared/validators/parseInput`.
 */

import { isValidDateTime, parseDecimalMin, parseNonNegativeDecimal, parsePositiveInt } from '@shared/validators/parseInput'

// ─── GoodsReceiptCreatePage ────────────────────────────────────────────────────

export interface GoodsReceiptLineFormState {
  purchaseOrderLineId: string
  ingredientId: string
  uomCode: string
  qtyReceived: string
  unitCost: string
  note: string
}

export interface GoodsReceiptHeaderFormState {
  purchaseOrderId: string
  receiptTime: string
  businessDate: string
}

/**
 * Validates the header fields of a goods receipt.
 * Backend: purchaseOrderId @NotNull, receiptTime @NotNull (Instant), businessDate @NotNull (LocalDate)
 */
export function validateGoodsReceiptHeader(form: GoodsReceiptHeaderFormState): string | null {
  if (!parsePositiveInt(form.purchaseOrderId)) {
    return 'Purchase Order ID must be a positive integer.'
  }
  if (!form.businessDate.trim()) {
    return 'Business date is required.'
  }
  if (!isValidDateTime(form.receiptTime)) {
    return 'Receipt time must be a valid date/time.'
  }
  return null
}

/**
 * Validates the line array of a goods receipt.
 * Backend: lines @NotEmpty; each line: ingredientId @NotNull, uomCode @NotNull,
 *   qtyReceived @NotNull @DecimalMin("0.0001"), unitCost @NotNull @DecimalMin("0.00")
 */
export function validateGoodsReceiptLines(lines: GoodsReceiptLineFormState[]): string | null {
  if (lines.length === 0) {
    return 'At least one goods receipt line is required.'
  }
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const lineNum = i + 1
    if (!parsePositiveInt(line.ingredientId)) {
      return `Line ${lineNum}: Ingredient ID must be a positive integer.`
    }
    if (!line.uomCode.trim()) {
      return `Line ${lineNum}: UOM code is required.`
    }
    if (parseDecimalMin(line.qtyReceived, 0.0001) === null) {
      return `Line ${lineNum}: Qty received must be ≥ 0.0001.`
    }
    if (parseNonNegativeDecimal(line.unitCost) === null) {
      return `Line ${lineNum}: Unit cost must be a non-negative number.`
    }
  }
  return null
}

// ─── SupplierInvoiceCreatePage ─────────────────────────────────────────────────

export interface InvoiceHeaderFormState {
  supplierId: string
  regionId: string
  outletId: string
  currencyCode: string
  invoiceNumber: string
  invoiceDate: string
}

export interface InvoiceLineFormState {
  lineType: string
  lineTotal: string
}

/**
 * Validates the header of a supplier invoice.
 * Backend: supplierId @NotNull, regionId @NotNull, outletId @NotNull,
 *   currencyCode @NotNull, invoiceNumber @NotNull, invoiceDate @NotNull (LocalDate)
 */
export function validateSupplierInvoiceHeader(form: InvoiceHeaderFormState): string | null {
  if (!parsePositiveInt(form.supplierId)) {
    return 'Supplier ID must be a positive integer.'
  }
  if (!parsePositiveInt(form.regionId)) {
    return 'Region ID must be a positive integer.'
  }
  if (!parsePositiveInt(form.outletId)) {
    return 'Outlet ID must be a positive integer.'
  }
  if (!form.currencyCode.trim()) {
    return 'Currency code is required.'
  }
  if (!form.invoiceNumber.trim()) {
    return 'Invoice number is required.'
  }
  if (!form.invoiceDate.trim()) {
    return 'Invoice date is required.'
  }
  return null
}

/**
 * Validates the line array of a supplier invoice.
 * Backend: lines @NotEmpty; each line: lineType @NotNull @Pattern(...),
 *   lineTotal @NotNull @DecimalMin("0.00")
 *
 * Note: lineType pattern is enforced by the Select component options — all four
 * valid values (STOCK, PARTIAL_MATCH, NON_PO_RECEIPT, NON_STOCK) are present.
 * This function only validates lineTotal which is user-typed.
 */
export function validateSupplierInvoiceLines(lines: InvoiceLineFormState[]): string | null {
  if (lines.length === 0) {
    return 'At least one invoice line is required.'
  }
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const lineNum = i + 1
    if (parseNonNegativeDecimal(line.lineTotal) === null) {
      return `Line ${lineNum}: Line total must be a non-negative number.`
    }
  }
  return null
}

// ─── SupplierPaymentCreatePage ─────────────────────────────────────────────────

export interface PaymentHeaderFormState {
  supplierId: string
  amount: string
  paymentTime: string
}

export interface PaymentAllocationFormState {
  supplierInvoiceId: string
  allocatedAmount: string
}

/**
 * Validates the header of a supplier payment.
 * Backend: supplierId @NotNull, amount @NotNull @DecimalMin("0.01"),
 *   paymentTime @NotNull (Instant)
 */
export function validateSupplierPaymentHeader(form: PaymentHeaderFormState): string | null {
  if (!parsePositiveInt(form.supplierId)) {
    return 'Supplier ID must be a positive integer.'
  }
  if (parseDecimalMin(form.amount, 0.01) === null) {
    return 'Amount must be ≥ 0.01.'
  }
  if (!isValidDateTime(form.paymentTime)) {
    return 'Payment time must be a valid date/time.'
  }
  return null
}

/**
 * Validates the allocation array of a supplier payment.
 * Backend: invoiceAllocations @NotEmpty; each: supplierInvoiceId @NotNull,
 *   allocatedAmount @NotNull @DecimalMin("0.01")
 */
export function validatePaymentAllocations(allocations: PaymentAllocationFormState[]): string | null {
  if (allocations.length === 0) {
    return 'At least one invoice allocation is required.'
  }
  for (let i = 0; i < allocations.length; i++) {
    const alloc = allocations[i]
    const num = i + 1
    if (!parsePositiveInt(alloc.supplierInvoiceId)) {
      return `Allocation ${num}: Invoice ID must be a positive integer.`
    }
    if (parseDecimalMin(alloc.allocatedAmount, 0.01) === null) {
      return `Allocation ${num}: Allocated amount must be ≥ 0.01.`
    }
  }
  return null
}

// ─── PurchaseOrderCreatePage ───────────────────────────────────────────────────

export interface PurchaseOrderLineFormState {
  ingredientId: string
  uomCode: string
  qtyOrdered: string
}

/**
 * Validates the line array of a purchase order.
 * Backend: lines @NotEmpty; each line: ingredientId @NotNull, uomCode @NotNull,
 *   qtyOrdered @NotNull @DecimalMin("0.0001")
 */
export function validatePurchaseOrderLines(lines: PurchaseOrderLineFormState[]): string | null {
  if (lines.length === 0) {
    return 'At least one purchase order line is required.'
  }
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const lineNum = i + 1
    if (!parsePositiveInt(line.ingredientId)) {
      return `Line ${lineNum}: Ingredient ID must be a positive integer.`
    }
    if (!line.uomCode.trim()) {
      return `Line ${lineNum}: UOM code is required.`
    }
    // Backend: @DecimalMin("0.0001") — strictly greater than zero with minimum precision
    if (parseDecimalMin(line.qtyOrdered, 0.0001) === null) {
      return `Line ${lineNum}: Qty ordered must be ≥ 0.0001.`
    }
  }
  return null
}
