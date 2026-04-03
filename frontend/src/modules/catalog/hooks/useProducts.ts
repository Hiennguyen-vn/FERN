import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '../api/catalog.api'
import type { ProductUpsertRequest } from '../model/catalog.types'

interface CatalogQueryOptions {
  enabled?: boolean
}

const KEYS = {
  products: ['catalog', 'products'] as const,
  product: (id: number) => ['catalog', 'products', id] as const,
  productCategories: ['catalog', 'product-categories'] as const,
  ingredientCategories: ['catalog', 'ingredient-categories'] as const,
  uoms: ['catalog', 'uoms'] as const,
}

// ─── Reference data ───────────────────────────────────────────────────────────
export function useProductCategories(options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.productCategories,
    queryFn: catalogApi.listProductCategories,
    staleTime: 1000 * 60 * 10,
    enabled: options.enabled ?? true,
  })
}

export function useIngredientCategories(options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.ingredientCategories,
    queryFn: catalogApi.listIngredientCategories,
    staleTime: 1000 * 60 * 10,
    enabled: options.enabled ?? true,
  })
}

export function useUnitsOfMeasure(options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.uoms,
    queryFn: catalogApi.listUnitsOfMeasure,
    staleTime: 1000 * 60 * 10,
    enabled: options.enabled ?? true,
  })
}

// ─── Products ─────────────────────────────────────────────────────────────────
export function useProducts(options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.products,
    queryFn: catalogApi.listProducts,
    enabled: options.enabled ?? true,
  })
}

export function useProduct(id: number, options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.product(id),
    queryFn: () => catalogApi.getProduct(id),
    enabled: (options.enabled ?? true) && id > 0,
  })
}

export function useCreateProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ProductUpsertRequest) => catalogApi.createProduct(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.products }),
  })
}

export function useUpdateProduct(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: ProductUpsertRequest) => catalogApi.updateProduct(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.product(id) })
      qc.invalidateQueries({ queryKey: KEYS.products })
    },
  })
}

export function useDeactivateProduct(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => catalogApi.deactivateProduct(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.product(id) })
      qc.invalidateQueries({ queryKey: KEYS.products })
    },
  })
}
