const RECENT_EXPORT_JOBS_KEY = 'fern_reports_recent_export_jobs'
const MAX_RECENT_EXPORT_JOBS = 12

function readIds(): number[] {
  const raw = localStorage.getItem(RECENT_EXPORT_JOBS_KEY)

  if (!raw) {
    return []
  }

  try {
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? parsed.map((value) => Number(value)).filter(Number.isFinite) : []
  } catch {
    return []
  }
}

function writeIds(ids: number[]) {
  localStorage.setItem(RECENT_EXPORT_JOBS_KEY, JSON.stringify(ids))
}

export function listRecentExportJobIds(): number[] {
  return readIds()
}

export function addRecentExportJobId(jobId: number) {
  const next = [jobId, ...readIds().filter((id) => id !== jobId)].slice(0, MAX_RECENT_EXPORT_JOBS)
  writeIds(next)
}

export function removeRecentExportJobId(jobId: number) {
  writeIds(readIds().filter((id) => id !== jobId))
}
