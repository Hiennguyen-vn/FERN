import { useMemo } from 'react'
import { useExportJobs } from './useExportJobs'
import { buildReportDashboardSummary } from '../services/reportsReadModel.service'

export function useReportDashboard() {
  const exportJobs = useExportJobs()

  const summaryCards = useMemo(() => {
    const jobCount = exportJobs.jobs.length
    const runningJobs = exportJobs.jobs.filter((job) => !['COMPLETED', 'FAILED'].includes(job.status.toUpperCase())).length
    const completedJobs = exportJobs.jobs.filter((job) => job.status.toUpperCase() === 'COMPLETED').length

    return buildReportDashboardSummary(jobCount, runningJobs, completedJobs)
  }, [exportJobs.jobs])

  return {
    ...exportJobs,
    summaryCards,
  }
}
