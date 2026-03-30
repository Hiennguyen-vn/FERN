export const auditQueryKeys = {
  auditEvent: (eventId: string) => ['audit', 'events', eventId] as const,
  auditEvents: (filters: Record<string, unknown>) => ['audit', 'events', filters] as const,
  requestTrace: (traceId: string) => ['audit', 'request-traces', traceId] as const,
  requestTraces: (filters: Record<string, unknown>) => ['audit', 'request-traces', filters] as const,
  securityEvents: (filters: Record<string, unknown>) => ['audit', 'security-events', filters] as const,
}
