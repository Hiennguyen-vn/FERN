import { useState } from 'react';
import {
  Calendar, DollarSign, Users as UsersIcon, FileText, Settings,
  Download, ChevronRight, CheckCircle, XCircle, Clock,
  Loader2, Package, Info, FolderTree, Percent, Lock, Plus, Edit2, Save,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog';
import { toast } from 'sonner';
import {
  mockPayrollPeriods as initialPeriods, mockPayrollRuns as initialRuns, mockPayrollEmployees,
  mockPayrollAudit as initialAudit, mockFinanceConfig as initialConfig, mockExportJobs as initialExports,
} from '@/data/mock-finance';
import type { PayrollRun, PayrollPeriod, PayrollAuditEntry, FinanceConfigSection, ExportJob } from '@/types/finance';
import { ChartOfAccountsModule } from './ChartOfAccountsModule';
import { TaxSetupModule } from './TaxSetupModule';
import { FiscalPeriodsModule } from './FiscalPeriodsModule';

type FinanceView = 'periods' | 'runs' | 'run-detail' | 'config' | 'exports' | 'coa' | 'tax' | 'fiscal';

/* ── Status badge helpers ── */
function PeriodStatusBadge({ status }: { status: string }) {
  const s: Record<string, string> = {
    open: 'bg-success/10 text-success',
    closed: 'bg-muted text-muted-foreground',
    locked: 'bg-warning/10 text-warning',
  };
  return <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${s[status] || s.open}`}>{status}</span>;
}

function RunStatusBadge({ status }: { status: string }) {
  const s: Record<string, string> = {
    draft: 'bg-muted text-muted-foreground',
    submitted: 'bg-info/10 text-info',
    approved: 'bg-success/10 text-success',
    rejected: 'bg-destructive/10 text-destructive',
    cancelled: 'bg-muted text-muted-foreground',
    paid: 'bg-primary/10 text-primary',
  };
  return <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${s[status] || s.draft}`}>{status}</span>;
}

function ExportStatusBadge({ status }: { status: string }) {
  const s: Record<string, { cls: string; icon: React.ElementType }> = {
    queued: { cls: 'text-muted-foreground', icon: Clock },
    processing: { cls: 'text-info', icon: Loader2 },
    completed: { cls: 'text-success', icon: CheckCircle },
    failed: { cls: 'text-destructive', icon: XCircle },
  };
  const cfg = s[status] || s.queued;
  const Icon = cfg.icon;
  return (
    <span className={`inline-flex items-center gap-1 text-[10px] font-medium ${cfg.cls}`}>
      <Icon className={`h-2.5 w-2.5 ${status === 'processing' ? 'animate-spin' : ''}`} />
      {status}
    </span>
  );
}

const fmt = (n: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 0 }).format(n);

