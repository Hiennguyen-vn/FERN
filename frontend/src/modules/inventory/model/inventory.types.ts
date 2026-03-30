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
