import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getOutletRevenueTodayStats } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'

export function useOutletRevenueTodayStats(outletIds: number[], enabled = true) {
  const normalizedOutletIds = useMemo(
    () => Array.from(new Set(outletIds.filter((outletId) => Number.isInteger(outletId) && outletId > 0))),
    [outletIds],
  )

  const query = useQuery({
    enabled: enabled && normalizedOutletIds.length > 0,
    queryFn: () => getOutletRevenueTodayStats(normalizedOutletIds),
    queryKey: reportQueryKeys.outletRevenueTodayStats(normalizedOutletIds),
    staleTime: 30_000,
  })

  return {
    ...query,
    outletStats: (query.data ?? []).map((stat) => ({ ...stat, isLoading: false })),
  }
}
