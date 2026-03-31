import { useQueries } from '@tanstack/react-query'
import { getExportJob } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import type { ExportJob } from '../model/reportExport.types'
import { isReportsPermissionDenied } from '../services/reportsError.service'
import { listRecentExportJobIds } from '../services/exportHistory.service'

interface UseExportJobsOptions {
  enabled?: boolean
}

export function useExportJobs(options: UseExportJobsOptions = {}) {
  const enabled = options.enabled ?? true
  const jobIds = listRecentExportJobIds()
  const activeJobIds = enabled ? jobIds : []

  const queries = useQueries({
    queries: activeJobIds.map((jobId) => ({
      queryKey: reportQueryKeys.exportJob(jobId),
      queryFn: () => getExportJob(jobId),
      refetchInterval: 3_000,
    })),
  })

  const restrictedJobIds = queries.flatMap((query, index) =>
    isReportsPermissionDenied(query.error) ? [activeJobIds[index]] : [],
  )
  const nonPermissionError = queries.find((query) => query.error && !isReportsPermissionDenied(query.error))?.error

  return {
    jobIds,
    jobs: queries
      .map((query) => query.data)
      .filter((job): job is ExportJob => Boolean(job)),
    restrictedJobCount: restrictedJobIds.length,
    restrictedJobIds,
    isLoading: queries.some((query) => query.isLoading),
    error: nonPermissionError ?? null,
    refresh: async () => {
      await Promise.all(queries.map((query) => query.refetch()))
    },
  }
}
