import { useQuery } from '@tanstack/react-query'
import { auditApi } from '../api/audit.api'
import { auditQueryKeys } from '../api/audit.queries'
import type { AuditEventsFilters, RequestTraceFilters, SecurityEventsFilters } from '../model/audit.types'

interface QueryOptions {
  enabled?: boolean
}

export function useAuditEvents(filters: AuditEventsFilters, options: QueryOptions = {}) {
  return useQuery({
    enabled: options.enabled ?? true,
    queryFn: () => auditApi.listAuditEvents(filters),
    queryKey: auditQueryKeys.auditEvents({ ...filters }),
  })
}

export function useAuditEvent(eventId: string | null, options: QueryOptions = {}) {
  return useQuery({
    enabled: (options.enabled ?? true) && Boolean(eventId),
    queryFn: () => auditApi.getAuditEvent(eventId as string),
    queryKey: auditQueryKeys.auditEvent(eventId ?? ''),
  })
}

export function useSecurityEvents(filters: SecurityEventsFilters, options: QueryOptions = {}) {
  return useQuery({
    enabled: options.enabled ?? true,
    queryFn: () => auditApi.listSecurityEvents(filters),
    queryKey: auditQueryKeys.securityEvents({ ...filters }),
  })
}

export function useRequestTraces(filters: RequestTraceFilters, options: QueryOptions = {}) {
  return useQuery({
    enabled: options.enabled ?? true,
    queryFn: () => auditApi.listRequestTraces(filters),
    queryKey: auditQueryKeys.requestTraces({ ...filters }),
  })
}

export function useRequestTrace(traceId: string | null, options: QueryOptions = {}) {
  return useQuery({
    enabled: (options.enabled ?? true) && Boolean(traceId),
    queryFn: () => auditApi.getRequestTrace(traceId as string),
    queryKey: auditQueryKeys.requestTrace(traceId ?? ''),
  })
}
