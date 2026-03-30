import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '../api/catalog.api'
import type { RecipeUpsertRequest, RecipeVersionUpsertRequest } from '../model/catalog.types'

interface CatalogQueryOptions {
  enabled?: boolean
}

const KEYS = {
  recipes: ['catalog', 'recipes'] as const,
  recipe: (id: number) => ['catalog', 'recipes', id] as const,
  recipeVersions: (recipeId: number) => ['catalog', 'recipe-versions', recipeId] as const,
  recipeVersion: (id: number) => ['catalog', 'recipe-versions', 'detail', id] as const,
}

export function useRecipes(options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.recipes,
    queryFn: catalogApi.listRecipes,
    enabled: options.enabled ?? true,
  })
}

export function useRecipe(id: number, options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.recipe(id),
    queryFn: () => catalogApi.getRecipe(id),
    enabled: (options.enabled ?? true) && id > 0,
  })
}

export function useRecipeVersions(recipeId: number, options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.recipeVersions(recipeId),
    queryFn: () => catalogApi.listRecipeVersions(recipeId),
    enabled: (options.enabled ?? true) && recipeId > 0,
  })
}

export function useRecipeVersion(id: number, options: CatalogQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.recipeVersion(id),
    queryFn: () => catalogApi.getRecipeVersion(id),
    enabled: (options.enabled ?? true) && id > 0,
  })
}

export function useCreateRecipe() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: RecipeUpsertRequest) => catalogApi.createRecipe(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.recipes }),
  })
}

export function useCreateRecipeVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: RecipeVersionUpsertRequest) => catalogApi.createRecipeVersion(body),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: KEYS.recipeVersions(data.recipeId) })
    },
  })
}

export function useUpdateRecipeVersion(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: RecipeVersionUpsertRequest) => catalogApi.updateRecipeVersion(id, body),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: KEYS.recipeVersion(id) })
      qc.invalidateQueries({ queryKey: KEYS.recipeVersions(data.recipeId) })
    },
  })
}

export function useActivateRecipeVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => catalogApi.activateRecipeVersion(id),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: KEYS.recipeVersions(data.recipeId) })
    },
  })
}

export function useArchiveRecipeVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => catalogApi.archiveRecipeVersion(id),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: KEYS.recipeVersions(data.recipeId) })
    },
  })
}
