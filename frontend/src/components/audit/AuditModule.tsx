import { useState, useMemo } from 'react';
import {
  Search, X, Shield, Activity, Layers, ChevronRight,
  FileText, AlertTriangle, CheckCircle2, Clock, Filter,
  Eye, Copy, ExternalLink, Info,
} from 'lucide-react';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription,
} from '@/components/ui/sheet';
import {
  mockAuditEvents, mockSecurityEvents, mockRequestTraces,
  AUDIT_ACTION_CONFIG, AUDIT_RESULT_CONFIG, SECURITY_SEVERITY_CONFIG,
} from '@/data/mock-audit';
import type { AuditEvent, SecurityEvent, RequestTrace } from '@/types/audit';

type AuditTab = 'events' | 'security' | 'traces';

export function AuditModule() {
  const [activeTab, setActiveTab] = useState<AuditTab>('events');

  const tabs: { key: AuditTab; label: string; icon: React.ElementType; count: number }[] = [
    { key: 'events', label: 'Audit Explorer', icon: FileText, count: mockAuditEvents.length },
    { key: 'security', label: 'Security Events', icon: Shield, count: mockSecurityEvents.length },
    { key: 'traces', label: 'Request Traces', icon: Activity, count: mockRequestTraces.length },
  ];

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {tabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={cn(
              'flex items-center gap-1.5 px-4 py-3 text-xs font-medium border-b-2 transition-colors',
              activeTab === tab.key
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            <tab.icon className="h-3.5 w-3.5" />
            {tab.label}
            <span className={cn(
              'text-[9px] px-1.5 py-0.5 rounded-full font-medium ml-1',
              activeTab === tab.key ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'
            )}>{tab.count}</span>
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto">
        {activeTab === 'events' && <AuditExplorer />}
        {activeTab === 'security' && <SecurityEvents />}
        {activeTab === 'traces' && <RequestTraceViewer />}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════════════ */
/* Audit Explorer                                                             */
/* ═══════════════════════════════════════════════════════════════════════════ */

function AuditExplorer() {
  const [search, setSearch] = useState('');
  const [moduleFilter, setModuleFilter] = useState<string>('all');
  const [actionFilter, setActionFilter] = useState<string>('all');
  const [resultFilter, setResultFilter] = useState<string>('all');
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent | null>(null);

  const filtered = useMemo(() => mockAuditEvents.filter(e => {
    if (moduleFilter !== 'all' && e.module !== moduleFilter) return false;
    if (actionFilter !== 'all' && e.action !== actionFilter) return false;
    if (resultFilter !== 'all' && e.result !== resultFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      return e.actor.toLowerCase().includes(q) ||
        e.entity.toLowerCase().includes(q) ||
        e.entityId.toLowerCase().includes(q) ||
        e.correlationId.toLowerCase().includes(q);
    }
    return true;
  }), [search, moduleFilter, actionFilter, resultFilter]);

  const activeFilters = [moduleFilter, actionFilter, resultFilter].filter(f => f !== 'all').length;

  // KPI strip
  const kpis = useMemo(() => {
    const total = mockAuditEvents.length;
    const denied = mockAuditEvents.filter(e => e.result === 'denied').length;
    const modules = new Set(mockAuditEvents.map(e => e.module)).size;
    const actors = new Set(mockAuditEvents.map(e => e.actor)).size;
    return [
      { label: 'Total Events', value: total, color: 'text-foreground' },
      { label: 'Denied / Failed', value: denied, color: denied > 0 ? 'text-destructive' : 'text-foreground' },
      { label: 'Modules', value: modules, color: 'text-foreground' },
      { label: 'Unique Actors', value: actors, color: 'text-foreground' },
    ];
  }, []);

  // Related traces for selected event
  const relatedTraces = selectedEvent
    ? mockRequestTraces.filter(t => t.correlationId === selectedEvent.correlationId)
    : [];

  return (
    <div className="p-6 space-y-5 animate-fade-in">
      {/* ── Header ── */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground mb-1">
            <Layers className="h-3 w-3" />
            <span>Governance</span>
            <ChevronRight className="h-3 w-3" />
            <span className="text-foreground font-medium">Audit Explorer</span>
          </div>
          <h2 className="text-lg font-semibold text-foreground">Audit Explorer</h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Immutable audit trail — every action, every actor, every outcome
          </p>
        </div>
      </div>

      {/* ── KPI Strip ── */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {kpis.map(kpi => (
          <div key={kpi.label} className="surface-elevated p-3.5">
            <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{kpi.label}</span>
            <p className={cn('text-xl font-semibold mt-1', kpi.color)}>{kpi.value}</p>
          </div>
        ))}
      </div>

      {/* ── Filter Bar ── */}
      <div className="surface-elevated p-3 flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-1.5 text-muted-foreground">
          <Filter className="h-3.5 w-3.5" />
          <span className="text-[10px] font-semibold uppercase tracking-wide">Filters</span>
          {activeFilters > 0 && (
            <span className="text-[9px] px-1.5 py-0.5 rounded-full bg-primary/10 text-primary font-medium">{activeFilters}</span>
          )}
        </div>
        <div className="h-4 w-px bg-border" />
        <div className="relative flex-1 max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            placeholder="Search actor, entity, ID, correlation…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="pl-9 h-8 text-sm"
          />
        </div>
        <select value={moduleFilter} onChange={e => setModuleFilter(e.target.value)} className="text-xs px-3 py-1.5 border rounded-md bg-card text-foreground h-8">
          <option value="all">All Modules</option>
          {['pos', 'inventory', 'procurement', 'catalog', 'iam', 'auth', 'system'].map(m => <option key={m} value={m}>{m.toUpperCase()}</option>)}
        </select>
        <select value={actionFilter} onChange={e => setActionFilter(e.target.value)} className="text-xs px-3 py-1.5 border rounded-md bg-card text-foreground h-8">
          <option value="all">All Actions</option>
          {['create', 'update', 'delete', 'approve', 'reject', 'cancel', 'login', 'logout', 'export'].map(a => <option key={a} value={a}>{a.charAt(0).toUpperCase() + a.slice(1)}</option>)}
        </select>
        <select value={resultFilter} onChange={e => setResultFilter(e.target.value)} className="text-xs px-3 py-1.5 border rounded-md bg-card text-foreground h-8">
          <option value="all">All Results</option>
          {['success', 'failure', 'denied'].map(r => <option key={r} value={r}>{r.charAt(0).toUpperCase() + r.slice(1)}</option>)}
        </select>
        {activeFilters > 0 && (
          <button
            onClick={() => { setModuleFilter('all'); setActionFilter('all'); setResultFilter('all'); setSearch(''); }}
            className="text-[10px] text-primary hover:underline"
          >
            Clear all
          </button>
        )}
      </div>

      {/* ── Audit Events Table ── */}
      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Timestamp', 'Actor', 'Role', 'Module', 'Action', 'Entity', 'Result', 'Correlation ID', ''].map(h => (
                <th key={h} className="text-left text-[11px] font-medium text-muted-foreground px-4 py-2.5 whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={9} className="px-4 py-16 text-center">
                  <div className="text-muted-foreground">
                    <Search className="h-8 w-8 mx-auto mb-2 opacity-30" />
                    <p className="text-sm font-medium">No matching audit events</p>
                    <p className="text-xs mt-1">Try adjusting your filters or search terms</p>
                  </div>
                </td>
              </tr>
            ) : filtered.map(event => {
              const actionCfg = AUDIT_ACTION_CONFIG[event.action];
              const resultCfg = AUDIT_RESULT_CONFIG[event.result];
              const hasChange = Boolean(event.before || event.after);
              return (
                <tr
                  key={event.id}
                  onClick={() => setSelectedEvent(event)}
                  className={cn(
                    'border-b last:border-0 hover:bg-muted/20 cursor-pointer transition-colors',
                    selectedEvent?.id === event.id && 'bg-primary/5',
                    event.result === 'denied' && 'bg-destructive/[0.02]',
                  )}
                >
                  <td className="px-4 py-2.5 font-mono text-[10px] text-muted-foreground whitespace-nowrap">
                    {new Date(event.timestamp).toLocaleString()}
                  </td>
                  <td className="px-4 py-2.5 text-sm font-medium text-foreground whitespace-nowrap">{event.actor}</td>
                  <td className="px-4 py-2.5 text-[10px] text-muted-foreground">{event.actorRole}</td>
                  <td className="px-4 py-2.5">
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-muted font-medium uppercase">{event.module}</span>
                  </td>
                  <td className="px-4 py-2.5">
                    <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', actionCfg.class)}>{actionCfg.label}</span>
                  </td>
                  <td className="px-4 py-2.5 text-xs">
                    <span className="font-medium text-foreground">{event.entity}</span>
                    <span className="font-mono text-[10px] text-muted-foreground ml-1.5">{event.entityId}</span>
                    {hasChange && <span className="ml-1.5 text-[9px] text-info font-medium">Δ</span>}
                  </td>
                  <td className="px-4 py-2.5">
                    <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', resultCfg.class)}>{resultCfg.label}</span>
                  </td>
                  <td className="px-4 py-2.5 font-mono text-[10px] text-primary/70 whitespace-nowrap">{event.correlationId}</td>
                  <td className="px-4 py-2.5">
                    <Eye className="h-3 w-3 text-muted-foreground opacity-0 group-hover:opacity-100" />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>

        {/* Result count footer */}
        <div className="px-4 py-2 border-t border-border bg-muted/10 flex items-center justify-between">
          <span className="text-[10px] text-muted-foreground">
            Showing {filtered.length} of {mockAuditEvents.length} events
          </span>
          <span className="text-[10px] text-muted-foreground font-mono">
            Latest: {mockAuditEvents[0] && new Date(mockAuditEvents[0].timestamp).toLocaleString()}
          </span>
        </div>
      </div>

      {/* ── Detail Drawer (Sheet) ── */}
      <Sheet open={!!selectedEvent} onOpenChange={open => { if (!open) setSelectedEvent(null); }}>
        <SheetContent side="right" className="w-full sm:max-w-lg overflow-y-auto p-0">
          <SheetHeader className="px-6 pt-6 pb-4 border-b">
            <div className="flex items-center gap-2 mb-1">
              {selectedEvent && (
                <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', AUDIT_RESULT_CONFIG[selectedEvent.result].class)}>
                  {AUDIT_RESULT_CONFIG[selectedEvent.result].label}
                </span>
              )}
              {selectedEvent && (
                <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', AUDIT_ACTION_CONFIG[selectedEvent.action].class)}>
                  {AUDIT_ACTION_CONFIG[selectedEvent.action].label}
                </span>
              )}
            </div>
            <SheetTitle className="text-base">
              {selectedEvent?.entity} <span className="font-mono text-sm text-muted-foreground">{selectedEvent?.entityId}</span>
            </SheetTitle>
            <SheetDescription className="text-xs">
              {selectedEvent && new Date(selectedEvent.timestamp).toLocaleString()} · {selectedEvent?.module.toUpperCase()} module
            </SheetDescription>
          </SheetHeader>

          {selectedEvent && (
            <div className="p-6 space-y-6">
              {/* ─ Context Grid ─ */}
              <div className="space-y-0.5">
                <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">Event Context</span>
                <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-3">
                  {[
                    ['Actor', selectedEvent.actor],
                    ['Role', selectedEvent.actorRole],
                    ['Module', selectedEvent.module.toUpperCase()],
                    ['Action', selectedEvent.action],
                    ['Scope Level', selectedEvent.scopeLevel],
                    ['Scope Name', selectedEvent.scopeName],
                    ['IP Address', selectedEvent.ipAddress],
                    ['Correlation ID', selectedEvent.correlationId],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <p className="text-[10px] text-muted-foreground">{label}</p>
                      <p className={cn(
                        'text-xs font-medium text-foreground mt-0.5',
                        (label === 'IP Address' || label === 'Correlation ID') && 'font-mono text-[11px]'
                      )}>{value}</p>
                    </div>
                  ))}
                </div>
              </div>

              {/* ─ Business Reason ─ */}
              {selectedEvent.reason && (
                <div className="p-3.5 rounded-lg bg-muted/30 border">
                  <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">Business Reason</span>
                  <p className="text-xs text-foreground mt-1.5 leading-relaxed">{selectedEvent.reason}</p>
                </div>
              )}

              {/* ─ Before / After Diff ─ */}
              {(selectedEvent.before || selectedEvent.after) && (
                <div className="space-y-2">
                  <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">State Change</span>
                  <div className="grid grid-cols-2 gap-3">
                    {selectedEvent.before && (
                      <div className="rounded-lg border border-destructive/20 overflow-hidden">
                        <div className="px-3 py-1.5 bg-destructive/5 border-b border-destructive/10">
                          <span className="text-[9px] font-semibold text-destructive uppercase tracking-wide">Before</span>
                        </div>
                        <div className="p-3">
                          {Object.entries(selectedEvent.before).map(([k, v]) => (
                            <div key={k} className="flex justify-between text-[11px] py-0.5">
                              <span className="text-muted-foreground">{k}</span>
                              <span className="font-mono text-foreground">{JSON.stringify(v)}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                    {selectedEvent.after && (
                      <div className="rounded-lg border border-success/20 overflow-hidden">
                        <div className="px-3 py-1.5 bg-success/5 border-b border-success/10">
                          <span className="text-[9px] font-semibold text-success uppercase tracking-wide">After</span>
                        </div>
                        <div className="p-3">
                          {Object.entries(selectedEvent.after).map(([k, v]) => (
                            <div key={k} className="flex justify-between text-[11px] py-0.5">
                              <span className="text-muted-foreground">{k}</span>
                              <span className="font-mono text-foreground">{JSON.stringify(v)}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* ─ Related Trace Chain ─ */}
              {relatedTraces.length > 0 && (
                <div className="space-y-2">
                  <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">Trace Chain</span>
                  <div className="space-y-0">
                    {relatedTraces.map((trace, i) => (
                      <div key={trace.id} className="relative pl-6 pb-3">
                        {i < relatedTraces.length - 1 && <div className="absolute left-[9px] top-4 bottom-0 w-px bg-border" />}
                        <div className={cn(
                          'absolute left-0 top-1 h-[18px] w-[18px] rounded-full border-2 flex items-center justify-center',
                          trace.statusCode < 300 ? 'border-success bg-success/10' : 'border-destructive bg-destructive/10'
                        )}>
                          <div className={cn('h-1.5 w-1.5 rounded-full', trace.statusCode < 300 ? 'bg-success' : 'bg-destructive')} />
                        </div>
                        <div className="space-y-0.5">
                          <p className="text-xs font-medium text-foreground">{trace.service}</p>
                          <p className="font-mono text-[10px] text-muted-foreground">
                            <span className={cn(
                              'font-bold mr-1',
                              trace.method === 'POST' ? 'text-success' : trace.method === 'PATCH' ? 'text-warning' : 'text-info'
                            )}>{trace.method}</span>
                            {trace.path}
                          </p>
                          <div className="flex items-center gap-2 text-[10px]">
                            <span className={cn('font-mono font-bold', trace.statusCode < 300 ? 'text-success' : 'text-destructive')}>{trace.statusCode}</span>
                            <span className="text-muted-foreground">{trace.durationMs}ms</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════════════ */
/* Security Events                                                            */
/* ═══════════════════════════════════════════════════════════════════════════ */

function SecurityEvents() {
  const [selectedSeverity, setSelectedSeverity] = useState<string>('all');

  const severityCounts = useMemo(() => ({
    critical: mockSecurityEvents.filter(e => e.severity === 'critical').length,
    warning: mockSecurityEvents.filter(e => e.severity === 'warning').length,
    info: mockSecurityEvents.filter(e => e.severity === 'info').length,
    open: mockSecurityEvents.filter(e => !e.resolved).length,
  }), []);

  const filtered = useMemo(() =>
    selectedSeverity === 'all'
      ? mockSecurityEvents
      : mockSecurityEvents.filter(e => e.severity === selectedSeverity),
    [selectedSeverity]
  );

  return (
    <div className="p-6 space-y-5 animate-fade-in">
      {/* Header */}
      <div>
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground mb-1">
          <Layers className="h-3 w-3" />
          <span>Governance</span>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Security Events</span>
        </div>
        <h2 className="text-lg font-semibold text-foreground">Security Events</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Threat monitoring — access anomalies, escalations, and policy violations</p>
      </div>

      {/* KPI + filter row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: 'Critical', value: severityCounts.critical, color: severityCounts.critical > 0 ? 'text-destructive' : 'text-foreground', filter: 'critical' },
          { label: 'Warning', value: severityCounts.warning, color: severityCounts.warning > 0 ? 'text-warning' : 'text-foreground', filter: 'warning' },
          { label: 'Info', value: severityCounts.info, color: 'text-foreground', filter: 'info' },
          { label: 'Open / Unresolved', value: severityCounts.open, color: severityCounts.open > 0 ? 'text-destructive' : 'text-success', filter: 'all' },
        ].map(kpi => (
          <button
            key={kpi.label}
            onClick={() => setSelectedSeverity(kpi.filter === 'all' ? 'all' : kpi.filter)}
            className={cn(
              'surface-elevated p-3.5 text-left transition-colors',
              selectedSeverity === kpi.filter && 'ring-1 ring-primary/30'
            )}
          >
            <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{kpi.label}</span>
            <p className={cn('text-xl font-semibold mt-1', kpi.color)}>{kpi.value}</p>
            <p className="text-[10px] text-muted-foreground mt-0.5">Last 7 days</p>
          </button>
        ))}
      </div>

      {/* Table */}
      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Timestamp', 'Severity', 'Type', 'Description', 'Actor / Source', 'IP Address', 'Status'].map(h => (
                <th key={h} className="text-left text-[11px] font-medium text-muted-foreground px-4 py-2.5 whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.map(event => {
              const sevCfg = SECURITY_SEVERITY_CONFIG[event.severity];
              return (
                <tr key={event.id} className={cn(
                  'border-b last:border-0 hover:bg-muted/20 transition-colors',
                  event.severity === 'critical' && !event.resolved && 'bg-destructive/[0.03]',
                )}>
                  <td className="px-4 py-2.5 font-mono text-[10px] text-muted-foreground whitespace-nowrap">{new Date(event.timestamp).toLocaleString()}</td>
                  <td className="px-4 py-2.5">
                    <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', sevCfg.class)}>{sevCfg.label}</span>
                  </td>
                  <td className="px-4 py-2.5 font-mono text-[10px] text-foreground">{event.type.split('_').join(' ')}</td>
                  <td className="px-4 py-2.5 text-xs text-foreground max-w-xs">{event.description}</td>
                  <td className="px-4 py-2.5 text-xs text-muted-foreground font-mono">{event.actor}</td>
                  <td className="px-4 py-2.5 font-mono text-[10px] text-muted-foreground">{event.ipAddress}</td>
                  <td className="px-4 py-2.5">
                    {event.resolved
                      ? <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full font-medium bg-success/10 text-success"><CheckCircle2 className="h-2.5 w-2.5" />Resolved</span>
                      : <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full font-medium bg-destructive/10 text-destructive"><AlertTriangle className="h-2.5 w-2.5" />Open</span>
                    }
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <div className="px-4 py-2 border-t border-border bg-muted/10">
          <span className="text-[10px] text-muted-foreground">Showing {filtered.length} of {mockSecurityEvents.length} events</span>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════════════ */
/* Request Trace Viewer                                                       */
/* ═══════════════════════════════════════════════════════════════════════════ */

function RequestTraceViewer() {
  const [selectedCorrelation, setSelectedCorrelation] = useState<string | null>(null);

  const correlationGroups = useMemo(() =>
    mockRequestTraces.reduce((acc, trace) => {
      if (!acc[trace.correlationId]) acc[trace.correlationId] = [];
      acc[trace.correlationId].push(trace);
      return acc;
    }, {} as Record<string, RequestTrace[]>),
  []);

  const activeTraces = selectedCorrelation ? correlationGroups[selectedCorrelation] || [] : [];
  const uniqueCorrelations = Object.keys(correlationGroups);

  return (
    <div className="p-6 space-y-5 animate-fade-in">
      {/* Header */}
      <div>
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground mb-1">
          <Layers className="h-3 w-3" />
          <span>Governance</span>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Request Traces</span>
        </div>
        <h2 className="text-lg font-semibold text-foreground">Request Traces</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Distributed trace chains — observability across service boundaries</p>
      </div>

      {/* KPI strip */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: 'Total Spans', value: mockRequestTraces.length },
          { label: 'Unique Chains', value: uniqueCorrelations.length },
          { label: 'Avg Duration', value: `${Math.round(mockRequestTraces.reduce((s, t) => s + t.durationMs, 0) / mockRequestTraces.length)}ms` },
          { label: 'Error Spans', value: mockRequestTraces.filter(t => t.statusCode >= 400).length },
        ].map(kpi => (
          <div key={kpi.label} className="surface-elevated p-3.5">
            <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{kpi.label}</span>
            <p className="text-xl font-semibold text-foreground mt-1">{kpi.value}</p>
          </div>
        ))}
      </div>

      {/* Table */}
      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Correlation ID', 'Timestamp', 'Method', 'Path', 'Status', 'Duration', 'Service', 'Actor'].map(h => (
                <th key={h} className={cn(
                  'text-[11px] font-medium text-muted-foreground px-4 py-2.5 whitespace-nowrap',
                  h === 'Status' ? 'text-center' : h === 'Duration' ? 'text-right' : 'text-left',
                )}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {mockRequestTraces.map(trace => (
              <tr
                key={trace.id}
                onClick={() => setSelectedCorrelation(trace.correlationId)}
                className={cn(
                  'border-b last:border-0 hover:bg-muted/20 cursor-pointer transition-colors',
                  selectedCorrelation === trace.correlationId && 'bg-primary/5',
                  trace.parentSpanId && 'pl-4',
                )}
              >
                <td className="px-4 py-2.5 font-mono text-[10px] text-primary/70">{trace.correlationId}</td>
                <td className="px-4 py-2.5 font-mono text-[10px] text-muted-foreground whitespace-nowrap">{new Date(trace.timestamp).toLocaleTimeString()}</td>
                <td className="px-4 py-2.5">
                  <span className={cn(
                    'text-[10px] px-1.5 py-0.5 rounded font-mono font-bold',
                    trace.method === 'GET' ? 'bg-info/10 text-info' : trace.method === 'POST' ? 'bg-success/10 text-success' : 'bg-warning/10 text-warning',
                  )}>{trace.method}</span>
                </td>
                <td className="px-4 py-2.5 font-mono text-[10px] text-muted-foreground">
                  {trace.parentSpanId && <span className="text-muted-foreground/40 mr-1">└</span>}
                  {trace.path}
                </td>
                <td className="px-4 py-2.5 text-center">
                  <span className={cn('text-[10px] font-mono font-bold', trace.statusCode < 300 ? 'text-success' : trace.statusCode < 400 ? 'text-warning' : 'text-destructive')}>{trace.statusCode}</span>
                </td>
                <td className="px-4 py-2.5 text-right font-mono text-[10px] text-muted-foreground">{trace.durationMs}ms</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{trace.service}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{trace.actor}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="px-4 py-2 border-t border-border bg-muted/10">
          <span className="text-[10px] text-muted-foreground">{mockRequestTraces.length} spans across {uniqueCorrelations.length} chains</span>
        </div>
      </div>

      {/* Trace Chain Drawer */}
      <Sheet open={!!selectedCorrelation} onOpenChange={open => { if (!open) setSelectedCorrelation(null); }}>
        <SheetContent side="right" className="w-full sm:max-w-md overflow-y-auto p-0">
          <SheetHeader className="px-6 pt-6 pb-4 border-b">
            <SheetTitle className="text-base">Trace Chain</SheetTitle>
            <SheetDescription className="font-mono text-xs">{selectedCorrelation}</SheetDescription>
          </SheetHeader>
          <div className="p-6 space-y-1">
            {activeTraces.map((trace, i) => (
              <div key={trace.id} className="relative pl-6 pb-4">
                {i < activeTraces.length - 1 && <div className="absolute left-[9px] top-4 bottom-0 w-px bg-border" />}
                <div className={cn(
                  'absolute left-0 top-1 h-[18px] w-[18px] rounded-full border-2 flex items-center justify-center',
                  trace.statusCode < 300 ? 'border-success bg-success/10' : 'border-destructive bg-destructive/10',
                )}>
                  <div className={cn('h-1.5 w-1.5 rounded-full', trace.statusCode < 300 ? 'bg-success' : 'bg-destructive')} />
                </div>
                <div className="space-y-0.5">
                  <p className="text-xs font-medium text-foreground">{trace.service}</p>
                  <p className="font-mono text-[10px] text-muted-foreground">
                    <span className={cn(
                      'font-bold mr-1',
                      trace.method === 'POST' ? 'text-success' : trace.method === 'PATCH' ? 'text-warning' : 'text-info',
                    )}>{trace.method}</span>
                    {trace.path}
                  </p>
                  <div className="flex items-center gap-2 text-[10px]">
                    <span className={cn('font-mono font-bold', trace.statusCode < 300 ? 'text-success' : 'text-destructive')}>{trace.statusCode}</span>
                    <span className="text-muted-foreground">{trace.durationMs}ms</span>
                    <span className="text-muted-foreground">·</span>
                    <span className="text-muted-foreground">{trace.actor}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
