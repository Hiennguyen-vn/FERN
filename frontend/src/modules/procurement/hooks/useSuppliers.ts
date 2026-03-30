import { useQuery } from '@tanstack/react-query'
import { listSuppliers } from '../api/procurement.api'

export function useSuppliers() {
  return useQuery({
    queryKey: ['procurement', 'suppliers'],
    queryFn: listSuppliers,
  })
}
