import { useQuery } from '@tanstack/react-query'
import { getProductAvailability, getProductPrices, getProducts } from '../api/pos.api'
import { posQueryKeys } from '../api/pos.queries'
import type { PosCatalogFilters } from '../model/pos.types'
import { resolveMenuItems } from '../services/catalogResolver.service'

export function usePosCatalog(filters: PosCatalogFilters | null, enabled = true) {
  return useQuery({
    enabled: filters !== null && enabled,
    queryKey: filters ? posQueryKeys.catalog(filters) : ['pos', 'catalog', 'disabled'],
    queryFn: async () => {
      const currentFilters = filters as PosCatalogFilters
      const [products, prices, availability] = await Promise.all([
        getProducts(),
        getProductPrices(),
        getProductAvailability(currentFilters.outletId),
      ])

      return resolveMenuItems(prices, products, availability, currentFilters)
    },
  })
}
