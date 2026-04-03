import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { orgApi } from '../api/org.api'
import { orgQueryKeys } from '../api/org.queries'
import type {
  CreateOutletPayload,
  CreateRegionPayload,
  OrgOutlet,
  OrgRegion,
  UpdateOutletPayload,
  UpdateRegionPayload,
} from '../model/org.types'

interface OrgQueryOptions {
  enabled?: boolean
}

export function useRegion(regionId: number, options: OrgQueryOptions = {}) {
  return useQuery({
    queryKey: orgQueryKeys.regionDetail(regionId),
    queryFn: () => orgApi.getRegion(regionId),
    enabled: (options.enabled ?? true) && regionId > 0,
  })
}

export function useRegionList(params: { search?: string; page?: number; size?: number }, options: OrgQueryOptions = {}) {
  return useQuery({
    queryKey: orgQueryKeys.regionList(params),
    queryFn: () => orgApi.listRegions(params),
    enabled: options.enabled ?? true,
  })
}

export function useOutlet(outletId: number, options: OrgQueryOptions = {}) {
  return useQuery({
    queryKey: orgQueryKeys.outletDetail(outletId),
    queryFn: () => orgApi.getOutlet(outletId),
    enabled: (options.enabled ?? true) && outletId > 0,
  })
}

export function useOutletList(
  params: { regionId?: number; search?: string; status?: string; page?: number; size?: number },
  options: OrgQueryOptions = {},
) {
  return useQuery({
    queryKey: orgQueryKeys.outletList(params),
    queryFn: () => orgApi.listOutlets(params),
    enabled: options.enabled ?? true,
  })
}

export function useRegions(regionIds: number[], options: OrgQueryOptions = {}) {
  const uniqueRegionIds = Array.from(
    new Set(regionIds.filter((regionId) => Number.isInteger(regionId) && regionId > 0)),
  )
  const queries = useQueries({
    queries: uniqueRegionIds.map((regionId) => ({
      queryKey: orgQueryKeys.regionDetail(regionId),
      queryFn: () => orgApi.getRegion(regionId),
      enabled: (options.enabled ?? true) && regionId > 0,
    })),
  })

  return {
    rows: queries.map((query) => query.data).filter((row): row is OrgRegion => Boolean(row)),
    isLoading: queries.some((query) => query.isLoading),
    error: queries.find((query) => query.error)?.error ?? null,
    refresh: async () => {
      await Promise.all(queries.map((query) => query.refetch()))
    },
  }
}

export function useCreateOutlet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateOutletPayload) => orgApi.createOutlet(payload),
    onSuccess: (outlet) => {
      void queryClient.invalidateQueries({ queryKey: ['org', 'outlets'] })
      void queryClient.invalidateQueries({ queryKey: ['regional-ops', 'outlets'] })
      void queryClient.invalidateQueries({ queryKey: ['ui'] })
      void queryClient.invalidateQueries({ queryKey: orgQueryKeys.outletDetail(outlet.id) })
    },
  })
}

export function useCreateRegion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateRegionPayload) => orgApi.createRegion(payload),
    onSuccess: (region) => {
      void queryClient.invalidateQueries({ queryKey: ['org', 'regions'] })
      void queryClient.invalidateQueries({ queryKey: ['regional-ops', 'regions'] })
      void queryClient.invalidateQueries({ queryKey: ['ui'] })
      void queryClient.invalidateQueries({ queryKey: orgQueryKeys.regionDetail(region.id) })
    },
  })
}

export function useUpdateRegion(regionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: UpdateRegionPayload) => orgApi.updateRegion(regionId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: orgQueryKeys.regionDetail(regionId) })
      void queryClient.invalidateQueries({ queryKey: ['org', 'regions'] })
      void queryClient.invalidateQueries({ queryKey: ['regional-ops', 'regions'] })
      void queryClient.invalidateQueries({ queryKey: ['ui'] })
    },
  })
}

export function useUpdateOutlet(outletId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: UpdateOutletPayload) => orgApi.updateOutlet(outletId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: orgQueryKeys.outletDetail(outletId) })
      void queryClient.invalidateQueries({ queryKey: ['org', 'outlets'] })
      void queryClient.invalidateQueries({ queryKey: ['regional-ops', 'outlets'] })
      void queryClient.invalidateQueries({ queryKey: ['ui'] })
    },
  })
}

export function useOutlets(outletIds: number[], options: OrgQueryOptions = {}) {
  const uniqueOutletIds = Array.from(
    new Set(outletIds.filter((outletId) => Number.isInteger(outletId) && outletId > 0)),
  )
  const queries = useQueries({
    queries: uniqueOutletIds.map((outletId) => ({
      queryKey: orgQueryKeys.outletDetail(outletId),
      queryFn: () => orgApi.getOutlet(outletId),
      enabled: (options.enabled ?? true) && outletId > 0,
    })),
  })

  return {
    rows: queries.map((query) => query.data).filter((row): row is OrgOutlet => Boolean(row)),
    isLoading: queries.some((query) => query.isLoading),
    error: queries.find((query) => query.error)?.error ?? null,
    refresh: async () => {
      await Promise.all(queries.map((query) => query.refetch()))
    },
  }
}
