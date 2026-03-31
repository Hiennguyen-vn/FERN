import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import {
  createSupplierInvoice,
  createSupplierPayment,
  getSupplierInvoice,
  supplierInvoiceAction,
} from '../api/procurement.api'

describe('procurement.api — supplier invoice and payment (AP flow)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('creates a supplier invoice against a goods receipt', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: { id: 501, invoiceNumber: 'INV-001', status: 'DRAFT' },
    } as any)

    const invoice = await createSupplierInvoice({
      goodsReceiptId: 9001,
      invoiceNumber: 'INV-001',
      invoiceDate: '2026-03-27',
      lines: [{ purchaseOrderLineId: 1001, invoicedQty: 10, unitPrice: 50000 }],
    } as any)

    expect(postSpy).toHaveBeenCalledWith('/supplier-invoices', expect.objectContaining({
      goodsReceiptId: 9001,
      invoiceNumber: 'INV-001',
    }))
    expect(invoice.id).toBe(501)
    expect(invoice.status).toBe('DRAFT')
  })

  it('retrieves a supplier invoice by id', async () => {
    const getSpy = vi.spyOn(gatewayClient, 'get').mockResolvedValue({
      data: { id: 501, invoiceNumber: 'INV-001', status: 'APPROVED' },
    } as any)

    const invoice = await getSupplierInvoice(501)

    expect(getSpy).toHaveBeenCalledWith('/supplier-invoices/501')
    expect(invoice.status).toBe('APPROVED')
  })

  it('approves and disputes a supplier invoice via action endpoint', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: { id: 501, status: 'APPROVED' },
    } as any)

    await supplierInvoiceAction(501, 'approve')
    expect(postSpy).toHaveBeenCalledWith('/supplier-invoices/501/approve')

    await supplierInvoiceAction(501, 'dispute')
    expect(postSpy).toHaveBeenCalledWith('/supplier-invoices/501/dispute')
  })

  it('creates a supplier payment linked to an invoice', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: { id: 601, status: 'POSTED' },
    } as any)

    const payload = {
      supplierId: 1,
      paymentDate: '2026-03-28',
      paymentMethod: 'BANK_TRANSFER',
      allocations: [{ supplierInvoiceId: 501, allocatedAmount: 500000 }],
    }
    const payment = await createSupplierPayment(payload as any, 'idem-pay-001')

    expect(postSpy).toHaveBeenCalledWith('/supplier-payments', payload, {
      headers: { 'Idempotency-Key': 'idem-pay-001' },
    })
    expect(payment.id).toBe(601)
  })
})