/* ═══════════════ PAYROLL PERIODS ═══════════════ */
function PayrollPeriods({ onViewRuns }: { onViewRuns: () => void }) {
  const [periods, setPeriods] = useState<PayrollPeriod[]>(initialPeriods);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState({ regionName: '', startDate: '', endDate: '' });
  const [actionTarget, setActionTarget] = useState<{ period: PayrollPeriod; action: 'close' | 'lock' } | null>(null);

  const handleCreate = () => {
    if (!form.regionName || !form.startDate || !form.endDate) { toast.error('All fields are required'); return; }
    const newPeriod: PayrollPeriod = {
      id: `pp-${Date.now()}`, regionId: `region-${Date.now()}`, regionName: form.regionName,
      startDate: form.startDate, endDate: form.endDate, status: 'open', runCount: 0,
    };
    setPeriods(prev => [...prev, newPeriod]);
    toast.success(`Period for "${form.regionName}" created`);
    setDialogOpen(false);
    setForm({ regionName: '', startDate: '', endDate: '' });
  };

  const handleAction = () => {
    if (!actionTarget) return;
    const newStatus = actionTarget.action === 'close' ? 'closed' : 'locked';
    setPeriods(prev => prev.map(p => p.id === actionTarget.period.id ? { ...p, status: newStatus as PayrollPeriod['status'] } : p));
    toast.success(`Period ${actionTarget.action === 'close' ? 'closed' : 'locked'}`);
    setActionTarget(null);
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Payroll Periods</h2>
          <p className="text-xs text-muted-foreground mt-0.5">Period management for payroll processing cycles</p>
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" className="h-8 text-xs gap-1.5" onClick={() => setDialogOpen(true)}><Plus className="h-3 w-3" /> New Period</Button>
          <Button size="sm" variant="outline" className="h-8 text-xs gap-1.5" onClick={onViewRuns}>View Payroll Runs <ChevronRight className="h-3 w-3" /></Button>
        </div>
      </div>

      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Period ID', 'Region', 'Start Date', 'End Date', 'Status', 'Runs', ''].map(h => (
                <th key={h} className={cn('text-[11px] font-medium text-muted-foreground px-4 py-2.5', h === 'Runs' ? 'text-right' : 'text-left')}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {periods.map(p => (
              <tr key={p.id} className="border-b last:border-0 hover:bg-muted/20 transition-colors">
                <td className="px-4 py-2.5 text-sm font-mono text-muted-foreground">{p.id}</td>
                <td className="px-4 py-2.5 text-sm font-medium text-foreground">{p.regionName}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{p.startDate}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{p.endDate}</td>
                <td className="px-4 py-2.5"><PeriodStatusBadge status={p.status} /></td>
                <td className="px-4 py-2.5 text-right text-sm text-muted-foreground">{p.runCount}</td>
                <td className="px-4 py-2.5">
                  <div className="flex items-center gap-1">
                    {p.status === 'open' && (
                      <Button variant="outline" size="sm" className="h-7 text-[10px] px-2 gap-1" onClick={() => setActionTarget({ period: p, action: 'close' })}>
                        <Lock className="h-3 w-3" /> Close
                      </Button>
                    )}
                    {p.status === 'closed' && (
                      <Button variant="outline" size="sm" className="h-7 text-[10px] px-2 gap-1" onClick={() => setActionTarget({ period: p, action: 'lock' })}>
                        <Lock className="h-3 w-3" /> Lock
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Create Period Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>New Payroll Period</DialogTitle>
            <DialogDescription>Create a new payroll processing period</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-1.5">
              <Label className="text-xs">Region</Label>
              <Input value={form.regionName} onChange={e => setForm(f => ({ ...f, regionName: e.target.value }))} placeholder="e.g. Central Region" className="h-9 text-sm" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Start Date</Label>
                <Input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} className="h-9 text-sm" />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">End Date</Label>
                <Input type="date" value={form.endDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} className="h-9 text-sm" />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setDialogOpen(false)}>Cancel</Button>
            <Button size="sm" onClick={handleCreate}>Create Period</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Action Confirmation */}
      <Dialog open={!!actionTarget} onOpenChange={() => setActionTarget(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{actionTarget?.action === 'close' ? 'Close Period' : 'Lock Period'}</DialogTitle>
            <DialogDescription>
              {actionTarget?.action === 'close'
                ? 'Closing this period will prevent new payroll runs. Continue?'
                : 'Locking this period is permanent and prevents any modifications. Continue?'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setActionTarget(null)}>Cancel</Button>
            <Button size="sm" onClick={handleAction}>{actionTarget?.action === 'close' ? 'Close Period' : 'Lock Period'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/* ═══════════════ PAYROLL RUN LIST ═══════════════ */
function PayrollRunList({ onSelect }: { onSelect: (r: PayrollRun) => void }) {
  const [runs, setRuns] = useState<PayrollRun[]>(initialRuns);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState({ regionName: '', periodLabel: '', employeeCount: '10', grossPay: '15000' });

  const handleCreate = () => {
    if (!form.regionName || !form.periodLabel) { toast.error('Region and period are required'); return; }
    const gross = parseFloat(form.grossPay) || 0;
    const ded = Math.round(gross * 0.2);
    const newRun: PayrollRun = {
      id: `PR-${new Date().getFullYear()}-${String(runs.length + 1).padStart(3, '0')}`,
      periodId: `pp-${Date.now()}`, regionName: form.regionName, periodLabel: form.periodLabel,
      employeeCount: parseInt(form.employeeCount) || 0, grossPay: gross, deductions: ded, netPay: gross - ded,
      status: 'draft', preparedBy: 'Current User', approvedBy: null, createdAt: new Date().toISOString().split('T')[0],
    };
    setRuns(prev => [...prev, newRun]);
    toast.success(`Payroll run ${newRun.id} created`);
    setDialogOpen(false);
    setForm({ regionName: '', periodLabel: '', employeeCount: '10', grossPay: '15000' });
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Payroll Runs</h2>
          <p className="text-xs text-muted-foreground mt-0.5">All payroll processing runs across regions</p>
        </div>
        <Button size="sm" className="h-8 text-xs gap-1.5" onClick={() => setDialogOpen(true)}><Plus className="h-3 w-3" /> New Payroll Run</Button>
      </div>

      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Run ID', 'Region', 'Period', 'Employees', 'Net Pay', 'Status', 'Prepared By', 'Approved By', ''].map(h => (
                <th key={h} className={cn('text-[11px] font-medium text-muted-foreground px-4 py-2.5', (h === 'Employees' || h === 'Net Pay') ? 'text-right' : 'text-left')}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {runs.map(r => (
              <tr key={r.id} className="border-b last:border-0 hover:bg-muted/20 cursor-pointer transition-colors" onClick={() => onSelect(r)}>
                <td className="px-4 py-2.5 text-sm font-mono font-medium text-foreground">{r.id}</td>
                <td className="px-4 py-2.5 text-sm font-medium text-foreground">{r.regionName}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{r.periodLabel}</td>
                <td className="px-4 py-2.5 text-right text-sm">{r.employeeCount}</td>
                <td className="px-4 py-2.5 text-right font-mono text-sm font-medium">{fmt(r.netPay)}</td>
                <td className="px-4 py-2.5"><RunStatusBadge status={r.status} /></td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{r.preparedBy}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{r.approvedBy || '—'}</td>
                <td className="px-4 py-2.5"><ChevronRight className="h-3.5 w-3.5 text-muted-foreground" /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Create Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>New Payroll Run</DialogTitle>
            <DialogDescription>Create a new payroll run for processing</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Region</Label>
                <Input value={form.regionName} onChange={e => setForm(f => ({ ...f, regionName: e.target.value }))} placeholder="e.g. Central Region" className="h-9 text-sm" />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Period</Label>
                <Input value={form.periodLabel} onChange={e => setForm(f => ({ ...f, periodLabel: e.target.value }))} placeholder="e.g. Jan 16-31" className="h-9 text-sm" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Employee Count</Label>
                <Input type="number" value={form.employeeCount} onChange={e => setForm(f => ({ ...f, employeeCount: e.target.value }))} className="h-9 text-sm" />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Gross Pay ($)</Label>
                <Input type="number" value={form.grossPay} onChange={e => setForm(f => ({ ...f, grossPay: e.target.value }))} className="h-9 text-sm" />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setDialogOpen(false)}>Cancel</Button>
            <Button size="sm" onClick={handleCreate}>Create Run</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/* ═══════════════ PAYROLL RUN DETAIL ═══════════════ */
function PayrollRunDetail({ run: initialRun, onBack }: { run: PayrollRun; onBack: () => void }) {
  const [run, setRun] = useState(initialRun);
  const [audit, setAudit] = useState<PayrollAuditEntry[]>(initialAudit);

  const transition = (newStatus: PayrollRun['status'], actionLabel: string) => {
    setRun(prev => ({
      ...prev,
      status: newStatus,
      approvedBy: ['approved', 'paid'].includes(newStatus) ? 'Current User' : prev.approvedBy,
    }));
    const entry: PayrollAuditEntry = {
      action: actionLabel, actor: 'Current User',
      timestamp: new Date().toISOString(),
      detail: `${actionLabel} — ${run.id} (${run.regionName})`,
    };
    setAudit(prev => [...prev, entry]);
    toast.success(`${actionLabel} successful`);
  };

  const statusActions: Record<string, { label: string; variant: 'default' | 'destructive' | 'outline'; target: PayrollRun['status'] }[]> = {
    draft: [{ label: 'Submit for Approval', variant: 'default', target: 'submitted' }],
    submitted: [
      { label: 'Approve', variant: 'default', target: 'approved' },
      { label: 'Reject', variant: 'destructive', target: 'rejected' },
    ],
    approved: [
      { label: 'Mark Paid', variant: 'default', target: 'paid' },
      { label: 'Cancel', variant: 'destructive', target: 'cancelled' },
    ],
    rejected: [{ label: 'Resubmit', variant: 'default', target: 'submitted' }],
    cancelled: [],
    paid: [],
  };
  const actions = statusActions[run.status] || [];

  return (
    <div className="space-y-5">
      <button onClick={onBack} className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-1 transition-colors">← Back to Payroll Runs</button>

      {/* Header */}
      <div className="surface-elevated p-5">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-foreground">{run.id}</h2>
            <p className="text-xs text-muted-foreground mt-1">{run.regionName} · {run.periodLabel} · Prepared by {run.preparedBy}</p>
          </div>
          <div className="flex items-center gap-2">
            <RunStatusBadge status={run.status} />
            {actions.map(a => (
              <Button key={a.label} size="sm" variant={a.variant} className="h-8 text-xs" onClick={() => transition(a.target, a.label)}>
                {a.label}
              </Button>
            ))}
          </div>
        </div>
      </div>

      {/* KPI row */}
      <div className="grid grid-cols-4 gap-3">
        {[
          { label: 'Employees', value: run.employeeCount.toString(), icon: UsersIcon },
          { label: 'Gross Pay', value: fmt(run.grossPay), icon: DollarSign },
          { label: 'Deductions', value: fmt(run.deductions), icon: DollarSign },
          { label: 'Net Pay', value: fmt(run.netPay), icon: DollarSign },
        ].map(k => (
          <div key={k.label} className="surface-elevated p-4">
            <div className="flex items-center gap-1.5 mb-2">
              <k.icon className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{k.label}</span>
            </div>
            <p className="text-xl font-semibold text-foreground">{k.value}</p>
          </div>
        ))}
      </div>

      {/* Employee Breakdown */}
      <div className="surface-elevated overflow-hidden">
        <div className="px-4 py-3 border-b">
          <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Employee Breakdown</span>
        </div>
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Employee', 'Outlet', 'Role', 'Hours', 'Base Pay', 'Overtime', 'Deductions', 'Net Pay'].map(h => (
                <th key={h} className={cn('text-[11px] font-medium text-muted-foreground px-4 py-2.5', !['Employee', 'Outlet', 'Role'].includes(h) ? 'text-right' : 'text-left')}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {mockPayrollEmployees.map(e => (
              <tr key={e.id} className="border-b last:border-0 hover:bg-muted/20 transition-colors">
                <td className="px-4 py-2.5 text-sm font-medium text-foreground">{e.name}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{e.outlet}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{e.role}</td>
                <td className="px-4 py-2.5 text-right text-sm">{e.hoursWorked}</td>
                <td className="px-4 py-2.5 text-right font-mono text-sm">{fmt(e.basePay)}</td>
                <td className="px-4 py-2.5 text-right font-mono text-sm">{fmt(e.overtime)}</td>
                <td className="px-4 py-2.5 text-right font-mono text-sm text-destructive">{fmt(e.deductions)}</td>
                <td className="px-4 py-2.5 text-right font-mono text-sm font-medium">{fmt(e.netPay)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Audit Timeline */}
      <div className="surface-elevated overflow-hidden">
        <div className="px-4 py-3 border-b">
          <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">Audit Timeline</span>
        </div>
        <div className="p-5">
          <div className="relative pl-6 space-y-4">
            <div className="absolute left-2.5 top-1 bottom-1 w-px bg-border" />
            {audit.map((a, i) => (
              <div key={i} className="relative">
                <div className="absolute -left-[14px] top-1 h-2 w-2 rounded-full bg-primary border-2 border-background" />
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-foreground">{a.action}</span>
                    <span className="text-[10px] text-muted-foreground">by {a.actor}</span>
                    <span className="text-[10px] text-muted-foreground">· {new Date(a.timestamp).toLocaleString()}</span>
                  </div>
                  <p className="text-[11px] text-muted-foreground mt-0.5">{a.detail}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════ FINANCE CONFIG ═══════════════ */
function FinanceConfig() {
  const [config, setConfig] = useState<FinanceConfigSection[]>(initialConfig);
  const [editingSection, setEditingSection] = useState<string | null>(null);
  const [editValues, setEditValues] = useState<Record<string, string>>({});

  const startEdit = (section: FinanceConfigSection) => {
    const values: Record<string, string> = {};
    section.settings.forEach(s => { values[s.key] = s.value; });
    setEditValues(values);
    setEditingSection(section.id);
  };

  const saveSection = (sectionId: string) => {
    setConfig(prev => prev.map(sec => sec.id === sectionId ? {
      ...sec,
      settings: sec.settings.map(s => ({ ...s, value: editValues[s.key] ?? s.value })),
    } : sec));
    setEditingSection(null);
    toast.success('Configuration saved');
  };

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Finance Configuration</h2>
        <p className="text-xs text-muted-foreground mt-0.5">System-wide finance and payroll settings</p>
      </div>

      {config.map(section => {
        const isEditing = editingSection === section.id;
        return (
          <div key={section.id} className="surface-elevated overflow-hidden">
            <div className="px-4 py-3 border-b flex items-center justify-between">
              <div>
                <span className="text-sm font-semibold text-foreground">{section.label}</span>
                <p className="text-[11px] text-muted-foreground mt-0.5">{section.description}</p>
              </div>
              {!isEditing ? (
                <Button variant="ghost" size="sm" className="h-7 text-[10px] gap-1" onClick={() => startEdit(section)}>
                  <Edit2 className="h-3 w-3" /> Edit
                </Button>
              ) : (
                <div className="flex items-center gap-1">
                  <Button variant="outline" size="sm" className="h-7 text-[10px]" onClick={() => setEditingSection(null)}>Cancel</Button>
                  <Button size="sm" className="h-7 text-[10px] gap-1" onClick={() => saveSection(section.id)}>
                    <Save className="h-3 w-3" /> Save
                  </Button>
                </div>
              )}
            </div>
            <div className="p-4 space-y-1">
              {section.settings.map(s => (
                <div key={s.key} className="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-muted/20 transition-colors">
                  <div>
                    <p className="text-sm font-medium text-foreground">{s.label}</p>
                    <p className="text-[10px] font-mono text-muted-foreground mt-0.5">{s.key}</p>
                  </div>
                  <div className="text-right">
                    {isEditing ? (
                      s.type === 'boolean' ? (
                        <Switch
                          checked={editValues[s.key] === 'true'}
                          onCheckedChange={v => setEditValues(prev => ({ ...prev, [s.key]: v ? 'true' : 'false' }))}
                        />
                      ) : s.type === 'select' ? (
                        <select
                          value={editValues[s.key] || s.value}
                          onChange={e => setEditValues(prev => ({ ...prev, [s.key]: e.target.value }))}
                          className="h-8 rounded-md border border-input bg-background px-2 text-xs"
                        >
                          {s.options?.map(o => <option key={o} value={o}>{o}</option>)}
                        </select>
                      ) : (
                        <Input
                          type={s.type === 'number' ? 'number' : 'text'}
                          value={editValues[s.key] || ''}
                          onChange={e => setEditValues(prev => ({ ...prev, [s.key]: e.target.value }))}
                          className="h-8 w-32 text-xs text-right"
                        />
                      )
                    ) : (
                      s.type === 'boolean' ? (
                        <span className={cn('text-[10px] font-medium px-2 py-0.5 rounded-full', s.value === 'true' ? 'bg-success/10 text-success' : 'bg-muted text-muted-foreground')}>
                          {s.value === 'true' ? 'Enabled' : 'Disabled'}
                        </span>
                      ) : s.type === 'select' ? (
                        <span className="text-xs text-foreground bg-muted px-2 py-0.5 rounded">{s.value}</span>
                      ) : (
                        <code className="text-xs text-foreground bg-muted px-2 py-0.5 rounded font-mono">{s.value}</code>
                      )
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}

/* ═══════════════ EXPORT CENTER ═══════════════ */
function ExportCenter() {
  const [exports, setExports] = useState<ExportJob[]>(initialExports);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState({ module: 'Finance', label: '', scope: 'System-wide' });

  const MODULES = ['Finance', 'Reports', 'Inventory', 'Audit', 'POS', 'Procurement', 'HR'];

  const handleRequest = () => {
    if (!form.label) { toast.error('Label is required'); return; }
    const newJob: ExportJob = {
      id: `exp-${Date.now()}`, module: form.module, label: form.label,
      requester: 'Current User', scope: form.scope,
      requestedAt: new Date().toISOString(), completedAt: null,
      status: 'queued', fileSize: null,
    };
    setExports(prev => [newJob, ...prev]);
    toast.success('Export requested');
    setDialogOpen(false);
    setForm({ module: 'Finance', label: '', scope: 'System-wide' });
  };

  const retryExport = (id: string) => {
    setExports(prev => prev.map(e => e.id === id ? { ...e, status: 'queued' as const } : e));
    toast.success('Export requeued');
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Export Center</h2>
          <p className="text-xs text-muted-foreground mt-0.5">Governed, scope-aware data exports with full audit trail</p>
        </div>
        <Button size="sm" className="h-8 text-xs gap-1.5" onClick={() => setDialogOpen(true)}><Plus className="h-3 w-3" /> Request Export</Button>
      </div>

      <div className="flex items-start gap-2.5 p-3 rounded-md bg-info/5 border border-info/10">
        <Info className="h-3.5 w-3.5 text-info mt-0.5 flex-shrink-0" />
        <p className="text-[11px] text-info leading-relaxed">
          All data exports are permission-gated, scope-aware, and logged in the audit trail.
        </p>
      </div>

      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Job ID', 'Module', 'Label', 'Requester', 'Scope', 'Requested', 'Status', 'Size', ''].map(h => (
                <th key={h} className={cn('text-[11px] font-medium text-muted-foreground px-4 py-2.5', h === 'Size' ? 'text-right' : 'text-left')}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {exports.map(j => (
              <tr key={j.id} className="border-b last:border-0 hover:bg-muted/20 transition-colors">
                <td className="px-4 py-2.5 font-mono text-sm text-muted-foreground">{j.id}</td>
                <td className="px-4 py-2.5 text-sm font-medium text-foreground">{j.module}</td>
                <td className="px-4 py-2.5 text-sm text-foreground">{j.label}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{j.requester}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{j.scope}</td>
                <td className="px-4 py-2.5 text-xs text-muted-foreground">{new Date(j.requestedAt).toLocaleDateString()}</td>
                <td className="px-4 py-2.5"><ExportStatusBadge status={j.status} /></td>
                <td className="px-4 py-2.5 text-right text-xs text-muted-foreground">{j.fileSize || '—'}</td>
                <td className="px-4 py-2.5">
                  {j.status === 'completed' && (
                    <Button size="sm" variant="ghost" className="h-7 text-[10px] px-2 gap-1">
                      <Download className="h-3 w-3" />Download
                    </Button>
                  )}
                  {j.status === 'failed' && (
                    <Button size="sm" variant="ghost" className="h-7 text-[10px] px-2 gap-1" onClick={() => retryExport(j.id)}>
                      Retry
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Request Export Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Request Export</DialogTitle>
            <DialogDescription>Create a new data export request</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-1.5">
              <Label className="text-xs">Module</Label>
              <select value={form.module} onChange={e => setForm(f => ({ ...f, module: e.target.value }))} className="flex h-9 w-full rounded-md border border-input bg-background px-3 text-sm">
                {MODULES.map(m => <option key={m} value={m}>{m}</option>)}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Label</Label>
              <Input value={form.label} onChange={e => setForm(f => ({ ...f, label: e.target.value }))} placeholder="e.g. Payroll Run PR-2025-001" className="h-9 text-sm" />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs">Scope</Label>
              <Input value={form.scope} onChange={e => setForm(f => ({ ...f, scope: e.target.value }))} placeholder="e.g. Central Region" className="h-9 text-sm" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setDialogOpen(false)}>Cancel</Button>
            <Button size="sm" onClick={handleRequest}>Request Export</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/* ═══════════════ MAIN FINANCE MODULE ═══════════════ */
const FINANCE_TABS: { key: FinanceView; label: string; icon: React.ElementType }[] = [
  { key: 'periods', label: 'Payroll Periods', icon: Calendar },
  { key: 'runs', label: 'Payroll Runs', icon: FileText },
  { key: 'coa', label: 'Chart of Accounts', icon: FolderTree },
  { key: 'tax', label: 'Tax Setup', icon: Percent },
  { key: 'fiscal', label: 'Fiscal Periods', icon: Lock },
  { key: 'config', label: 'Config', icon: Settings },
  { key: 'exports', label: 'Exports', icon: Download },
];

export function FinanceModule() {
  const [view, setView] = useState<FinanceView>('periods');
  const [selectedRun, setSelectedRun] = useState<PayrollRun | null>(null);

  const activeTab = view === 'run-detail' ? 'runs' : view;

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {FINANCE_TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => { setView(tab.key); setSelectedRun(null); }}
            className={cn(
              'flex items-center gap-1.5 px-4 py-3 text-xs font-medium border-b-2 transition-colors',
              activeTab === tab.key
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            <tab.icon className="h-3.5 w-3.5" />
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="p-6 space-y-5">
          {view === 'periods' && <PayrollPeriods onViewRuns={() => setView('runs')} />}
          {view === 'runs' && !selectedRun && <PayrollRunList onSelect={r => { setSelectedRun(r); setView('run-detail'); }} />}
          {view === 'run-detail' && selectedRun && <PayrollRunDetail run={selectedRun} onBack={() => { setView('runs'); setSelectedRun(null); }} />}
          {view === 'coa' && <ChartOfAccountsModule />}
          {view === 'tax' && <TaxSetupModule />}
          {view === 'fiscal' && <FiscalPeriodsModule />}
          {view === 'config' && <FinanceConfig />}
          {view === 'exports' && <ExportCenter />}
        </div>
      </div>
    </div>
  );
}
