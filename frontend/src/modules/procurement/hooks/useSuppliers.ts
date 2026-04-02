import { useQuery } from '@tanstack/react-query'
import { listSuppliers } from '../api/procurement.api'

interface QueryOptions {
  enabled?: boolean
}

export function useSuppliers(options: QueryOptions = {}) {
  return useQuery({
    enabled: options.enabled ?? true,
    queryKey: ['procurement', 'suppliers'],
    queryFn: listSuppliers,
  })
}
