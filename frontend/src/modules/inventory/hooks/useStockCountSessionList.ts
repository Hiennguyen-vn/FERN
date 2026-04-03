import { useQuery } from '@tanstack/react-query'
import { listStockCountSessions } from '../api/inventory.api'
import type { StockCountSessionListFilters } from '../model/inventory.types'

export function useStockCountSessionList(filters: StockCountSessionListFilters | null) {
  return useQuery({
    enabled: filters !== null,
    queryKey: ['inventory', 'stock-count-sessions', filters],
    queryFn: () => listStockCountSessions(filters as StockCountSessionListFilters),
  })
}
