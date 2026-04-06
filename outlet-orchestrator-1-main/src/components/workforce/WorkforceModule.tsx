import { useState } from 'react';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Progress } from '@/components/ui/progress';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import {
  Clock, UserCheck, UserX, AlertTriangle, Timer,
  CheckCircle, XCircle, CalendarDays, Search, TrendingUp,
  LogIn, LogOut,
} from 'lucide-react';
import { mockAttendance, mockOvertime, mockLeaveRequests, mockLeaveQuotas } from '@/data/mock-workforce';
import type { AttendanceRecord, OvertimeRecord, LeaveRequest, LeaveQuota } from '@/types/workforce';
import { toast } from 'sonner';

/* ─── helpers ─── */
const attendanceStatusConfig: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  checked_in: { label: 'Checked In', variant: 'default' },
  checked_out: { label: 'Checked Out', variant: 'secondary' },
  late: { label: 'Late', variant: 'destructive' },
  absent: { label: 'Absent', variant: 'destructive' },
  on_leave: { label: 'On Leave', variant: 'outline' },
};

const leaveTypeLabel: Record<string, string> = {
  annual: 'Annual', sick: 'Sick', personal: 'Personal', maternity: 'Maternity', unpaid: 'Unpaid',
};

const statusBadge = (s: string) => {
  const map: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    pending: 'outline', approved: 'default', rejected: 'destructive', cancelled: 'secondary',
  };
  return map[s] ?? 'secondary';
};

