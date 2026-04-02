import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '../api/catalog.api'
import type { PromotionUpsertRequest } from '../model/catalog.types'

interface PromotionQueryOptions {
  enabled?: boolean
}

interface PromotionFilters {
  scopeId?: number
  scopeType?: string
}

const KEYS = {
  promotions: (filters: PromotionFilters) =>
    ['catalog', 'promotions', filters.scopeType ?? 'all-scopes', filters.scopeId ?? 'all-ids'] as const,
}

export function usePromotions(filters: PromotionFilters = {}, options: PromotionQueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.promotions(filters),
    queryFn: () => catalogApi.listPromotions(filters),
    enabled: options.enabled ?? true,
  })
}

export function useCreatePromotion() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (body: PromotionUpsertRequest) => catalogApi.createPromotion(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['catalog', 'promotions'] })
    },
  })
}

export function useUpdatePromotion(id: number) {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (body: PromotionUpsertRequest) => catalogApi.updatePromotion(id, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['catalog', 'promotions'] })
    },
  })
}

export function useDeactivatePromotion() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => catalogApi.deactivatePromotion(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['catalog', 'promotions'] })
    },
  })
}
