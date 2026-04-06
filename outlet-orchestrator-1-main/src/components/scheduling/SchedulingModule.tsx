import { useState, useMemo } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import {
  CalendarClock, ArrowLeftRight, Clock, Timer, Users, AlertTriangle,
  CheckCircle, XCircle, Plus, Search, Edit2, Trash2, UserPlus, UserMinus,
} from 'lucide-react';
import { mockShifts as initialShifts, mockSwapRequests, mockTimeOffRequests, mockOvertimeSummary, mockEmployeePool } from '@/data/mock-scheduling';
import type { Shift, ShiftAssignment, ShiftType } from '@/types/scheduling';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

/* ─── Config ─── */
const shiftStatusConfig: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  scheduled: { label: 'Scheduled', variant: 'outline' },
  in_progress: { label: 'In Progress', variant: 'default' },
  completed: { label: 'Completed', variant: 'secondary' },
  cancelled: { label: 'Cancelled', variant: 'destructive' },
};

const assignmentStatusConfig: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  assigned: { label: 'Assigned', variant: 'outline' },
  confirmed: { label: 'Confirmed', variant: 'secondary' },
  checked_in: { label: 'Checked In', variant: 'default' },
  checked_out: { label: 'Checked Out', variant: 'secondary' },
  missed: { label: 'Missed', variant: 'destructive' },
};

const shiftTypeLabel: Record<string, string> = {
  morning: 'Morning', afternoon: 'Afternoon', evening: 'Evening', night: 'Night', split: 'Split',
};

const timeOffTypeLabel: Record<string, string> = {
  annual: 'Annual', sick: 'Sick', personal: 'Personal', unpaid: 'Unpaid',
};

const statusBadge = (s: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
  const map: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    pending: 'outline', approved: 'default', rejected: 'destructive', cancelled: 'secondary',
  };
  return map[s] ?? 'secondary';
};