/* ─── Attendance Tab ─── */
function AttendanceTab() {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const [records, setRecords] = useState(mockAttendance);
  const [selectedRecord, setSelectedRecord] = useState<AttendanceRecord | null>(null);

  const filtered = records.filter(r => {
    const matchSearch = r.employeeName.toLowerCase().includes(search.toLowerCase()) ||
      r.outletName.toLowerCase().includes(search.toLowerCase());
    const matchFilter = filter === 'all' || r.status === filter;
    return matchSearch && matchFilter;
  });

  const todayRecords = records.filter(r => r.date === '2026-04-05');
  const checkedIn = todayRecords.filter(r => r.status === 'checked_in').length;
  const late = todayRecords.filter(r => r.status === 'late').length;
  const absent = todayRecords.filter(r => r.status === 'absent').length;
  const onLeave = todayRecords.filter(r => r.status === 'on_leave').length;

  const handleCheckOut = (id: string) => {
    setRecords(prev => prev.map(r =>
      r.id === id ? { ...r, status: 'checked_out' as const, checkOut: new Date().toTimeString().slice(0, 5) } : r
    ));
    setSelectedRecord(null);
    toast.success('Check-out recorded');
  };

  return (
    <div className="space-y-6">
      {/* KPI cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10"><UserCheck className="h-5 w-5 text-primary" /></div>
            <div><p className="text-2xl font-bold">{checkedIn}</p><p className="text-xs text-muted-foreground">Checked In</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-destructive/10"><AlertTriangle className="h-5 w-5 text-destructive" /></div>
            <div><p className="text-2xl font-bold">{late}</p><p className="text-xs text-muted-foreground">Late</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-destructive/10"><UserX className="h-5 w-5 text-destructive" /></div>
            <div><p className="text-2xl font-bold">{absent}</p><p className="text-xs text-muted-foreground">Absent</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-muted"><CalendarDays className="h-5 w-5 text-muted-foreground" /></div>
            <div><p className="text-2xl font-bold">{onLeave}</p><p className="text-xs text-muted-foreground">On Leave</p></div>
          </CardContent>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search employee or outlet…" className="pl-9" value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <Select value={filter} onValueChange={setFilter}>
          <SelectTrigger className="w-[180px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            <SelectItem value="checked_in">Checked In</SelectItem>
            <SelectItem value="checked_out">Checked Out</SelectItem>
            <SelectItem value="late">Late</SelectItem>
            <SelectItem value="absent">Absent</SelectItem>
            <SelectItem value="on_leave">On Leave</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead>Outlet</TableHead>
                <TableHead>Scheduled</TableHead>
                <TableHead>Check In</TableHead>
                <TableHead>Check Out</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Late (min)</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map(r => (
                <TableRow key={r.id} className="cursor-pointer hover:bg-muted/50" onClick={() => setSelectedRecord(r)}>
                  <TableCell className="font-medium">{r.employeeName}</TableCell>
                  <TableCell className="text-muted-foreground">{r.outletName}</TableCell>
                  <TableCell>{r.scheduledIn} – {r.scheduledOut}</TableCell>
                  <TableCell>{r.checkIn ?? '—'}</TableCell>
                  <TableCell>{r.checkOut ?? '—'}</TableCell>
                  <TableCell>
                    <Badge variant={attendanceStatusConfig[r.status]?.variant ?? 'secondary'}>
                      {attendanceStatusConfig[r.status]?.label ?? r.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">{r.lateMinutes > 0 ? r.lateMinutes : '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Detail sheet */}
      <Sheet open={!!selectedRecord} onOpenChange={() => setSelectedRecord(null)}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Attendance Detail</SheetTitle>
          </SheetHeader>
          {selectedRecord && (
            <div className="mt-6 space-y-4">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">Employee</p>
                <p className="font-medium">{selectedRecord.employeeName}</p>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-sm text-muted-foreground">Outlet</p>
                  <p>{selectedRecord.outletName}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Date</p>
                  <p>{selectedRecord.date}</p>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-sm text-muted-foreground">Scheduled</p>
                  <p>{selectedRecord.scheduledIn} – {selectedRecord.scheduledOut}</p>
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Status</p>
                  <Badge variant={attendanceStatusConfig[selectedRecord.status]?.variant}>
                    {attendanceStatusConfig[selectedRecord.status]?.label}
                  </Badge>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="flex items-center gap-2">
                  <LogIn className="h-4 w-4 text-muted-foreground" />
                  <div>
                    <p className="text-sm text-muted-foreground">Check In</p>
                    <p className="font-medium">{selectedRecord.checkIn ?? 'Not yet'}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <LogOut className="h-4 w-4 text-muted-foreground" />
                  <div>
                    <p className="text-sm text-muted-foreground">Check Out</p>
                    <p className="font-medium">{selectedRecord.checkOut ?? 'Not yet'}</p>
                  </div>
                </div>
              </div>
              {selectedRecord.lateMinutes > 0 && (
                <div className="p-3 rounded-lg bg-destructive/10 border border-destructive/20">
                  <p className="text-sm font-medium text-destructive">Late by {selectedRecord.lateMinutes} minutes</p>
                </div>
              )}
              {selectedRecord.overtimeMinutes > 0 && (
                <div className="p-3 rounded-lg bg-primary/10 border border-primary/20">
                  <p className="text-sm font-medium text-primary">Overtime: {selectedRecord.overtimeMinutes} minutes</p>
                </div>
              )}
              {selectedRecord.notes && (
                <div>
                  <p className="text-sm text-muted-foreground">Notes</p>
                  <p>{selectedRecord.notes}</p>
                </div>
              )}
              {(selectedRecord.status === 'checked_in' || selectedRecord.status === 'late') && (
                <Button className="w-full" onClick={() => handleCheckOut(selectedRecord.id)}>
                  <LogOut className="h-4 w-4 mr-2" /> Record Check-Out
                </Button>
              )}
            </div>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}

/* ─── Overtime Tab ─── */
function OvertimeTab() {
  const [records, setRecords] = useState(mockOvertime);
  const [filter, setFilter] = useState('all');

  const filtered = records.filter(r => filter === 'all' || r.status === filter);
  const pending = records.filter(r => r.status === 'pending');
  const totalOTHours = records.filter(r => r.status === 'approved').reduce((s, r) => s + r.overtimeHours, 0);
  const totalOTCost = records.filter(r => r.status === 'approved').reduce((s, r) => s + r.estimatedPay, 0);

  const handleAction = (id: string, action: 'approved' | 'rejected') => {
    setRecords(prev => prev.map(r =>
      r.id === id ? { ...r, status: action, approvedBy: action === 'approved' ? 'Current User' : undefined } : r
    ));
    toast.success(action === 'approved' ? 'Overtime approved' : 'Overtime rejected');
  };

  return (
    <div className="space-y-6">
      {/* Summary */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-accent"><Timer className="h-5 w-5 text-accent-foreground" /></div>
            <div><p className="text-2xl font-bold">{pending.length}</p><p className="text-xs text-muted-foreground">Pending Approval</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10"><Clock className="h-5 w-5 text-primary" /></div>
            <div><p className="text-2xl font-bold">{totalOTHours}h</p><p className="text-xs text-muted-foreground">Approved OT (This Month)</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10"><TrendingUp className="h-5 w-5 text-primary" /></div>
            <div><p className="text-2xl font-bold">₫{totalOTCost.toLocaleString()}</p><p className="text-xs text-muted-foreground">Approved OT Cost</p></div>
          </CardContent>
        </Card>
      </div>

      {/* Filter */}
      <div className="flex gap-3">
        <Select value={filter} onValueChange={setFilter}>
          <SelectTrigger className="w-[180px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All</SelectItem>
            <SelectItem value="pending">Pending</SelectItem>
            <SelectItem value="approved">Approved</SelectItem>
            <SelectItem value="rejected">Rejected</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead>Outlet</TableHead>
                <TableHead>Date</TableHead>
                <TableHead className="text-right">Scheduled</TableHead>
                <TableHead className="text-right">Actual</TableHead>
                <TableHead className="text-right">OT Hours</TableHead>
                <TableHead className="text-right">Rate</TableHead>
                <TableHead className="text-right">Est. Pay</TableHead>
                <TableHead>Status</TableHead>
                <TableHead></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map(r => (
                <TableRow key={r.id}>
                  <TableCell className="font-medium">{r.employeeName}</TableCell>
                  <TableCell className="text-muted-foreground">{r.outletName}</TableCell>
                  <TableCell>{r.date}</TableCell>
                  <TableCell className="text-right">{r.scheduledHours}h</TableCell>
                  <TableCell className="text-right">{r.actualHours}h</TableCell>
                  <TableCell className="text-right font-medium">{r.overtimeHours}h</TableCell>
                  <TableCell className="text-right">{r.rate}x</TableCell>
                  <TableCell className="text-right">₫{r.estimatedPay.toLocaleString()}</TableCell>
                  <TableCell>
                    <Badge variant={statusBadge(r.status)}>{r.status}</Badge>
                  </TableCell>
                  <TableCell>
                    {r.status === 'pending' && (
                      <div className="flex gap-1">
                        <Button size="icon" variant="ghost" className="h-7 w-7 text-primary" onClick={() => handleAction(r.id, 'approved')}>
                          <CheckCircle className="h-4 w-4" />
                        </Button>
                        <Button size="icon" variant="ghost" className="h-7 w-7 text-destructive" onClick={() => handleAction(r.id, 'rejected')}>
                          <XCircle className="h-4 w-4" />
                        </Button>
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

/* ─── Leave Tab ─── */
function LeaveTab() {
  const [requests, setRequests] = useState(mockLeaveRequests);
  const [tab, setTab] = useState<'requests' | 'quotas'>('requests');
  const [filter, setFilter] = useState('all');

  const pending = requests.filter(r => r.status === 'pending');
  const filteredRequests = requests.filter(r => filter === 'all' || r.status === filter);

  const handleAction = (id: string, action: 'approved' | 'rejected') => {
    setRequests(prev => prev.map(r =>
      r.id === id ? { ...r, status: action, approvedBy: action === 'approved' ? 'Current User' : undefined } : r
    ));
    toast.success(action === 'approved' ? 'Leave approved' : 'Leave rejected');
  };

  return (
    <div className="space-y-6">
      {/* Summary */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-accent"><CalendarDays className="h-5 w-5 text-accent-foreground" /></div>
            <div><p className="text-2xl font-bold">{pending.length}</p><p className="text-xs text-muted-foreground">Pending Requests</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10"><CheckCircle className="h-5 w-5 text-primary" /></div>
            <div><p className="text-2xl font-bold">{requests.filter(r => r.status === 'approved').length}</p><p className="text-xs text-muted-foreground">Approved This Month</p></div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 pb-3 px-4 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-muted"><UserCheck className="h-5 w-5 text-muted-foreground" /></div>
            <div><p className="text-2xl font-bold">{mockLeaveQuotas.length}</p><p className="text-xs text-muted-foreground">Staff Tracked</p></div>
          </CardContent>
        </Card>
      </div>

      {/* Sub-tabs */}
      <div className="flex gap-2">
        <Button variant={tab === 'requests' ? 'default' : 'outline'} size="sm" onClick={() => setTab('requests')}>
          Leave Requests
        </Button>
        <Button variant={tab === 'quotas' ? 'default' : 'outline'} size="sm" onClick={() => setTab('quotas')}>
          Quota Overview
        </Button>
      </div>

      {tab === 'requests' && (
        <>
          <div className="flex gap-3">
            <Select value={filter} onValueChange={setFilter}>
              <SelectTrigger className="w-[180px]"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All</SelectItem>
                <SelectItem value="pending">Pending</SelectItem>
                <SelectItem value="approved">Approved</SelectItem>
                <SelectItem value="rejected">Rejected</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Employee</TableHead>
                    <TableHead>Outlet</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Period</TableHead>
                    <TableHead className="text-right">Days</TableHead>
                    <TableHead>Reason</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredRequests.map(r => (
                    <TableRow key={r.id}>
                      <TableCell className="font-medium">{r.employeeName}</TableCell>
                      <TableCell className="text-muted-foreground">{r.outletName}</TableCell>
                      <TableCell><Badge variant="outline">{leaveTypeLabel[r.leaveType]}</Badge></TableCell>
                      <TableCell>{r.startDate === r.endDate ? r.startDate : `${r.startDate} → ${r.endDate}`}</TableCell>
                      <TableCell className="text-right">{r.totalDays}</TableCell>
                      <TableCell className="max-w-[200px] truncate">{r.reason}</TableCell>
                      <TableCell><Badge variant={statusBadge(r.status)}>{r.status}</Badge></TableCell>
                      <TableCell>
                        {r.status === 'pending' && (
                          <div className="flex gap-1">
                            <Button size="icon" variant="ghost" className="h-7 w-7 text-primary" onClick={() => handleAction(r.id, 'approved')}>
                              <CheckCircle className="h-4 w-4" />
                            </Button>
                            <Button size="icon" variant="ghost" className="h-7 w-7 text-destructive" onClick={() => handleAction(r.id, 'rejected')}>
                              <XCircle className="h-4 w-4" />
                            </Button>
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}

      {tab === 'quotas' && (
        <Card>
          <CardHeader>
            <CardTitle>Leave Quota Overview</CardTitle>
            <CardDescription>Annual / Sick / Personal leave tracking per employee</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-6">
              {mockLeaveQuotas.map(q => (
                <div key={q.employeeId} className="space-y-3 pb-4 border-b last:border-0">
                  <div className="flex justify-between items-center">
                    <div>
                      <p className="font-medium">{q.employeeName}</p>
                      <p className="text-xs text-muted-foreground">{q.outletName}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-3 gap-4">
                    {(['annual', 'sick', 'personal'] as const).map(type => {
                      const data = q[type];
                      const pct = (data.used / data.total) * 100;
                      return (
                        <div key={type} className="space-y-1.5">
                          <div className="flex justify-between text-xs">
                            <span className="capitalize text-muted-foreground">{type}</span>
                            <span className="font-medium">{data.remaining}/{data.total}</span>
                          </div>
                          <Progress value={pct} className="h-2" />
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

/* ─── Main Module ─── */
export function WorkforceModule() {
  return (
    <div className="p-6 space-y-6 max-w-7xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Workforce Management</h1>
        <p className="text-muted-foreground">Attendance, overtime, and leave management</p>
      </div>
      <Tabs defaultValue="attendance" className="space-y-4">
        <TabsList>
          <TabsTrigger value="attendance" className="gap-2"><Clock className="h-4 w-4" /> Chấm công</TabsTrigger>
          <TabsTrigger value="overtime" className="gap-2"><Timer className="h-4 w-4" /> Overtime</TabsTrigger>
          <TabsTrigger value="leave" className="gap-2"><CalendarDays className="h-4 w-4" /> Nghỉ phép</TabsTrigger>
        </TabsList>
        <TabsContent value="attendance"><AttendanceTab /></TabsContent>
        <TabsContent value="overtime"><OvertimeTab /></TabsContent>
        <TabsContent value="leave"><LeaveTab /></TabsContent>
      </Tabs>
    </div>
  );
}
