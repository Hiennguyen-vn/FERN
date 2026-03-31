import type { PageResponse } from '@core/types/api'

export interface StockBalance {
  regionId: number
  outletId: number
  ingredientId: number
  qtyOnHand: string
  qtyReserved: string
  qtyAvailable: string
  unitCost: string
  lastCountDate: string | null
}

export interface InventoryTransaction {
  id: number
  regionId: number
  outletId: number
  ingredientId: number
  qtyChange: string
  businessDate: string
  txnTime: string
  txnType: string
  unitCost: string
  sourceReferenceType: string | null
  sourceReferenceId: string | null
  createdByUserId: number | null
}

export interface StockBalanceFilters {
  ingredientId?: number
  outletId: number
  page: number
  size: number
}

export interface InventoryTransactionFilters {
  from?: string
  ingredientId?: number
  outletId: number
  page: number
  size: number
  sourceId?: string
  sourceType?: string
  to?: string
  txnType?: string
}

export type StockBalancePage = PageResponse<StockBalance>
export type InventoryTransactionPage = PageResponse<InventoryTransaction>

// ─── Stock adjustment ─────────────────────────────────────────────────────────
export type StockAdjustmentStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'
export type AdjustmentDirection = 'IN' | 'OUT'

export interface StockAdjustment {
  id: number
  status: StockAdjustmentStatus
  regionId: number
  outletId: number
  ingredientId: number
  adjustmentDirection: AdjustmentDirection
  qty: string
  businessDate: string
  reason: string
  note: string | null
  inventoryTransactionId: number | null
  postedAt: string | null
}

export interface CreateStockAdjustmentRequest {
  regionId: number
  outletId: number
  ingredientId: number
  adjustmentDirection: AdjustmentDirection
  qty: number
  businessDate: string
  reason: string
  note?: string | null
}

// ─── Waste record ─────────────────────────────────────────────────────────────
export type WasteRecordStatus = 'DRAFT' | 'POSTED' | 'CANCELLED'

export interface WasteRecord {
  id: number
  status: WasteRecordStatus
  regionId: number
  outletId: number
  ingredientId: number
  qty: string
  businessDate: string
  reason: string
  note: string | null
  inventoryTransactionId: number | null
  postedAt: string | null
}

export interface CreateWasteRecordRequest {
  regionId: number
  outletId: number
  ingredientId: number
  qty: number
  businessDate: string
  reason: string
  note?: string | null
}

// ─── Stock count session ──────────────────────────────────────────────────────
export type StockCountSessionStatus = 'OPEN' | 'IN_PROGRESS' | 'POSTED' | 'CANCELLED'

export interface StockCountLine {
  ingredientId: number
  systemQty: string
  actualQty: string | null
  varianceQty: string | null
  note: string | null
}

export interface StockCountSession {
  id: number
  status: StockCountSessionStatus
  regionId: number
  outletId: number
  countDate: string
  note: string | null
  startedAt: string | null
  postedAt: string | null
  lines: StockCountLine[]
}

export interface CreateStockCountSessionRequest {
  regionId: number
  outletId: number
  countDate: string
  note?: string | null
  ingredientIds: number[]
}

export interface StockCountLineInput {
  ingredientId: number
  actualQty: number
  note?: string | null
}

export interface UpdateStockCountLinesRequest {
  lines: StockCountLineInput[]
}
