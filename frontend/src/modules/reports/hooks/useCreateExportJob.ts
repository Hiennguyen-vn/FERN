import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createExportJob } from '../api/reports.api'
import { reportMutationKeys } from '../api/reports.mutations'
import type { CreateExportPayload } from '../model/reportExport.types'

export function useCreateExportJob() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: reportMutationKeys.createExport,
    mutationFn: (payload: CreateExportPayload) => createExportJob(payload),
    onSuccess: (job) => {
      void queryClient.invalidateQueries({
        queryKey: ['reports', 'exports', 'list'],
      })
      void queryClient.invalidateQueries({
        queryKey: ['reports', 'exports', job.exportJobId],
      })
      void queryClient.invalidateQueries({
        queryKey: ['reports', 'dashboard'],
      })
    },
  })
}
