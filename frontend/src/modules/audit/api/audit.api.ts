import { gatewayClient } from '@core/api/gatewayClient'
import type {
  AuditEventDetail,
  AuditEventSummary,
  AuditEventsFilters,
  AuditListResponse,
  RequestTraceDetail,
  RequestTraceFilters,
  RequestTraceSummary,
  SecurityEventSummary,
  SecurityEventsFilters,
} from '../model/audit.types'

export const auditApi = {
  listAuditEvents(filters: AuditEventsFilters = {}) {
    return gatewayClient
      .get<AuditListResponse<AuditEventSummary>>('/audit/events', { params: filters })
      .then((response) => response.data.items)
  },

  getAuditEvent(eventId: string) {
    return gatewayClient.get<AuditEventDetail>(`/audit/events/${eventId}`).then((response) => response.data)
  },

  listSecurityEvents(filters: SecurityEventsFilters = {}) {
    return gatewayClient
      .get<AuditListResponse<SecurityEventSummary>>('/audit/security-events', { params: filters })
      .then((response) => response.data.items)
  },

  listRequestTraces(filters: RequestTraceFilters = {}) {
    return gatewayClient
      .get<AuditListResponse<RequestTraceSummary>>('/audit/request-traces', { params: filters })
      .then((response) => response.data.items)
  },

  getRequestTrace(traceId: string) {
    return gatewayClient
      .get<RequestTraceDetail>(`/audit/request-traces/${traceId}`)
      .then((response) => response.data)
  },
}
