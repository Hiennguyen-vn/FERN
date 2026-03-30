import type { PageResponse } from '@core/types/api'

export interface ReportStockBalance {
  ingredientId: number
  lastCountDate: string | null
  outletId: number
  qtyAvailable: string
  qtyOnHand: string
  qtyReserved: string
  regionId: number
  unitCost: string
}

export interface ReportInventoryTransaction {
  businessDate: string
  createdByUserId: number | null
  id: number
  ingredientId: number
  outletId: number
  qtyChange: string
  regionId: number
  sourceReferenceId: string | null
  sourceReferenceType: string | null
  txnTime: string
  txnType: string
  unitCost: string
}

export interface InventoryReportFilters {
  from?: string
  ingredientId?: number
  outletId?: number
  page: number
  size: number
  to?: string
  txnType?: string
}

export interface InventoryReportSummary {
  availableQuantity: number
  balanceRows: number
  ingredients: number
  transactionRows: number
  txnQuantityDelta: number
}

export type ReportStockBalancePage = PageResponse<ReportStockBalance>
export type ReportInventoryTransactionPage = PageResponse<ReportInventoryTransaction>
