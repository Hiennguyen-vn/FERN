import { gatewayClient } from '@core/api/gatewayClient'
import type {
  InventoryTransactionFilters,
  InventoryTransactionPage,
  StockBalanceFilters,
  StockBalancePage,
} from '../model/inventory.types'

export async function getStockBalances(filters: StockBalanceFilters) {
  const { data } = await gatewayClient.get<StockBalancePage>('/stock-balances', {
    params: filters,
  })

  return data
}

export async function getInventoryTransactions(filters: InventoryTransactionFilters) {
  const { data } = await gatewayClient.get<InventoryTransactionPage>('/inventory-transactions', {
    params: filters,
  })

  return data
}
