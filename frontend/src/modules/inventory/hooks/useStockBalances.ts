import { useQuery } from '@tanstack/react-query'
import { getStockBalances } from '../api/inventory.api'
import type { StockBalanceFilters } from '../model/inventory.types'

export function useStockBalances(filters: StockBalanceFilters | null) {
  return useQuery({
    enabled: filters !== null,
    queryKey: ['inventory', 'stock-balances', filters],
    queryFn: () => getStockBalances(filters as StockBalanceFilters),
  })
}
