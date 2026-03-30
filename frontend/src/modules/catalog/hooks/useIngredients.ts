import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '../api/catalog.api'
import type { IngredientUpsertRequest } from '../model/catalog.types'

interface CatalogQueryOptions {
  enabled?: boolean
}

const KEYS = {
  ingredients: ['catalog', 'ingredients'] as const,
  ingredient: (id: number) => ['catalog', 'ingredients', id] as const,
}

export function useIngredients(options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.ingredients,
    queryFn: catalogApi.listIngredients,
    enabled: options.enabled ?? true,
  })
}

export function useIngredient(id: number, options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.ingredient(id),
    queryFn: () => catalogApi.getIngredient(id),
    enabled: (options.enabled ?? true) && id > 0,
  })
}

export function useCreateIngredient() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: IngredientUpsertRequest) => catalogApi.createIngredient(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.ingredients }),
  })
}

export function useUpdateIngredient(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: IngredientUpsertRequest) => catalogApi.updateIngredient(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.ingredient(id) })
      qc.invalidateQueries({ queryKey: KEYS.ingredients })
    },
  })
}
