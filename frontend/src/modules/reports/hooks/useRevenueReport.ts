import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getExportPreview } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import type { RevenueReportFilters } from '../model/revenueReport.types'
import {
  buildRevenueExportPayload,
  buildRevenueRunRequest,
  buildRevenueSummary,
} from '../services/reportsReadModel.service'
import { canPreviewExport } from '../services/reportsUiPolicy.service'
import { useCreateExportJob } from './useCreateExportJob'
import { useExportJob } from './useExportJob'

export function useRevenueReport() {
  const createExport = useCreateExportJob()
  const [activeJobId, setActiveJobId] = useState<number | null>(null)
  const [activeFilters, setActiveFilters] = useState<RevenueReportFilters | null>(null)
  const exportJobQuery = useExportJob(activeJobId)
  const exportJob = exportJobQuery.data ?? null

  const previewQuery = useQuery({
    enabled: activeJobId !== null && Boolean(exportJob && canPreviewExport(exportJob)),
    queryFn: () => getExportPreview(activeJobId as number),
    queryKey: reportQueryKeys.exportPreview(activeJobId ?? 0),
  })

  const summary = useMemo(() => buildRevenueSummary(previewQuery.data), [previewQuery.data])

  async function runReport(filters: RevenueReportFilters) {
    const job = await createExport.mutateAsync(buildRevenueExportPayload(filters))
    setActiveJobId(job.exportJobId)
    setActiveFilters(filters)
    return job
  }

  return {
    activeFilters: activeFilters ? buildRevenueRunRequest(activeFilters) : null,
    exportJob,
    exportJobError: exportJobQuery.error,
    isCreating: createExport.isPending,
    isLoading: exportJobQuery.isLoading,
    preview: previewQuery.data ?? null,
    previewError: previewQuery.error,
    runReport,
    summary,
  }
}
