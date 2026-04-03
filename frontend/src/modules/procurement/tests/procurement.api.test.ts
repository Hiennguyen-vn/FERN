import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@core/api/idempotency', () => ({
  generateIdempotencyKey: vi.fn(() => 'mock-idempotency-key'),
}))

vi.mock('@core/api/gatewayClient', () => ({
  gatewayClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}))

import { gatewayClient } from '@core/api/gatewayClient'
import {
  activateSupplier,
  createGoodsReceipt,
  createPurchaseOrder,
  createSupplier,
  createSupplierInvoice,
  createSupplierPayment,
  getGoodsReceipt,
  getPurchaseOrder,
  getSupplierInvoice,
  goodsReceiptAction,
  listGoodsReceipts,
  listPurchaseOrders,
  listSupplierInvoices,
  listSupplierPayments,
  listSuppliers,
  purchaseOrderAction,
  supplierInvoiceAction,
  updatePurchaseOrder,
  updateSupplier,
} from '../api/procurement.api'

describe('procurement.api', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('listSuppliers calls GET /suppliers and returns data', async () => {
    const suppliers = [{ id: 1, name: 'S1' } as any]
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: suppliers } as any)

    const result = await listSuppliers()
    expect(gatewayClient.get).toHaveBeenCalledWith('/suppliers')
    expect(result).toEqual(suppliers)
  })

  it('createSupplier calls POST /suppliers with payload and returns data', async () => {
    const created = { id: 2, name: 'New' } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: created } as any)

    const payload = { name: 'New', code: 'N1' } as any
    const result = await createSupplier(payload)
    expect(gatewayClient.post).toHaveBeenCalledWith('/suppliers', payload)
    expect(result).toEqual(created)
  })

  it('updateSupplier calls PATCH /suppliers/:id with payload', async () => {
    const updated = { id: 3, name: 'Upd' } as any
    vi.mocked(gatewayClient.patch).mockResolvedValue({ data: updated } as any)

    const payload = { name: 'Upd' } as any
    const result = await updateSupplier(3, payload)
    expect(gatewayClient.patch).toHaveBeenCalledWith('/suppliers/3', payload)
    expect(result).toEqual(updated)
  })

  it('activateSupplier calls POST /suppliers/:id/activate', async () => {
    const active = { id: 4, status: 'ACTIVE' } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: active } as any)

    const result = await activateSupplier(4)
    expect(gatewayClient.post).toHaveBeenCalledWith('/suppliers/4/activate')
    expect(result).toEqual(active)
  })

  it('createPurchaseOrder calls POST /purchase-orders with payload', async () => {
    const po = { id: 10 } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: po } as any)

    const payload = { supplierId: 1, outletId: 2 } as any
    const result = await createPurchaseOrder(payload)
    expect(gatewayClient.post).toHaveBeenCalledWith('/purchase-orders', payload)
    expect(result).toEqual(po)
  })

  it('listPurchaseOrders calls GET /purchase-orders with optional params', async () => {
    const list = [{ id: 1 } as any]
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: list } as any)

    const all = await listPurchaseOrders()
    expect(gatewayClient.get).toHaveBeenCalledWith('/purchase-orders', { params: undefined })
    expect(all).toEqual(list)

    vi.mocked(gatewayClient.get).mockResolvedValueOnce({ data: [] } as any)
    await listPurchaseOrders({ outletId: 7, supplierId: 2, status: 'DRAFT', limit: 50 })
    expect(gatewayClient.get).toHaveBeenCalledWith('/purchase-orders', {
      params: { outletId: 7, supplierId: 2, status: 'DRAFT', limit: 50 },
    })
  })

  it('getPurchaseOrder calls GET /purchase-orders/:id', async () => {
    const po = { id: 99 } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: po } as any)

    const result = await getPurchaseOrder(99)
    expect(gatewayClient.get).toHaveBeenCalledWith('/purchase-orders/99')
    expect(result).toEqual(po)
  })

  it('updatePurchaseOrder calls PATCH /purchase-orders/:id', async () => {
    const po = { id: 99, notes: 'x' } as any
    vi.mocked(gatewayClient.patch).mockResolvedValue({ data: po } as any)

    const payload = { notes: 'x' } as any
    const result = await updatePurchaseOrder(99, payload)
    expect(gatewayClient.patch).toHaveBeenCalledWith('/purchase-orders/99', payload)
    expect(result).toEqual(po)
  })

  it('purchaseOrderAction calls POST /purchase-orders/:id/:action', async () => {
    const po = { id: 1, status: 'ISSUED' } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: po } as any)

    for (const action of ['submit', 'approve', 'issue', 'cancel'] as const) {
      vi.mocked(gatewayClient.post).mockResolvedValueOnce({ data: po } as any)
      await purchaseOrderAction(5, action)
      expect(gatewayClient.post).toHaveBeenCalledWith(`/purchase-orders/5/${action}`)
    }
  })

  it('createGoodsReceipt calls POST /goods-receipts with payload', async () => {
    const gr = { id: 20 } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: gr } as any)

    const payload = { purchaseOrderId: 1, outletId: 2 } as any
    const result = await createGoodsReceipt(payload)
    expect(gatewayClient.post).toHaveBeenCalledWith('/goods-receipts', payload)
    expect(result).toEqual(gr)
  })

  it('listGoodsReceipts calls GET /goods-receipts with optional params', async () => {
    const list = [{ id: 1 } as any]
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: list } as any)

    await listGoodsReceipts()
    expect(gatewayClient.get).toHaveBeenCalledWith('/goods-receipts', { params: undefined })

    vi.mocked(gatewayClient.get).mockResolvedValueOnce({ data: [] } as any)
    await listGoodsReceipts({ purchaseOrderId: 3, outletId: 4, status: 'POSTED', limit: 10 })
    expect(gatewayClient.get).toHaveBeenCalledWith('/goods-receipts', {
      params: { purchaseOrderId: 3, outletId: 4, status: 'POSTED', limit: 10 },
    })
  })

  it('getGoodsReceipt calls GET /goods-receipts/:id', async () => {
    const gr = { id: 8 } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: gr } as any)

    const result = await getGoodsReceipt(8)
    expect(gatewayClient.get).toHaveBeenCalledWith('/goods-receipts/8')
    expect(result).toEqual(gr)
  })

  it('goodsReceiptAction posts receive/cancel without idempotency; post with header', async () => {
    const gr = { id: 1 } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: gr } as any)

    await goodsReceiptAction(1, 'receive')
    expect(gatewayClient.post).toHaveBeenCalledWith('/goods-receipts/1/receive', undefined, undefined)

    await goodsReceiptAction(1, 'cancel')
    expect(gatewayClient.post).toHaveBeenCalledWith('/goods-receipts/1/cancel', undefined, undefined)

    await goodsReceiptAction(1, 'post')
    expect(gatewayClient.post).toHaveBeenCalledWith('/goods-receipts/1/post', undefined, {
      headers: { 'Idempotency-Key': 'mock-idempotency-key' },
    })
  })

  it('listSupplierInvoices calls GET /supplier-invoices with optional params', async () => {
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: [] } as any)

    await listSupplierInvoices()
    expect(gatewayClient.get).toHaveBeenCalledWith('/supplier-invoices', { params: undefined })

    await listSupplierInvoices({ supplierId: 1, outletId: 2, status: 'APPROVED', limit: 5 })
    expect(gatewayClient.get).toHaveBeenCalledWith('/supplier-invoices', {
      params: { supplierId: 1, outletId: 2, status: 'APPROVED', limit: 5 },
    })
  })

  it('createSupplierInvoice calls POST /supplier-invoices', async () => {
    const inv = { id: 30 } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: inv } as any)

    const payload = { supplierId: 1, amount: 100 } as any
    const result = await createSupplierInvoice(payload)
    expect(gatewayClient.post).toHaveBeenCalledWith('/supplier-invoices', payload)
    expect(result).toEqual(inv)
  })

  it('getSupplierInvoice calls GET /supplier-invoices/:id', async () => {
    const inv = { id: 31 } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: inv } as any)

    const result = await getSupplierInvoice(31)
    expect(gatewayClient.get).toHaveBeenCalledWith('/supplier-invoices/31')
    expect(result).toEqual(inv)
  })

  it('supplierInvoiceAction calls POST /supplier-invoices/:id/:action', async () => {
    const inv = { id: 40 } as any
    for (const action of ['approve', 'dispute'] as const) {
      vi.mocked(gatewayClient.post).mockResolvedValueOnce({ data: inv } as any)
      await supplierInvoiceAction(40, action)
      expect(gatewayClient.post).toHaveBeenCalledWith(`/supplier-invoices/40/${action}`)
    }
  })

  it('listSupplierPayments calls GET /supplier-payments with optional params', async () => {
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: [] } as any)

    await listSupplierPayments()
    expect(gatewayClient.get).toHaveBeenCalledWith('/supplier-payments', { params: undefined })

    await listSupplierPayments({ supplierId: 9, limit: 25 })
    expect(gatewayClient.get).toHaveBeenCalledWith('/supplier-payments', { params: { supplierId: 9, limit: 25 } })
  })

  it('createSupplierPayment calls POST /supplier-payments with Idempotency-Key header', async () => {
    const pay = { id: 50 } as any
    vi.mocked(gatewayClient.post).mockResolvedValue({ data: pay } as any)

    const payload = { supplierId: 1, amount: 200 } as any
    const result = await createSupplierPayment(payload, 'client-key-xyz')
    expect(gatewayClient.post).toHaveBeenCalledWith('/supplier-payments', payload, {
      headers: { 'Idempotency-Key': 'client-key-xyz' },
    })
    expect(result).toEqual(pay)
  })
})
