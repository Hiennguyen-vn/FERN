import { useQuery } from '@tanstack/react-query'
import { listExportJobs } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import type { ExportJobListFilters } from '../model/reportExport.types'

interface UseExportJobsOptions {
  enabled?: boolean
  filters?: ExportJobListFilters
}

export function useExportJobs(options: UseExportJobsOptions = {}) {
  const enabled = options.enabled ?? true
  const filters = {
    page: 0,
    size: 20,
    ...(options.filters ?? {}),
  }
  const query = useQuery({
    queryKey: reportQueryKeys.exportJobs(filters),
    queryFn: () => listExportJobs(filters),
    enabled,
    refetchInterval: enabled ? 3_000 : false,
  })

  return {
    jobs: query.data?.items ?? [],
    restrictedJobCount: 0,
    restrictedJobIds: [] as number[],
    hasMore: query.data?.hasMore ?? false,
    isLoading: query.isLoading,
    error: query.error ?? null,
    refresh: async () => {
      await query.refetch()
    },
  }
}
