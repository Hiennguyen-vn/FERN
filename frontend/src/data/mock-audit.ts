import type { AuditEvent, SecurityEvent, RequestTrace } from '@/types/audit';

export const mockAuditEvents: AuditEvent[] = [
  { id: 'ae-01', timestamp: '2026-04-04T13:45:22', actor: 'Marcus Rivera', actorRole: 'Outlet Manager', module: 'pos', action: 'create', entity: 'SaleOrder', entityId: 'SO-4823', result: 'success', scopeLevel: 'outlet', scopeName: 'Downtown Flagship', ipAddress: '10.0.1.42', correlationId: 'corr-a1b2c3' },
  { id: 'ae-02', timestamp: '2026-04-04T13:30:15', actor: 'Aisha Patel', actorRole: 'Outlet Manager', module: 'procurement', action: 'approve', entity: 'PurchaseOrder', entityId: 'PO-2026-0403', result: 'success', scopeLevel: 'outlet', scopeName: 'Downtown Flagship', ipAddress: '10.0.1.43', correlationId: 'corr-d4e5f6', reason: 'Within budget threshold' },
  { id: 'ae-03', timestamp: '2026-04-04T12:15:00', actor: 'System', actorRole: 'System', module: 'inventory', action: 'update', entity: 'StockBalance', entityId: 'bal-ing-03', result: 'success', scopeLevel: 'outlet', scopeName: 'Downtown Flagship', ipAddress: '10.0.0.1', correlationId: 'corr-g7h8i9', before: { quantity: 18.5 }, after: { quantity: 14.0 } },
  { id: 'ae-04', timestamp: '2026-04-04T11:45:30', actor: 'Jenny Tan', actorRole: 'Buyer', module: 'procurement', action: 'create', entity: 'PurchaseOrder', entityId: 'PO-2026-0404', result: 'success', scopeLevel: 'outlet', scopeName: 'Downtown Flagship', ipAddress: '10.0.1.44', correlationId: 'corr-j0k1l2' },
  { id: 'ae-05', timestamp: '2026-04-04T10:30:00', actor: 'Marcus Rivera', actorRole: 'Outlet Manager', module: 'inventory', action: 'approve', entity: 'StockCount', entityId: 'SC-0024', result: 'success', scopeLevel: 'outlet', scopeName: 'Downtown Flagship', ipAddress: '10.0.1.42', correlationId: 'corr-m3n4o5' },
  { id: 'ae-06', timestamp: '2026-04-04T09:15:00', actor: 'admin@opscenter.io', actorRole: 'System Admin', module: 'iam', action: 'update', entity: 'Role', entityId: 'role-cashier', result: 'success', scopeLevel: 'system', scopeName: 'System', ipAddress: '10.0.0.5', correlationId: 'corr-p6q7r8', before: { permissions: ['pos.order.*'] }, after: { permissions: ['pos.order.*', 'pos.table.read'] } },
  { id: 'ae-07', timestamp: '2026-04-04T08:00:12', actor: 'Marcus Rivera', actorRole: 'Outlet Manager', module: 'auth', action: 'login', entity: 'Session', entityId: 'sess-001', result: 'success', scopeLevel: 'system', scopeName: 'System', ipAddress: '10.0.1.42', correlationId: 'corr-s9t0u1' },
  { id: 'ae-08', timestamp: '2026-04-03T22:00:00', actor: 'System', actorRole: 'System', module: 'system', action: 'export', entity: 'DailyReport', entityId: 'rpt-20260403', result: 'success', scopeLevel: 'system', scopeName: 'System', ipAddress: '10.0.0.1', correlationId: 'corr-v2w3x4' },
  { id: 'ae-09', timestamp: '2026-04-03T17:30:00', actor: 'Unknown', actorRole: 'Unknown', module: 'iam', action: 'login', entity: 'Session', entityId: 'sess-fail-01', result: 'denied', scopeLevel: 'system', scopeName: 'System', ipAddress: '203.0.113.42', correlationId: 'corr-y5z6a7', reason: 'Invalid credentials — 3rd attempt' },
  { id: 'ae-10', timestamp: '2026-04-03T16:00:00', actor: 'Sarah Ng', actorRole: 'Finance Reviewer', module: 'catalog', action: 'update', entity: 'PricingRule', entityId: 'pr-01', result: 'success', scopeLevel: 'system', scopeName: 'System', ipAddress: '10.0.1.50', correlationId: 'corr-b8c9d0', before: { basePrice: 17.90 }, after: { basePrice: 18.90 } },
];

