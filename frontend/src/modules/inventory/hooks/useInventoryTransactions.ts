import { useQuery } from '@tanstack/react-query'
import { getInventoryTransactions } from '../api/inventory.api'
import type { InventoryTransactionFilters } from '../model/inventory.types'

export function useInventoryTransactions(filters: InventoryTransactionFilters | null) {
  return useQuery({
    enabled: filters !== null,
    queryKey: ['inventory', 'transactions', filters],
    queryFn: () => getInventoryTransactions(filters as InventoryTransactionFilters),
  })
}
