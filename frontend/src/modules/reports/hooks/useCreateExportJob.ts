import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createExportJob } from '../api/reports.api'
import { reportMutationKeys } from '../api/reports.mutations'
import { reportQueryKeys } from '../api/reports.queries'
import type { CreateExportPayload } from '../model/reportExport.types'
import { addRecentExportJobId, listRecentExportJobIds } from '../services/exportHistory.service'

export function useCreateExportJob() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: reportMutationKeys.createExport,
    mutationFn: (payload: CreateExportPayload) => createExportJob(payload),
    onSuccess: (job) => {
      addRecentExportJobId(job.exportJobId)
      void queryClient.invalidateQueries({
        queryKey: reportQueryKeys.exportJobs(listRecentExportJobIds()),
      })
    },
  })
}
