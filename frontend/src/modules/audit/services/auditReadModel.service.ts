import type {
  AuditEventSummary,
  RequestTraceDetail,
  RequestTraceSummary,
  SecurityEventSummary,
} from '../model/audit.types'

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export function formatAuditDate(value: string | null | undefined) {
  if (!value) {
    return 'N/A'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return dateFormatter.format(parsed)
}

export function formatAuditJson(value: unknown) {
  if (value == null) {
    return 'No data'
  }

  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

export function formatAuditScope(regionId?: number | null, outletId?: number | null) {
  if (regionId && outletId) {
    return `Region #${regionId} · Outlet #${outletId}`
  }

  if (regionId) {
    return `Region #${regionId}`
  }

  if (outletId) {
    return `Outlet #${outletId}`
  }

  return 'No explicit scope'
}

export function matchesAuditSearch(values: Array<string | number | null | undefined>, search: string) {
  const query = search.trim().toLowerCase()
  if (!query) {
    return true
  }

  return values.some((value) => String(value ?? '').toLowerCase().includes(query))
}

export function buildAuditEventSummaryCards(events: AuditEventSummary[]) {
  const successCount = events.filter((event) => String(event.outcome ?? '').toUpperCase() === 'SUCCESS').length
  const failedCount = events.filter((event) => String(event.outcome ?? '').toUpperCase() === 'FAILURE').length
  const distinctModules = new Set(events.map((event) => event.module)).size

  return [
    { label: 'Events', value: events.length },
    { label: 'Success', tone: 'success' as const, value: successCount },
    { label: 'Failures', tone: 'warning' as const, value: failedCount },
    { label: 'Modules', tone: 'info' as const, value: distinctModules },
  ]
}

export function buildSecuritySummaryCards(events: SecurityEventSummary[]) {
  const successCount = events.filter((event) => String(event.outcome ?? '').toUpperCase() === 'SUCCESS').length
  const failedCount = events.length - successCount
  const distinctUsers = new Set(events.map((event) => event.userId).filter(Boolean)).size

  return [
    { label: 'Security events', value: events.length },
    { label: 'Failures', tone: 'danger' as const, value: failedCount },
    { label: 'Successes', tone: 'success' as const, value: successCount },
    { label: 'Users', tone: 'info' as const, value: distinctUsers },
  ]
}

export function buildTraceSummaryCards(traces: RequestTraceSummary[]) {
  const errorCount = traces.filter((trace) => (trace.statusCode ?? 0) >= 500).length
  const avgDuration =
    traces.length > 0
      ? Math.round(
          traces.reduce((total, trace) => total + Number(trace.durationMs ?? 0), 0) / traces.length,
        )
      : 0

  return [
    { label: 'Request traces', value: traces.length },
    { label: '5xx traces', tone: 'danger' as const, value: errorCount },
    { label: 'Avg duration', tone: 'info' as const, value: `${avgDuration} ms` },
    { label: 'Correlations', tone: 'warning' as const, value: new Set(traces.map((trace) => trace.correlationId).filter(Boolean)).size },
  ]
}

export function getStatusCodePresentation(statusCode: number | null): {
  label: string
  tone: 'danger' | 'neutral' | 'success' | 'warning'
} {
  if (statusCode == null) {
    return { label: 'N/A', tone: 'neutral' }
  }

  if (statusCode >= 500) {
    return { label: String(statusCode), tone: 'danger' }
  }

  if (statusCode >= 400) {
    return { label: String(statusCode), tone: 'warning' }
  }

  if (statusCode >= 200) {
    return { label: String(statusCode), tone: 'success' }
  }

  return { label: String(statusCode), tone: 'neutral' }
}

export function buildTraceTitle(trace: RequestTraceDetail) {
  return `${trace.method} ${trace.endpoint}`
}
