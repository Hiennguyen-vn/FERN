import { gatewayClient } from '@core/api/gatewayClient'
import type {
  CreateGoodsReceiptPayload,
  CreatePurchaseOrderPayload,
  GoodsReceipt,
  PurchaseOrder,
  Supplier,
} from '../model/procurement.types'

export async function listSuppliers() {
  const { data } = await gatewayClient.get<Supplier[]>('/suppliers')
  return data
}

export async function createPurchaseOrder(payload: CreatePurchaseOrderPayload) {
  const { data } = await gatewayClient.post<PurchaseOrder>('/purchase-orders', payload)
  return data
}

export async function getPurchaseOrder(id: number) {
  const { data } = await gatewayClient.get<PurchaseOrder>(`/purchase-orders/${id}`)
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

export async function getGoodsReceipt(id: number) {
  const { data } = await gatewayClient.get<GoodsReceipt>(`/goods-receipts/${id}`)
  return data
}

export async function goodsReceiptAction(id: number, action: 'receive' | 'post' | 'cancel') {
  const { data } = await gatewayClient.post<GoodsReceipt>(`/goods-receipts/${id}/${action}`)
  return data
}
