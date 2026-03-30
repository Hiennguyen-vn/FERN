export interface AuditListResponse<T> {
  items: T[]
}

export interface AuditEventSummary {
  action: string | null
  correlationId: string | null
  detailSummary: string | null
  eventType: string
  id: string
  ingestedAt: string | null
  module: string
  occurredAt: string
  outletId: number | null
  outcome: string | null
  regionId: number | null
  resourceId: string | null
  resourceType: string | null
  sourceEventId: string
  sourceService: string
  userId: number | null
}

export interface AuditEventDetail extends AuditEventSummary {
  detailMasked: boolean
  idempotencyKey: string | null
  newValue: unknown
  oldValue: unknown
  payload: unknown
}

export interface SecurityEventSummary {
  correlationId: string | null
  detailSummary: string | null
  eventType: string
  failureReason: string | null
  id: string
  ingestedAt: string | null
  module: string
  occurredAt: string
  outcome: string | null
  sourceEventId: string
  sourceService: string
  userId: number | null
}

export interface SecurityEventDetail extends SecurityEventSummary {
  detailMasked: boolean
  idempotencyKey: string | null
  ipAddress: string | null
  payload: unknown
  userAgent: string | null
}

export interface RequestTraceSummary {
  correlationId: string | null
  detailSummary: string | null
  durationMs: number | null
  endpoint: string
  eventType: string
  id: string
  ingestedAt: string | null
  method: string
  module: string
  occurredAt: string
  outletId: number | null
  regionId: number | null
  requestId: string | null
  sourceEventId: string
  sourceService: string
  statusCode: number | null
  userId: number | null
}

export interface RequestTraceDetail extends RequestTraceSummary {
  detailMasked: boolean
  idempotencyKey: string | null
  payload: unknown
}

export interface AuditEventsFilters {
  action?: string
  correlationId?: string
  limit?: number
  module?: string
  occurredFrom?: string
  occurredTo?: string
  outletId?: number
  outcome?: string
  regionId?: number
  resourceId?: string
  resourceType?: string
  sourceService?: string
  userId?: number
}

export interface SecurityEventsFilters {
  correlationId?: string
  eventType?: string
  limit?: number
  module?: string
  occurredFrom?: string
  occurredTo?: string
  outcome?: string
  sourceService?: string
  userId?: number
}

export interface RequestTraceFilters {
  correlationId?: string
  endpoint?: string
  limit?: number
  method?: string
  module?: string
  occurredFrom?: string
  occurredTo?: string
  outletId?: number
  regionId?: number
  sourceService?: string
  statusCode?: number
  userId?: number
}
