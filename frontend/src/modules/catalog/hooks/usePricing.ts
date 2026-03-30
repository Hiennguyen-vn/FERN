import { useQuery } from '@tanstack/react-query'
import { catalogApi } from '../api/catalog.api'

interface CatalogQueryOptions {
  enabled?: boolean
}

interface AvailabilityFilters {
  outletId?: number
  productId?: number
}

const KEYS = {
  productPrices: ['catalog', 'product-prices'] as const,
  availability: (filters: AvailabilityFilters) =>
    ['catalog', 'product-availability', filters.productId ?? 'all-products', filters.outletId ?? 'all-outlets'] as const,
}

export function useProductPrices(options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.productPrices,
    queryFn: catalogApi.listProductPrices,
    enabled: options.enabled ?? true,
  })
}

export function useAvailability(filters: AvailabilityFilters = {}, options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.availability(filters),
    queryFn: () => catalogApi.listAvailability(filters),
    enabled: options.enabled ?? true,
  })
}