/* ─── Shifts Tab ─── */
function ShiftsTab() {
  const [shifts, setShifts] = useState<Shift[]>(initialShifts);
  const [search, setSearch] = useState('');
  const [outletFilter, setOutletFilter] = useState('all');
  const [selected, setSelected] = useState<Shift | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [addEmployeeOpen, setAddEmployeeOpen] = useState(false);
  const [selectedEmployees, setSelectedEmployees] = useState<string[]>([]);

  const [formData, setFormData] = useState({
    outletName: '', date: '', shiftType: 'morning' as string,
    startTime: '', endTime: '', breakMinutes: 60, notes: '',
    assignedEmployees: [] as string[],
  });

  const outlets = useMemo(() => [...new Set(shifts.map(s => s.outletName))], [shifts]);

  const filtered = useMemo(() => shifts.filter(s => {
    if (outletFilter !== 'all' && s.outletName !== outletFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      const matchOutlet = s.outletName.toLowerCase().includes(q);
      const matchEmployee = s.assignments.some(a => a.employeeName.toLowerCase().includes(q));
      return matchOutlet || matchEmployee;
    }
    return true;
  }), [shifts, search, outletFilter]);

  const todayShifts = shifts.filter(s => s.date === '2026-04-05');
  const onDuty = todayShifts.filter(s => s.status === 'in_progress').reduce((sum, s) => sum + s.assignments.filter(a => a.status === 'checked_in').length, 0);
  const totalAssignments = shifts.reduce((sum, s) => sum + s.assignments.length, 0);

  const openCreate = () => {
    setEditingId(null);
    setFormData({ outletName: '', date: '', shiftType: 'morning', startTime: '', endTime: '', breakMinutes: 60, notes: '', assignedEmployees: [] });
    setFormOpen(true);
  };

  const openEdit = (s: Shift) => {
    setEditingId(s.id);
    setFormData({
      outletName: s.outletName, date: s.date, shiftType: s.shiftType,
      startTime: s.startTime, endTime: s.endTime, breakMinutes: s.breakMinutes,
      notes: s.notes || '', assignedEmployees: s.assignments.map(a => a.employeeId),
    });
    setFormOpen(true);
    setSelected(null);
  };

  const toggleEmployee = (empId: string) => {
    setFormData(prev => ({
      ...prev,
      assignedEmployees: prev.assignedEmployees.includes(empId)
        ? prev.assignedEmployees.filter(id => id !== empId)
        : [...prev.assignedEmployees, empId],
    }));
  };

  const handleSave = () => {
    if (!formData.outletName.trim() || !formData.date || !formData.startTime || !formData.endTime) {
      toast.error('Outlet, date, and time are required');
      return;
    }
    if (formData.assignedEmployees.length === 0) {
      toast.error('Assign at least one employee');
      return;
    }

    const buildAssignments = (existingAssignments: ShiftAssignment[] = []): ShiftAssignment[] => {
      return formData.assignedEmployees.map(empId => {
        const existing = existingAssignments.find(a => a.employeeId === empId);
        if (existing) return existing;
        const emp = mockEmployeePool.find(e => e.id === empId);
        return {
          id: `asg-${Date.now()}-${empId}`,
          employeeId: empId,
          employeeName: emp?.name || 'Unknown',
          employeeRole: emp?.role || 'Staff',
          status: 'assigned' as const,
          overtimeMinutes: 0,
        };
      });
    };

    if (editingId) {
      setShifts(prev => prev.map(s => s.id === editingId ? {
        ...s,
        outletName: formData.outletName,
        date: formData.date,
        shiftType: formData.shiftType as ShiftType,
        startTime: formData.startTime,
        endTime: formData.endTime,
        breakMinutes: formData.breakMinutes,
        notes: formData.notes || undefined,
        assignments: buildAssignments(s.assignments),
      } : s));
      toast.success('Shift updated');
    } else {
      const newShift: Shift = {
        id: `shift-${Date.now()}`,
        outletId: `outlet-${Date.now()}`,
        outletName: formData.outletName,
        date: formData.date,
        shiftType: formData.shiftType as ShiftType,
        startTime: formData.startTime,
        endTime: formData.endTime,
        breakMinutes: formData.breakMinutes,
        notes: formData.notes || undefined,
        status: 'scheduled',
        assignments: buildAssignments(),
      };
      setShifts(prev => [newShift, ...prev]);
      toast.success('Shift created');
    }
    setFormOpen(false);
  };

  const handleDelete = (id: string) => {
    setShifts(prev => prev.filter(s => s.id !== id));
    setDeleteConfirm(null);
    setSelected(null);
    toast.success('Shift deleted');
  };

  const handleAddEmployeeToShift = () => {
    if (!selected || selectedEmployees.length === 0) return;
    setShifts(prev => prev.map(s => {
      if (s.id !== selected.id) return s;
      const newAssignments = selectedEmployees
        .filter(empId => !s.assignments.some(a => a.employeeId === empId))
        .map(empId => {
          const emp = mockEmployeePool.find(e => e.id === empId);
          return {
            id: `asg-${Date.now()}-${empId}`,
            employeeId: empId,
            employeeName: emp?.name || 'Unknown',
            employeeRole: emp?.role || 'Staff',
            status: 'assigned' as const,
            overtimeMinutes: 0,
          };
        });
      const updated = { ...s, assignments: [...s.assignments, ...newAssignments] };
      setSelected(updated);
      return updated;
    }));
    setSelectedEmployees([]);
    setAddEmployeeOpen(false);
    toast.success('Employees added to shift');
  };

  const handleRemoveAssignment = (shiftId: string, assignmentId: string) => {
    setShifts(prev => prev.map(s => {
      if (s.id !== shiftId) return s;
      const updated = { ...s, assignments: s.assignments.filter(a => a.id !== assignmentId) };
      setSelected(updated);
      return updated;
    }));
    toast.success('Employee removed from shift');
  };

  return (
    <div className="space-y-6">
      {/* KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10"><CalendarClock className="h-5 w-5 text-primary" /></div>
            <div><p className="text-2xl font-bold">{todayShifts.length}</p><p className="text-xs text-muted-foreground">Shifts Today</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10"><Users className="h-5 w-5 text-primary" /></div>
            <div><p className="text-2xl font-bold">{onDuty}</p><p className="text-xs text-muted-foreground">On Duty Now</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-muted"><UserPlus className="h-5 w-5 text-muted-foreground" /></div>
            <div><p className="text-2xl font-bold">{totalAssignments}</p><p className="text-xs text-muted-foreground">Total Assignments</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-muted"><Clock className="h-5 w-5 text-muted-foreground" /></div>
            <div><p className="text-2xl font-bold">{shifts.length}</p><p className="text-xs text-muted-foreground">Total Shifts</p></div>
          </CardContent>
        </Card>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search outlet or employee…" className="pl-9" value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <Select value={outletFilter} onValueChange={setOutletFilter}>
          <SelectTrigger className="w-[200px]"><SelectValue placeholder="All Outlets" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Outlets</SelectItem>
            {outlets.map(o => <SelectItem key={o} value={o}>{o}</SelectItem>)}
          </SelectContent>
        </Select>
        <Button size="sm" className="gap-1.5" onClick={openCreate}><Plus className="h-4 w-4" /> New Shift</Button>
      </div>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Outlet</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Shift</TableHead>
                <TableHead>Hours</TableHead>
                <TableHead>Staff</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.length === 0 ? (
                <TableRow><TableCell colSpan={6} className="text-center py-12">
                  <Search className="h-8 w-8 mx-auto mb-2 opacity-30 text-muted-foreground" />
                  <p className="text-sm text-muted-foreground">No shifts found</p>
                </TableCell></TableRow>
              ) : filtered.map(s => (
                <TableRow key={s.id} className="cursor-pointer hover:bg-muted/50" onClick={() => setSelected(s)}>
                  <TableCell className="font-medium">{s.outletName}</TableCell>
                  <TableCell>{s.date}</TableCell>
                  <TableCell><Badge variant="outline">{shiftTypeLabel[s.shiftType]}</Badge></TableCell>
                  <TableCell>{s.startTime} – {s.endTime}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1.5">
                      <Users className="h-3.5 w-3.5 text-muted-foreground" />
                      <span className="font-medium">{s.assignments.length}</span>
                      <span className="text-xs text-muted-foreground">
                        {s.assignments.length > 0 && `(${s.assignments.map(a => a.employeeName.split(' ')[0]).join(', ')})`}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={shiftStatusConfig[s.status]?.variant ?? 'secondary'}>
                      {shiftStatusConfig[s.status]?.label ?? s.status}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Detail Sheet */}
      <Sheet open={!!selected} onOpenChange={open => { if (!open) setSelected(null); }}>
        <SheetContent className="w-full sm:max-w-lg overflow-y-auto">
          <SheetHeader>
            <SheetTitle>Shift Detail</SheetTitle>
            <SheetDescription>{selected?.outletName} · {selected?.date}</SheetDescription>
          </SheetHeader>
          {selected && (
            <div className="mt-6 space-y-5">
              {/* Actions */}
              <div className="flex gap-2 flex-wrap">
                {selected.status === 'scheduled' && (
                  <>
                    <Button variant="outline" size="sm" className="gap-1.5" onClick={() => openEdit(selected)}><Edit2 className="h-3.5 w-3.5" /> Edit</Button>
                    <Button variant="outline" size="sm" className="gap-1.5 text-destructive border-destructive/30 hover:bg-destructive/5" onClick={() => setDeleteConfirm(selected.id)}>
                      <Trash2 className="h-3.5 w-3.5" /> Delete
                    </Button>
                  </>
                )}
                <Button variant="outline" size="sm" className="gap-1.5" onClick={() => { setSelectedEmployees([]); setAddEmployeeOpen(true); }}>
                  <UserPlus className="h-3.5 w-3.5" /> Add Staff
                </Button>
              </div>

              {/* Shift info */}
              <div className="grid grid-cols-2 gap-3">
                {[
                  ['Shift Type', shiftTypeLabel[selected.shiftType]],
                  ['Hours', `${selected.startTime} – ${selected.endTime}`],
                  ['Break', `${selected.breakMinutes} min`],
                  ['Status', shiftStatusConfig[selected.status]?.label ?? selected.status],
                ].map(([label, value]) => (
                  <div key={label} className="p-3 rounded-lg bg-muted/20 border">
                    <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">{label}</p>
                    <p className="text-sm font-medium mt-1">{value}</p>
                  </div>
                ))}
              </div>
              {selected.notes && (
                <div className="p-3 rounded-lg bg-muted/20 border">
                  <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide">Notes</p>
                  <p className="text-sm mt-1">{selected.notes}</p>
                </div>
              )}

              {/* Assigned Employees */}
              <div>
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-3">
                  Assigned Staff ({selected.assignments.length})
                </p>
                <div className="space-y-2">
                  {selected.assignments.length === 0 ? (
                    <p className="text-sm text-muted-foreground py-4 text-center">No employees assigned yet</p>
                  ) : selected.assignments.map(a => (
                    <div key={a.id} className="flex items-center justify-between p-3 rounded-lg border">
                      <div className="flex items-center gap-3">
                        <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center text-xs font-medium text-primary">
                          {a.employeeName.split(' ').map(n => n[0]).join('')}
                        </div>
                        <div>
                          <p className="text-sm font-medium">{a.employeeName}</p>
                          <p className="text-xs text-muted-foreground">{a.employeeRole}</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant={assignmentStatusConfig[a.status]?.variant ?? 'secondary'} className="text-[10px]">
                          {assignmentStatusConfig[a.status]?.label ?? a.status}
                        </Badge>
                        {a.overtimeMinutes > 0 && (
                          <Badge variant="destructive" className="text-[10px]">{a.overtimeMinutes}m OT</Badge>
                        )}
                        {selected.status === 'scheduled' && (
                          <Button size="icon" variant="ghost" className="h-7 w-7 text-destructive hover:bg-destructive/5"
                            onClick={(e) => { e.stopPropagation(); handleRemoveAssignment(selected.id, a.id); }}>
                            <UserMinus className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </SheetContent>
      </Sheet>

      {/* Create/Edit Shift Dialog */}
      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader><DialogTitle>{editingId ? 'Edit Shift' : 'New Shift'}</DialogTitle></DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Outlet</Label>
                <Input value={formData.outletName} onChange={e => setFormData(p => ({ ...p, outletName: e.target.value }))} placeholder="e.g. Downtown Flagship" />
              </div>
              <div className="space-y-2">
                <Label>Date</Label>
                <Input type="date" value={formData.date} onChange={e => setFormData(p => ({ ...p, date: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label>Shift Type</Label>
                <Select value={formData.shiftType} onValueChange={v => setFormData(p => ({ ...p, shiftType: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(shiftTypeLabel).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Start Time</Label>
                <Input type="time" value={formData.startTime} onChange={e => setFormData(p => ({ ...p, startTime: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>End Time</Label>
                <Input type="time" value={formData.endTime} onChange={e => setFormData(p => ({ ...p, endTime: e.target.value }))} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Break (minutes)</Label>
              <Input type="number" value={formData.breakMinutes} onChange={e => setFormData(p => ({ ...p, breakMinutes: Number(e.target.value) }))} className="w-32" />
            </div>
            <div className="space-y-2">
              <Label>Notes</Label>
              <Textarea value={formData.notes} onChange={e => setFormData(p => ({ ...p, notes: e.target.value }))} rows={2} placeholder="Optional notes…" />
            </div>

            {/* Employee Assignment */}
            <div className="space-y-3">
              <Label className="flex items-center gap-2"><Users className="h-4 w-4" /> Assign Employees</Label>
              <div className="border rounded-lg divide-y max-h-48 overflow-y-auto">
                {mockEmployeePool.map(emp => {
                  const isSelected = formData.assignedEmployees.includes(emp.id);
                  return (
                    <label key={emp.id} className="flex items-center gap-3 p-3 hover:bg-muted/50 cursor-pointer">
                      <Checkbox checked={isSelected} onCheckedChange={() => toggleEmployee(emp.id)} />
                      <div className="flex-1">
                        <p className="text-sm font-medium">{emp.name}</p>
                        <p className="text-xs text-muted-foreground">{emp.role}</p>
                      </div>
                      {isSelected && <CheckCircle className="h-4 w-4 text-primary" />}
                    </label>
                  );
                })}
              </div>
              <p className="text-xs text-muted-foreground">{formData.assignedEmployees.length} employee(s) selected</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFormOpen(false)}>Cancel</Button>
            <Button onClick={handleSave}>{editingId ? 'Update Shift' : 'Create Shift'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Add Employee to existing shift */}
      <Dialog open={addEmployeeOpen} onOpenChange={setAddEmployeeOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Add Staff to Shift</DialogTitle></DialogHeader>
          <div className="border rounded-lg divide-y max-h-64 overflow-y-auto">
            {selected && mockEmployeePool
              .filter(emp => !selected.assignments.some(a => a.employeeId === emp.id))
              .map(emp => {
                const isChecked = selectedEmployees.includes(emp.id);
                return (
                  <label key={emp.id} className="flex items-center gap-3 p-3 hover:bg-muted/50 cursor-pointer">
                    <Checkbox checked={isChecked} onCheckedChange={() => {
                      setSelectedEmployees(prev => isChecked ? prev.filter(id => id !== emp.id) : [...prev, emp.id]);
                    }} />
                    <div className="flex-1">
                      <p className="text-sm font-medium">{emp.name}</p>
                      <p className="text-xs text-muted-foreground">{emp.role}</p>
                    </div>
                  </label>
                );
              })}
            {selected && mockEmployeePool.filter(emp => !selected.assignments.some(a => a.employeeId === emp.id)).length === 0 && (
              <p className="text-sm text-muted-foreground text-center py-6">All employees already assigned</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddEmployeeOpen(false)}>Cancel</Button>
            <Button onClick={handleAddEmployeeToShift} disabled={selectedEmployees.length === 0}>
              Add {selectedEmployees.length > 0 ? `(${selectedEmployees.length})` : ''}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirm */}
      <Dialog open={!!deleteConfirm} onOpenChange={() => setDeleteConfirm(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>Delete Shift</DialogTitle></DialogHeader>
          <p className="text-sm text-muted-foreground">Are you sure? This will remove the shift and all employee assignments.</p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteConfirm(null)}>Cancel</Button>
            <Button variant="destructive" onClick={() => deleteConfirm && handleDelete(deleteConfirm)}>Delete</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/* ─── Swap Requests Tab ─── */
function SwapRequestsTab() {
  const [requests, setRequests] = useState(mockSwapRequests);

  const handleAction = (id: string, action: 'approved' | 'rejected') => {
    setRequests(prev => prev.map(r => r.id === id ? { ...r, status: action, reviewedBy: 'Current User', reviewedAt: new Date().toISOString() } : r));
    toast.success(action === 'approved' ? 'Swap approved' : 'Swap rejected');
  };

  const pending = requests.filter(r => r.status === 'pending').length;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-accent"><ArrowLeftRight className="h-5 w-5 text-accent-foreground" /></div>
          <div><p className="text-2xl font-bold">{pending}</p><p className="text-xs text-muted-foreground">Pending Swaps</p></div>
        </CardContent></Card>
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-primary/10"><CheckCircle className="h-5 w-5 text-primary" /></div>
          <div><p className="text-2xl font-bold">{requests.filter(r => r.status === 'approved').length}</p><p className="text-xs text-muted-foreground">Approved</p></div>
        </CardContent></Card>
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-destructive/10"><XCircle className="h-5 w-5 text-destructive" /></div>
          <div><p className="text-2xl font-bold">{requests.filter(r => r.status === 'rejected').length}</p><p className="text-xs text-muted-foreground">Rejected</p></div>
        </CardContent></Card>
      </div>

      <Card>
        <CardHeader><CardTitle className="text-lg">Swap Requests</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          {requests.map(sr => (
            <div key={sr.id} className="border rounded-lg p-4 space-y-3">
              <div className="flex items-center justify-between">
                <Badge variant={statusBadge(sr.status)}>{sr.status}</Badge>
                <span className="text-xs text-muted-foreground">{new Date(sr.createdAt).toLocaleDateString()}</span>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="p-3 rounded-lg bg-muted/20 border">
                  <p className="text-[10px] font-medium text-muted-foreground uppercase">Requester</p>
                  <p className="font-medium mt-1">{sr.requesterName}</p>
                  <p className="text-xs text-muted-foreground">{sr.originalDate} · {sr.originalShift}</p>
                </div>
                <div className="p-3 rounded-lg bg-muted/20 border">
                  <p className="text-[10px] font-medium text-muted-foreground uppercase">Swap With</p>
                  <p className="font-medium mt-1">{sr.targetName}</p>
                  <p className="text-xs text-muted-foreground">{sr.targetDate} · {sr.targetShift}</p>
                </div>
              </div>
              <p className="text-sm"><span className="text-muted-foreground">Reason:</span> {sr.reason}</p>
              {sr.status === 'pending' && (
                <div className="flex gap-2">
                  <Button size="sm" className="flex-1 gap-1.5" onClick={() => handleAction(sr.id, 'approved')}><CheckCircle className="h-4 w-4" /> Approve</Button>
                  <Button size="sm" variant="destructive" className="flex-1 gap-1.5" onClick={() => handleAction(sr.id, 'rejected')}><XCircle className="h-4 w-4" /> Reject</Button>
                </div>
              )}
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

/* ─── Time Off Tab ─── */
function TimeOffTab() {
  const [requests, setRequests] = useState(mockTimeOffRequests);
  const [filter, setFilter] = useState('all');
  const pending = requests.filter(r => r.status === 'pending').length;
  const filtered = requests.filter(r => filter === 'all' || r.status === filter);

  const handleAction = (id: string, action: 'approved' | 'rejected') => {
    setRequests(prev => prev.map(r => r.id === id ? { ...r, status: action as any, reviewedBy: 'Current User', reviewedAt: new Date().toISOString() } : r));
    toast.success(action === 'approved' ? 'Leave approved' : 'Leave rejected');
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-accent"><Clock className="h-5 w-5 text-accent-foreground" /></div>
          <div><p className="text-2xl font-bold">{pending}</p><p className="text-xs text-muted-foreground">Pending Requests</p></div>
        </CardContent></Card>
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-primary/10"><CheckCircle className="h-5 w-5 text-primary" /></div>
          <div><p className="text-2xl font-bold">{requests.filter(r => r.status === 'approved').length}</p><p className="text-xs text-muted-foreground">Approved</p></div>
        </CardContent></Card>
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-destructive/10"><XCircle className="h-5 w-5 text-destructive" /></div>
          <div><p className="text-2xl font-bold">{requests.filter(r => r.status === 'rejected').length}</p><p className="text-xs text-muted-foreground">Rejected</p></div>
        </CardContent></Card>
      </div>

      <Select value={filter} onValueChange={setFilter}>
        <SelectTrigger className="w-[180px]"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All Status</SelectItem>
          <SelectItem value="pending">Pending</SelectItem>
          <SelectItem value="approved">Approved</SelectItem>
          <SelectItem value="rejected">Rejected</SelectItem>
        </SelectContent>
      </Select>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead>Outlet</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>From</TableHead>
                <TableHead>To</TableHead>
                <TableHead className="text-center">Days</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Status</TableHead>
                <TableHead></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map(r => (
                <TableRow key={r.id}>
                  <TableCell><div><p className="font-medium">{r.employeeName}</p><p className="text-xs text-muted-foreground">{r.employeeRole}</p></div></TableCell>
                  <TableCell className="text-muted-foreground">{r.outletName}</TableCell>
                  <TableCell><Badge variant="outline">{timeOffTypeLabel[r.type]}</Badge></TableCell>
                  <TableCell>{r.startDate}</TableCell>
                  <TableCell>{r.endDate}</TableCell>
                  <TableCell className="text-center">{r.days}</TableCell>
                  <TableCell className="max-w-[150px] truncate text-muted-foreground">{r.reason}</TableCell>
                  <TableCell><Badge variant={statusBadge(r.status)}>{r.status}</Badge></TableCell>
                  <TableCell>
                    {r.status === 'pending' && (
                      <div className="flex gap-1">
                        <Button size="icon" variant="ghost" className="h-7 w-7 text-primary" onClick={() => handleAction(r.id, 'approved')}><CheckCircle className="h-4 w-4" /></Button>
                        <Button size="icon" variant="ghost" className="h-7 w-7 text-destructive" onClick={() => handleAction(r.id, 'rejected')}><XCircle className="h-4 w-4" /></Button>
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

/* ─── Overtime Tab ─── */
function OvertimeTab() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-accent"><Timer className="h-5 w-5 text-accent-foreground" /></div>
          <div><p className="text-2xl font-bold">{mockOvertimeSummary.length}</p><p className="text-xs text-muted-foreground">Employees with OT</p></div>
        </CardContent></Card>
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-primary/10"><Clock className="h-5 w-5 text-primary" /></div>
          <div><p className="text-2xl font-bold">{mockOvertimeSummary.reduce((s, o) => s + o.overtimeHours, 0)}h</p><p className="text-xs text-muted-foreground">Total OT Hours</p></div>
        </CardContent></Card>
        <Card><CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
          <div className="p-2 rounded-lg bg-primary/10"><AlertTriangle className="h-5 w-5 text-primary" /></div>
          <div><p className="text-2xl font-bold">₫{(mockOvertimeSummary.reduce((s, o) => s + o.overtimePay, 0) / 1000).toLocaleString()}K</p><p className="text-xs text-muted-foreground">Total OT Cost</p></div>
        </CardContent></Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Overtime Summary</CardTitle>
          <CardDescription>01/04/2026 – 05/04/2026</CardDescription>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead>Outlet</TableHead>
                <TableHead className="text-right">Scheduled</TableHead>
                <TableHead className="text-right">Actual</TableHead>
                <TableHead className="text-right">OT Hours</TableHead>
                <TableHead className="text-right">Rate</TableHead>
                <TableHead className="text-right">OT Pay</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {mockOvertimeSummary.map(o => (
                <TableRow key={o.employeeId}>
                  <TableCell className="font-medium">{o.employeeName}</TableCell>
                  <TableCell className="text-muted-foreground">{o.outletName}</TableCell>
                  <TableCell className="text-right">{o.scheduledHours}h</TableCell>
                  <TableCell className="text-right">{o.actualHours}h</TableCell>
                  <TableCell className="text-right font-medium">
                    {o.overtimeHours > 0 && <AlertTriangle className="h-3 w-3 inline mr-1 text-destructive" />}
                    {o.overtimeHours}h
                  </TableCell>
                  <TableCell className="text-right">x{o.overtimeRate}</TableCell>
                  <TableCell className="text-right font-medium">₫{(o.overtimePay / 1000).toLocaleString()}K</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

/* ─── Main Module ─── */
export function SchedulingModule() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Scheduling</h1>
        <p className="text-muted-foreground">Manage shifts, assign staff, and handle swap/time-off requests</p>
      </div>

      <Tabs defaultValue="shifts" className="space-y-4">
        <TabsList>
          <TabsTrigger value="shifts"><CalendarClock className="h-4 w-4 mr-1" /> Shifts</TabsTrigger>
          <TabsTrigger value="swaps"><ArrowLeftRight className="h-4 w-4 mr-1" /> Swap Requests</TabsTrigger>
          <TabsTrigger value="timeoff"><Clock className="h-4 w-4 mr-1" /> Time Off</TabsTrigger>
          <TabsTrigger value="overtime"><Timer className="h-4 w-4 mr-1" /> Overtime</TabsTrigger>
        </TabsList>

        <TabsContent value="shifts"><ShiftsTab /></TabsContent>
        <TabsContent value="swaps"><SwapRequestsTab /></TabsContent>
        <TabsContent value="timeoff"><TimeOffTab /></TabsContent>
        <TabsContent value="overtime"><OvertimeTab /></TabsContent>
      </Tabs>
    </div>
  );
}
