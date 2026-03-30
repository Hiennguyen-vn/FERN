import { useQueries } from '@tanstack/react-query'
import { getExportJob } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import type { ExportJob } from '../model/reportExport.types'
import { listRecentExportJobIds } from '../services/exportHistory.service'

export function useExportJobs() {
  const jobIds = listRecentExportJobIds()

  const queries = useQueries({
    queries: jobIds.map((jobId) => ({
      queryKey: reportQueryKeys.exportJob(jobId),
      queryFn: () => getExportJob(jobId),
      refetchInterval: 3_000,
    })),
  })

  return {
    jobIds,
    jobs: queries
      .map((query) => query.data)
      .filter((job): job is ExportJob => Boolean(job)),
    isLoading: queries.some((query) => query.isLoading),
    error: queries.find((query) => query.error)?.error,
    refresh: async () => {
      await Promise.all(queries.map((query) => query.refetch()))
    },
  }
}
