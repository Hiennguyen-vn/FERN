import { useState, useMemo } from 'react';
import {
  Search, Layers, ChevronRight, Filter, Clock, Users, DollarSign,
  AlertTriangle, CheckCircle2, Flag, Zap, CalendarDays,
  User, MapPin, Timer, Ban, FileText,
} from 'lucide-react';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription,
} from '@/components/ui/sheet';
import {
  mockAttendanceRecords, ATTENDANCE_STATUS_CONFIG,
  EXCEPTION_CONFIG, APPROVAL_STATUS_CONFIG,
} from '@/data/mock-hr';
import type { AttendanceRecord } from '@/types/hr';
import { PayrollModule } from './PayrollModule';
import { ContractModule } from './ContractModule';

type HRTab = 'attendance' | 'payroll' | 'contracts';

export function HRModule() {
  const [activeTab, setActiveTab] = useState<HRTab>('attendance');

  return (
    <div className="flex flex-col h-full animate-fade-in">
      <div className="border-b bg-card px-6 flex items-center gap-0 flex-shrink-0">
        {([
          { key: 'attendance' as HRTab, label: 'Attendance Review', icon: Clock, count: mockAttendanceRecords.length },
          { key: 'payroll' as HRTab, label: 'Payroll & Payslips', icon: DollarSign, count: 6 },
          { key: 'contracts' as HRTab, label: 'Hợp đồng', icon: FileText, count: 10 },
        ]).map(tab => (
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
              activeTab === tab.key ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground',
            )}>{tab.count}</span>
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto">
        {activeTab === 'attendance' && <AttendanceReviewQueue />}
        {activeTab === 'payroll' && <div className="p-6"><PayrollModule /></div>}
        {activeTab === 'contracts' && <ContractModule />}
      </div>
    </div>
  );
}

function AttendanceReviewQueue() {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [approvalFilter, setApprovalFilter] = useState<string>('all');
  const [dateFilter, setDateFilter] = useState<string>('2026-04-04');
  const [selectedRecord, setSelectedRecord] = useState<AttendanceRecord | null>(null);

  const filtered = useMemo(() => mockAttendanceRecords.filter(r => {
    if (dateFilter && r.date !== dateFilter) return false;
    if (statusFilter !== 'all' && r.attendanceStatus !== statusFilter) return false;
    if (approvalFilter !== 'all' && r.approvalStatus !== approvalFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      return r.employeeName.toLowerCase().includes(q) ||
        r.employeeRole.toLowerCase().includes(q) ||
        r.employeeId.toLowerCase().includes(q);
    }
    return true;
  }), [search, statusFilter, approvalFilter, dateFilter]);

  const kpis = useMemo(() => {
    const today = mockAttendanceRecords.filter(r => r.date === dateFilter);
    const pending = today.filter(r => r.approvalStatus === 'pending_review').length;
    const flagged = today.filter(r => r.approvalStatus === 'flagged').length;
    const exceptions = today.filter(r => r.exception !== 'none').length;
    const present = today.filter(r => r.attendanceStatus === 'present' || r.attendanceStatus === 'late' || r.attendanceStatus === 'half_day').length;
    return [
      { label: 'Pending Review', value: pending, icon: Clock, color: pending > 0 ? 'text-warning' : 'text-foreground', sub: 'needs attention' },
      { label: 'Flagged', value: flagged, icon: Flag, color: flagged > 0 ? 'text-destructive' : 'text-foreground', sub: 'requires investigation' },
      { label: 'Exceptions', value: exceptions, icon: AlertTriangle, color: exceptions > 0 ? 'text-warning' : 'text-foreground', sub: 'this date' },
      { label: 'Present / Scheduled', value: `${present}/${today.length}`, icon: Users, color: 'text-foreground', sub: `${today.length > 0 ? Math.round((present / today.length) * 100) : 0}% attendance` },
    ];
  }, [dateFilter]);

  const activeFilters = [statusFilter, approvalFilter].filter(f => f !== 'all').length + (dateFilter !== '2026-04-04' ? 1 : 0);

  return (
    <div className="p-6 space-y-5 animate-fade-in">
      {/* Header */}
      <div>
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground mb-1">
          <Layers className="h-3 w-3" />
          <span>Human Resources</span>
          <ChevronRight className="h-3 w-3" />
          <span className="text-foreground font-medium">Attendance Review</span>
        </div>
        <h2 className="text-lg font-semibold text-foreground">Attendance Review Queue</h2>
        <p className="text-xs text-muted-foreground mt-0.5">Review, approve, and flag daily attendance records and shift exceptions</p>
      </div>

      {/* KPI Strip */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {kpis.map(kpi => (
          <div key={kpi.label} className="surface-elevated p-3.5">
            <div className="flex items-center gap-1.5 mb-2">
              <kpi.icon className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{kpi.label}</span>
            </div>
            <p className={cn('text-xl font-semibold', kpi.color)}>{kpi.value}</p>
            <p className="text-[10px] text-muted-foreground mt-0.5">{kpi.sub}</p>
          </div>
        ))}
      </div>

      {/* Filter Bar */}
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
          <Input placeholder="Search employee name, role, ID…" value={search} onChange={e => setSearch(e.target.value)} className="pl-9 h-8 text-sm" />
        </div>
        <input type="date" value={dateFilter} onChange={e => setDateFilter(e.target.value)} className="text-xs px-3 py-1.5 border rounded-md bg-card text-foreground h-8" />
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="text-xs px-3 py-1.5 border rounded-md bg-card text-foreground h-8">
          <option value="all">All Statuses</option>
          {Object.entries(ATTENDANCE_STATUS_CONFIG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        <select value={approvalFilter} onChange={e => setApprovalFilter(e.target.value)} className="text-xs px-3 py-1.5 border rounded-md bg-card text-foreground h-8">
          <option value="all">All Approvals</option>
          {Object.entries(APPROVAL_STATUS_CONFIG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        {(activeFilters > 0 || search) && (
          <button onClick={() => { setStatusFilter('all'); setApprovalFilter('all'); setDateFilter('2026-04-04'); setSearch(''); }} className="text-[10px] text-primary hover:underline">Clear all</button>
        )}
      </div>

      {/* Attendance Table */}
      <div className="surface-elevated overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/30">
              {['Employee', 'Role', 'Shift', 'Clock In', 'Clock Out', 'Status', 'Exception', 'Approval', ''].map(h => (
                <th key={h} className="text-left text-[11px] font-medium text-muted-foreground px-4 py-2.5 whitespace-nowrap">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr><td colSpan={9} className="px-4 py-16 text-center">
                <Search className="h-8 w-8 mx-auto mb-2 opacity-30 text-muted-foreground" />
                <p className="text-sm font-medium text-muted-foreground">No attendance records found</p>
                <p className="text-xs text-muted-foreground mt-1">Try adjusting the date or filters</p>
              </td></tr>
            ) : filtered.map(record => {
              const sCfg = ATTENDANCE_STATUS_CONFIG[record.attendanceStatus];
              const eCfg = EXCEPTION_CONFIG[record.exception];
              const aCfg = APPROVAL_STATUS_CONFIG[record.approvalStatus];
              const isActionable = record.approvalStatus === 'pending_review' || record.approvalStatus === 'flagged';
              return (
                <tr
                  key={record.id}
                  onClick={() => setSelectedRecord(record)}
                  className={cn(
                    'border-b last:border-0 hover:bg-muted/20 cursor-pointer transition-colors',
                    selectedRecord?.id === record.id && 'bg-primary/5',
                    record.approvalStatus === 'flagged' && 'bg-destructive/[0.02]',
                    record.approvalStatus === 'pending_review' && 'bg-warning/[0.02]',
                  )}
                >
                  <td className="px-4 py-2.5">
                    <p className="text-sm font-medium text-foreground">{record.employeeName}</p>
                    <p className="text-[10px] text-muted-foreground font-mono">{record.employeeId}</p>
                  </td>
                  <td className="px-4 py-2.5 text-xs text-muted-foreground">{record.employeeRole}</td>
                  <td className="px-4 py-2.5 font-mono text-[11px] text-muted-foreground">{record.scheduledShift}</td>
                  <td className="px-4 py-2.5 font-mono text-[11px] text-foreground">{record.clockIn || <span className="text-destructive">—</span>}</td>
                  <td className="px-4 py-2.5 font-mono text-[11px] text-foreground">{record.clockOut || <span className="text-warning">—</span>}</td>
                  <td className="px-4 py-2.5">
                    <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', sCfg.class)}>{sCfg.label}</span>
                  </td>
                  <td className="px-4 py-2.5">
                    {record.exception !== 'none' ? (
                      <div className="flex items-center gap-1">
                        <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', eCfg.class)}>{eCfg.label}</span>
                        {record.exceptionMinutes && <span className="text-[9px] text-muted-foreground">{record.exceptionMinutes}m</span>}
                      </div>
                    ) : (
                      <span className="text-[10px] text-muted-foreground">—</span>
                    )}
                  </td>
                  <td className="px-4 py-2.5">
                    <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', aCfg.class)}>{aCfg.label}</span>
                  </td>
                  <td className="px-4 py-2.5">
                    {isActionable && <div className="h-2 w-2 rounded-full bg-warning animate-pulse" />}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <div className="px-4 py-2 border-t border-border bg-muted/10 flex items-center justify-between">
          <span className="text-[10px] text-muted-foreground">Showing {filtered.length} of {mockAttendanceRecords.length} records</span>
          <span className="text-[10px] text-muted-foreground">Date: {dateFilter}</span>
        </div>
      </div>

      {/* Detail Drawer */}
      <Sheet open={!!selectedRecord} onOpenChange={open => { if (!open) setSelectedRecord(null); }}>
        <SheetContent side="right" className="w-full sm:max-w-lg overflow-y-auto p-0">
          <SheetHeader className="px-6 pt-6 pb-4 border-b">
            <div className="flex items-center gap-2 mb-1">
              {selectedRecord && (
                <>
                  <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', ATTENDANCE_STATUS_CONFIG[selectedRecord.attendanceStatus].class)}>
                    {ATTENDANCE_STATUS_CONFIG[selectedRecord.attendanceStatus].label}
                  </span>
                  <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', APPROVAL_STATUS_CONFIG[selectedRecord.approvalStatus].class)}>
                    {APPROVAL_STATUS_CONFIG[selectedRecord.approvalStatus].label}
                  </span>
                  {selectedRecord.exception !== 'none' && (
                    <span className={cn('text-[10px] px-2 py-0.5 rounded-full font-medium', EXCEPTION_CONFIG[selectedRecord.exception].class)}>
                      {EXCEPTION_CONFIG[selectedRecord.exception].label}
                    </span>
                  )}
                </>
              )}
            </div>
            <SheetTitle className="text-base">{selectedRecord?.employeeName}</SheetTitle>
            <SheetDescription>
              {selectedRecord?.employeeRole} · {selectedRecord?.employeeId}
            </SheetDescription>
          </SheetHeader>

          {selectedRecord && (
            <div className="p-6 space-y-6">
              {/* Shift Timeline */}
              <div className="p-4 rounded-lg bg-muted/20 border">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">Shift Timeline</span>
                  <span className="font-mono text-[11px] text-muted-foreground">{selectedRecord.date}</span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="text-center">
                    <p className="text-[9px] text-muted-foreground uppercase">Scheduled</p>
                    <p className="font-mono text-sm font-medium text-muted-foreground mt-0.5">{selectedRecord.scheduledShift}</p>
                  </div>
                  <div className="flex-1 h-px bg-border" />
                  <div className="text-center">
                    <p className="text-[9px] text-muted-foreground uppercase">Clock In</p>
                    <p className={cn(
                      'font-mono text-sm font-medium mt-0.5',
                      selectedRecord.clockIn ? (selectedRecord.exception === 'late_arrival' ? 'text-warning' : 'text-success') : 'text-destructive',
                    )}>{selectedRecord.clockIn || 'N/A'}</p>
                  </div>
                  <div className="flex-1 h-px bg-border" />
                  <div className="text-center">
                    <p className="text-[9px] text-muted-foreground uppercase">Clock Out</p>
                    <p className={cn(
                      'font-mono text-sm font-medium mt-0.5',
                      selectedRecord.clockOut ? (selectedRecord.exception === 'overtime' ? 'text-info' : 'text-success') : 'text-warning',
                    )}>{selectedRecord.clockOut || 'N/A'}</p>
                  </div>
                </div>
                {selectedRecord.exceptionMinutes && (
                  <div className="mt-3 pt-3 border-t border-border flex items-center gap-2">
                    <Timer className="h-3 w-3 text-muted-foreground" />
                    <span className="text-[10px] text-muted-foreground">Exception duration:</span>
                    <span className={cn('font-mono text-xs font-medium',
                      selectedRecord.exception === 'overtime' ? 'text-info' : 'text-warning',
                    )}>{selectedRecord.exceptionMinutes} minutes</span>
                  </div>
                )}
              </div>

              {/* Context Grid */}
              <div>
                <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">Record Details</span>
                <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-3">
                  {[
                    ['Outlet', selectedRecord.outletName],
                    ['Date', selectedRecord.date],
                    ['Scheduled Shift', selectedRecord.scheduledShift],
                    ['Attendance', ATTENDANCE_STATUS_CONFIG[selectedRecord.attendanceStatus].label],
                    ['Exception', selectedRecord.exception !== 'none' ? EXCEPTION_CONFIG[selectedRecord.exception].label : 'None'],
                    ['Approval', APPROVAL_STATUS_CONFIG[selectedRecord.approvalStatus].label],
                    ...(selectedRecord.reviewedBy ? [['Reviewed By', selectedRecord.reviewedBy]] : []),
                    ...(selectedRecord.reviewedAt ? [['Reviewed At', new Date(selectedRecord.reviewedAt).toLocaleString()]] : []),
                  ].map(([label, value]) => (
                    <div key={label}>
                      <p className="text-[10px] text-muted-foreground">{label}</p>
                      <p className="text-xs font-medium text-foreground mt-0.5">{value}</p>
                    </div>
                  ))}
                </div>
              </div>

              {/* Notes */}
              {selectedRecord.notes && (
                <div className="p-3.5 rounded-lg bg-muted/30 border">
                  <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">Notes / Context</span>
                  <p className="text-xs text-foreground mt-1.5 leading-relaxed">{selectedRecord.notes}</p>
                </div>
              )}

              {/* Flagged Warning */}
              {selectedRecord.approvalStatus === 'flagged' && (
                <div className="p-3.5 rounded-lg border border-destructive/20 bg-destructive/5">
                  <div className="flex items-center gap-1.5 mb-1.5">
                    <Flag className="h-3 w-3 text-destructive" />
                    <span className="text-[10px] font-semibold text-destructive uppercase tracking-wide">Flagged for Investigation</span>
                  </div>
                  <p className="text-xs text-foreground leading-relaxed">
                    This record has been flagged by the system or a reviewer. Please investigate and take appropriate action.
                  </p>
                </div>
              )}

              {/* Action Bar */}
              {(selectedRecord.approvalStatus === 'pending_review' || selectedRecord.approvalStatus === 'flagged') && (
                <div className="space-y-2 pt-2">
                  <div className="flex items-center gap-2">
                    <button className="flex-1 h-9 rounded-md bg-success text-success-foreground text-xs font-medium flex items-center justify-center gap-1.5 hover:bg-success/90 transition-colors">
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      Approve
                    </button>
                    <button className="flex-1 h-9 rounded-md border border-destructive/30 text-destructive text-xs font-medium flex items-center justify-center gap-1.5 hover:bg-destructive/5 transition-colors">
                      <Flag className="h-3.5 w-3.5" />
                      {selectedRecord.approvalStatus === 'flagged' ? 'Escalate' : 'Flag'}
                    </button>
                  </div>
                  {selectedRecord.approvalStatus === 'flagged' && (
                    <button className="w-full h-9 rounded-md border border-border text-muted-foreground text-xs font-medium flex items-center justify-center gap-1.5 hover:bg-muted/50 transition-colors">
                      <Ban className="h-3.5 w-3.5" />
                      Mark as Unexcused Absence
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}
