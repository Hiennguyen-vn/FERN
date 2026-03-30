import type { ExportJob } from '../model/reportExport.types'

export function isTerminalExportStatus(status: string): boolean {
  return ['COMPLETED', 'FAILED'].includes(status.toUpperCase())
}

export function getExportPollingInterval(job?: ExportJob | null): number | false {
  if (!job) {
    return false
  }

  return isTerminalExportStatus(job.status) ? false : 3_000
}
