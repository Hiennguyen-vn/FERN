import { generateIdempotencyKey } from '@core/api/idempotency'
import { gatewayClient } from '@core/api/gatewayClient'
import type {
  CreateGoodsReceiptPayload,
  CreatePurchaseOrderPayload,
  CreateSupplierInvoicePayload,
  CreateSupplierPaymentPayload,
  GoodsReceipt,
  PurchaseOrder,
  Supplier,
  SupplierInvoice,
  SupplierPayment,
  SupplierUpsertPayload,
  UpdatePurchaseOrderPayload,
} from '../model/procurement.types'

export async function listSuppliers() {
  const { data } = await gatewayClient.get<Supplier[]>('/suppliers')
  return data
}

export async function createSupplier(payload: SupplierUpsertPayload) {
  const { data } = await gatewayClient.post<Supplier>('/suppliers', payload)
  return data
}

export async function updateSupplier(id: number, payload: SupplierUpsertPayload) {
  const { data } = await gatewayClient.patch<Supplier>(`/suppliers/${id}`, payload)
  return data
}

export async function activateSupplier(id: number) {
  const { data } = await gatewayClient.post<Supplier>(`/suppliers/${id}/activate`)
  return data
}

export async function createPurchaseOrder(payload: CreatePurchaseOrderPayload) {
  const { data } = await gatewayClient.post<PurchaseOrder>('/purchase-orders', payload)
  return data
}

export async function listPurchaseOrders(params?: { outletId?: number; supplierId?: number; status?: string; limit?: number }) {
  const { data } = await gatewayClient.get<PurchaseOrder[]>('/purchase-orders', { params })
  return data
}

export async function getPurchaseOrder(id: number) {
  const { data } = await gatewayClient.get<PurchaseOrder>(`/purchase-orders/${id}`)
  return data
}

export async function updatePurchaseOrder(id: number, payload: UpdatePurchaseOrderPayload) {
  const { data } = await gatewayClient.patch<PurchaseOrder>(`/purchase-orders/${id}`, payload)
  return data
}

export async function purchaseOrderAction(id: number, action: 'submit' | 'approve' | 'issue' | 'cancel') {
  const { data } = await gatewayClient.post<PurchaseOrder>(`/purchase-orders/${id}/${action}`)
  return data
}

export async function createGoodsReceipt(payload: CreateGoodsReceiptPayload) {
  const { data } = await gatewayClient.post<GoodsReceipt>('/goods-receipts', payload)
  return data
}

export async function listGoodsReceipts(params?: { purchaseOrderId?: number; outletId?: number; status?: string; limit?: number }) {
  const { data } = await gatewayClient.get<GoodsReceipt[]>('/goods-receipts', { params })
  return data
}

export async function getGoodsReceipt(id: number) {
  const { data } = await gatewayClient.get<GoodsReceipt>(`/goods-receipts/${id}`)
  return data
}

export async function goodsReceiptAction(id: number, action: 'receive' | 'post' | 'cancel') {
  const config = action === 'post' ? { headers: { 'Idempotency-Key': generateIdempotencyKey() } } : undefined
  const { data } = await gatewayClient.post<GoodsReceipt>(`/goods-receipts/${id}/${action}`, undefined, config)
  return data
}

// ─── Supplier invoices ────────────────────────────────────────────────────────
export async function listSupplierInvoices(params?: { supplierId?: number; outletId?: number; status?: string; limit?: number }) {
  const { data } = await gatewayClient.get<SupplierInvoice[]>('/supplier-invoices', { params })
  return data
}

export async function createSupplierInvoice(payload: CreateSupplierInvoicePayload) {
  const { data } = await gatewayClient.post<SupplierInvoice>('/supplier-invoices', payload)
  return data
}

export async function getSupplierInvoice(id: number) {
  const { data } = await gatewayClient.get<SupplierInvoice>(`/supplier-invoices/${id}`)
  return data
}

export async function supplierInvoiceAction(id: number, action: 'approve' | 'dispute') {
  const { data } = await gatewayClient.post<SupplierInvoice>(`/supplier-invoices/${id}/${action}`)
  return data
}

// ─── Supplier payments ────────────────────────────────────────────────────────
export async function listSupplierPayments(params?: { supplierId?: number; limit?: number }) {
  const { data } = await gatewayClient.get<SupplierPayment[]>('/supplier-payments', { params })
  return data
}

export async function createSupplierPayment(
  payload: CreateSupplierPaymentPayload,
  idempotencyKey: string,
) {
  const { data } = await gatewayClient.post<SupplierPayment>('/supplier-payments', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
  return data
}