export const mockSecurityEvents: SecurityEvent[] = [
  { id: 'se-01', timestamp: '2026-04-04T09:45:00', severity: 'warning', type: 'brute_force_attempt', actor: '203.0.113.42', ipAddress: '203.0.113.42', description: 'Multiple failed login attempts detected from external IP', resolved: false, correlationId: 'corr-y5z6a7' },
  { id: 'se-02', timestamp: '2026-04-04T08:30:00', severity: 'info', type: 'permission_escalation', actor: 'admin@opscenter.io', ipAddress: '10.0.0.5', description: 'Role permission updated: cashier role gained pos.table.read', resolved: true, correlationId: 'corr-p6q7r8' },
  { id: 'se-03', timestamp: '2026-04-03T23:00:00', severity: 'info', type: 'api_key_rotation', actor: 'System', ipAddress: '10.0.0.1', description: 'Scheduled API key rotation completed for gateway service', resolved: true, correlationId: 'corr-e1f2g3' },
  { id: 'se-04', timestamp: '2026-04-02T14:20:00', severity: 'critical', type: 'unauthorized_access', actor: '198.51.100.15', ipAddress: '198.51.100.15', description: 'Attempted access to /api/iam/roles without authentication', resolved: true, correlationId: 'corr-h4i5j6' },
  { id: 'se-05', timestamp: '2026-04-01T11:00:00', severity: 'warning', type: 'rate_limit_exceeded', actor: 'pos-service', ipAddress: '10.0.1.42', description: 'POS service exceeded rate limit on stock reservation API (429)', resolved: true, correlationId: 'corr-k7l8m9' },
];

export const mockRequestTraces: RequestTrace[] = [
  { id: 'rt-01', correlationId: 'corr-a1b2c3', timestamp: '2026-04-04T13:45:22.001', method: 'POST', path: '/api/pos/sale-orders', statusCode: 201, durationMs: 142, actor: 'Marcus Rivera', service: 'gateway' },
  { id: 'rt-02', correlationId: 'corr-a1b2c3', timestamp: '2026-04-04T13:45:22.045', method: 'POST', path: '/internal/inventory/reserve', statusCode: 200, durationMs: 38, actor: 'pos-service', service: 'inventory-service', parentSpanId: 'rt-01' },
  { id: 'rt-03', correlationId: 'corr-a1b2c3', timestamp: '2026-04-04T13:45:22.090', method: 'POST', path: '/internal/pos/sale-orders', statusCode: 201, durationMs: 52, actor: 'gateway', service: 'pos-service', parentSpanId: 'rt-01' },
  { id: 'rt-04', correlationId: 'corr-d4e5f6', timestamp: '2026-04-04T13:30:15.001', method: 'PATCH', path: '/api/procurement/purchase-orders/po-03/approve', statusCode: 200, durationMs: 98, actor: 'Aisha Patel', service: 'gateway' },
  { id: 'rt-05', correlationId: 'corr-d4e5f6', timestamp: '2026-04-04T13:30:15.030', method: 'PATCH', path: '/internal/procurement/purchase-orders/po-03/approve', statusCode: 200, durationMs: 65, actor: 'gateway', service: 'procurement-service', parentSpanId: 'rt-04' },
  { id: 'rt-06', correlationId: 'corr-y5z6a7', timestamp: '2026-04-03T17:30:00.001', method: 'POST', path: '/api/auth/login', statusCode: 401, durationMs: 12, actor: 'Unknown', service: 'gateway' },
];

export const AUDIT_ACTION_CONFIG: Record<string, { label: string; class: string }> = {
  create: { label: 'Create', class: 'bg-success/10 text-success' },
  update: { label: 'Update', class: 'bg-info/10 text-info' },
  delete: { label: 'Delete', class: 'bg-destructive/10 text-destructive' },
  approve: { label: 'Approve', class: 'bg-success/10 text-success' },
  reject: { label: 'Reject', class: 'bg-warning/10 text-warning' },
  cancel: { label: 'Cancel', class: 'bg-muted text-muted-foreground' },
  login: { label: 'Login', class: 'bg-primary/10 text-primary' },
  logout: { label: 'Logout', class: 'bg-muted text-muted-foreground' },
  export: { label: 'Export', class: 'bg-info/10 text-info' },
};

export const AUDIT_RESULT_CONFIG: Record<string, { label: string; class: string }> = {
  success: { label: 'Success', class: 'bg-success/10 text-success' },
  failure: { label: 'Failure', class: 'bg-destructive/10 text-destructive' },
  denied: { label: 'Denied', class: 'bg-destructive/10 text-destructive' },
};

export const SECURITY_SEVERITY_CONFIG: Record<string, { label: string; class: string }> = {
  info: { label: 'Info', class: 'bg-info/10 text-info' },
  warning: { label: 'Warning', class: 'bg-warning/10 text-warning' },
  critical: { label: 'Critical', class: 'bg-destructive/10 text-destructive' },
};
