import { gatewayClient } from '@core/api/gatewayClient'
import type {
  CreateStockAdjustmentRequest,
  CreateStockCountSessionRequest,
  CreateWasteRecordRequest,
  InventoryTransactionFilters,
  InventoryTransactionPage,
  StockAdjustment,
  StockBalanceFilters,
  StockBalancePage,
  StockCountSession,
  UpdateStockCountLinesRequest,
  WasteRecord,
} from '../model/inventory.types'

// ─── Reads ────────────────────────────────────────────────────────────────────
export async function getStockBalances(filters: StockBalanceFilters) {
  const { data } = await gatewayClient.get<StockBalancePage>('/stock-balances', { params: filters })
  return data
}

export async function getInventoryTransactions(filters: InventoryTransactionFilters) {
  const { data } = await gatewayClient.get<InventoryTransactionPage>('/inventory-transactions', {
    params: filters,
  })
  return data
}

// ─── Stock adjustments ────────────────────────────────────────────────────────
export async function createStockAdjustment(request: CreateStockAdjustmentRequest) {
  const { data } = await gatewayClient.post<StockAdjustment>('/stock-adjustments', request)
  return data
}

export async function postStockAdjustment(id: number, idempotencyKey: string) {
  const { data } = await gatewayClient.post<StockAdjustment>(
    `/stock-adjustments/${id}/post`,
    null,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return data
}

export async function cancelStockAdjustment(id: number) {
  const { data } = await gatewayClient.post<StockAdjustment>(`/stock-adjustments/${id}/cancel`)
  return data
}

// ─── Waste records ────────────────────────────────────────────────────────────
export async function createWasteRecord(request: CreateWasteRecordRequest) {
  const { data } = await gatewayClient.post<WasteRecord>('/waste-records', request)
  return data
}

export async function postWasteRecord(id: number, idempotencyKey: string) {
  const { data } = await gatewayClient.post<WasteRecord>(
    `/waste-records/${id}/post`,
    null,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return data
}

export async function cancelWasteRecord(id: number) {
  const { data } = await gatewayClient.post<WasteRecord>(`/waste-records/${id}/cancel`)
  return data
}

// ─── Stock count sessions ─────────────────────────────────────────────────────
export async function createStockCountSession(request: CreateStockCountSessionRequest) {
  const { data } = await gatewayClient.post<StockCountSession>('/stock-count-sessions', request)
  return data
}

export async function startStockCountSession(id: number) {
  const { data } = await gatewayClient.post<StockCountSession>(`/stock-count-sessions/${id}/start`)
  return data
}

export async function updateStockCountLines(id: number, request: UpdateStockCountLinesRequest) {
  const { data } = await gatewayClient.put<StockCountSession>(
    `/stock-count-sessions/${id}/lines`,
    request,
  )
  return data
}

export async function postStockCountSession(id: number, idempotencyKey: string) {
  const { data } = await gatewayClient.post<StockCountSession>(
    `/stock-count-sessions/${id}/post`,
    null,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return data
}

export async function cancelStockCountSession(id: number) {
  const { data } = await gatewayClient.post<StockCountSession>(
    `/stock-count-sessions/${id}/cancel`,
  )
  return data
}
