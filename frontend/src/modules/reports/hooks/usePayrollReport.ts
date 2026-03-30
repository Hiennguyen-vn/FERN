import { useQuery } from '@tanstack/react-query'
import { getPayrollRunReport, getPayrollSummary, listPayrollRuns } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import type { ReportPayrollSummaryFilters } from '../model/payrollReport.types'

interface UsePayrollReportOptions {
  enabled?: boolean
  runId?: number
}

export function usePayrollReport(
  filters: ReportPayrollSummaryFilters | null,
  options: UsePayrollReportOptions = {},
) {
  const enabled = options.enabled ?? true
  const regionId = filters?.regionId

  const summaryQuery = useQuery({
    enabled: enabled && Boolean(filters?.regionId && filters.fromDate && filters.toDate),
    queryFn: () => getPayrollSummary(filters as ReportPayrollSummaryFilters),
    queryKey: reportQueryKeys.payrollSummary(filters ? { ...filters } : {}),
  })

  const runsQuery = useQuery({
    enabled: enabled && Boolean(regionId),
    queryFn: () => listPayrollRuns(regionId),
    queryKey: reportQueryKeys.payrollRuns(regionId),
  })

  const runDetailQuery = useQuery({
    enabled: enabled && Boolean(options.runId),
    queryFn: () => getPayrollRunReport(options.runId as number),
    queryKey: reportQueryKeys.payrollRunReport(options.runId ?? 0),
  })

  return {
    runDetailQuery,
    runsQuery,
    summaryQuery,
  }
}
