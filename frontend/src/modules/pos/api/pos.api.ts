import { generateIdempotencyKey } from '@core/api/idempotency'
import { gatewayClient } from '@core/api/gatewayClient'
import type {
  AddSalePaymentPayload,
  CreateSaleOrderPayload,
  OpenPosSessionPayload,
  OpenSessionResult,
  PosSession,
  PosSessionFilters,
  Product,
  ProductAvailability,
  ProductPrice,
  ReconcilePosSessionPayload,
  SaleOrder,
  UpdateSaleOrderPayload,
} from '../model/pos.types'

export async function listPosSessions(filters: PosSessionFilters) {
  const { data } = await gatewayClient.get<PosSession[]>('/pos-sessions', {
    params: {
      outletId: filters.outletId,
      terminalId: filters.terminalId || undefined,
      businessDate: filters.businessDate || undefined,
      status: filters.status || undefined,
    },
  })

  return data
}

export async function openPosSession(payload: OpenPosSessionPayload): Promise<OpenSessionResult> {
  const response = await gatewayClient.post<PosSession>('/pos-sessions', payload)

  return {
    session: response.data,
    sessionExisted: String(response.headers['x-session-existed']).toLowerCase() === 'true',
  }
}

export async function getPosSession(sessionId: number) {
  const { data } = await gatewayClient.get<PosSession>(`/pos-sessions/${sessionId}`)
  return data
}

export async function closePosSession(sessionId: number) {
  const { data } = await gatewayClient.post<PosSession>(`/pos-sessions/${sessionId}/close`)
  return data
}

export async function reconcilePosSession(sessionId: number, payload: ReconcilePosSessionPayload) {
  const { data } = await gatewayClient.post<PosSession>(`/pos-sessions/${sessionId}/reconcile`, payload)
  return data
}

export async function createSaleOrder(payload: CreateSaleOrderPayload) {
  const { currencyCode: _currencyCode, ...requestBody } = payload
  const { data } = await gatewayClient.post<SaleOrder>('/sale-orders', requestBody)
  return data
}

export async function getSaleOrder(orderId: number) {
  const { data } = await gatewayClient.get<SaleOrder>(`/sale-orders/${orderId}`)
  return data
}

export async function getSaleOrderSnapshot(orderId: number) {
  const { data } = await gatewayClient.get<Record<string, unknown>>(`/sale-orders/${orderId}/snapshot`)
  return data
}

export async function listSessionOrders(posSessionId: number, limit = 50) {
  const { data } = await gatewayClient.get<SaleOrder[]>('/sale-orders', {
    params: { posSessionId, limit },
  })
  return data
}

export async function updateSaleOrder(orderId: number, payload: UpdateSaleOrderPayload) {
  const { data } = await gatewayClient.patch<SaleOrder>(`/sale-orders/${orderId}`, payload)
  return data
}

export async function addSalePayment(orderId: number, payload: AddSalePaymentPayload, idempotencyKey = generateIdempotencyKey()) {
  const { data } = await gatewayClient.post<SaleOrder>(`/sale-orders/${orderId}/payments`, payload, {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  })
  return data
}

export async function completeSaleOrder(orderId: number) {
  const { data } = await gatewayClient.post<SaleOrder>(`/sale-orders/${orderId}/complete`)
  return data
}

export async function cancelSaleOrder(orderId: number) {
  const { data } = await gatewayClient.post<SaleOrder>(`/sale-orders/${orderId}/cancel`)
  return data
}

export async function getProducts() {
  const { data } = await gatewayClient.get<Product[]>('/products')
  return data
}

export async function getProductPrices() {
  const { data } = await gatewayClient.get<ProductPrice[]>('/product-prices')
  return data
}

export async function getProductAvailability(outletId: number) {
  const { data } = await gatewayClient.get<ProductAvailability[]>('/product-availability', {
    params: { outletId },
  })

  return data
}
