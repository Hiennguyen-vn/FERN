import { useQuery } from '@tanstack/react-query'
import { getExportJob } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import { getExportPollingInterval } from '../services/exportPolling.service'

export function useExportJob(jobId: number | null) {
  return useQuery({
    enabled: jobId !== null,
    queryKey: reportQueryKeys.exportJob(jobId ?? 0),
    queryFn: () => getExportJob(jobId as number),
    refetchInterval: (query) => getExportPollingInterval(query.state.data),
  })
}
