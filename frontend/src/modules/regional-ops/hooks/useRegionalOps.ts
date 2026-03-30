import { useQueries, useQuery } from '@tanstack/react-query'
import { regionalOpsApi } from '../api/regionalOps.api'
import { regionalOpsQueryKeys } from '../api/regionalOps.queries'
import type { RegionalOutlet, RegionalRegion } from '../model/regionalOps.types'

interface RegionalOpsQueryOptions {
  enabled?: boolean
}

export function useRegionalRegion(regionId: number | null, options: RegionalOpsQueryOptions = {}) {
  return useQuery({
    enabled: (options.enabled ?? true) && Number.isInteger(regionId) && Number(regionId) > 0,
    queryFn: () => regionalOpsApi.getRegion(regionId as number),
    queryKey: regionalOpsQueryKeys.regionDetail(regionId ?? 0),
  })
}

export function useRegionalOutlet(outletId: number | null, options: RegionalOpsQueryOptions = {}) {
  return useQuery({
    enabled: (options.enabled ?? true) && Number.isInteger(outletId) && Number(outletId) > 0,
    queryFn: () => regionalOpsApi.getOutlet(outletId as number),
    queryKey: regionalOpsQueryKeys.outletDetail(outletId ?? 0),
  })
}

export function useRegionalOutlets(outletIds: number[], options: RegionalOpsQueryOptions = {}) {
  const uniqueOutletIds = Array.from(new Set(outletIds.filter((outletId) => Number.isInteger(outletId) && outletId > 0)))
  const queries = useQueries({
    queries: uniqueOutletIds.map((outletId) => ({
      enabled: (options.enabled ?? true) && outletId > 0,
      queryFn: () => regionalOpsApi.getOutlet(outletId),
      queryKey: regionalOpsQueryKeys.outletDetail(outletId),
    })),
  })

  return {
    error: queries.find((query) => query.error)?.error ?? null,
    isLoading: queries.some((query) => query.isLoading),
    refresh: async () => {
      await Promise.all(queries.map((query) => query.refetch()))
    },
    rows: queries.map((query) => query.data).filter((row): row is RegionalOutlet => Boolean(row)),
  }
}

export function useRegionalRegions(regionIds: number[], options: RegionalOpsQueryOptions = {}) {
  const uniqueRegionIds = Array.from(new Set(regionIds.filter((regionId) => Number.isInteger(regionId) && regionId > 0)))
  const queries = useQueries({
    queries: uniqueRegionIds.map((regionId) => ({
      enabled: (options.enabled ?? true) && regionId > 0,
      queryFn: () => regionalOpsApi.getRegion(regionId),
      queryKey: regionalOpsQueryKeys.regionDetail(regionId),
    })),
  })

  return {
    error: queries.find((query) => query.error)?.error ?? null,
    isLoading: queries.some((query) => query.isLoading),
    refresh: async () => {
      await Promise.all(queries.map((query) => query.refetch()))
    },
    rows: queries.map((query) => query.data).filter((row): row is RegionalRegion => Boolean(row)),
  }
}
