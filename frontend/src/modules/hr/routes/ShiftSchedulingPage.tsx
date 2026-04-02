import { useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import {
  Badge,
  Button,
  Card,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
} from '@design-system/index'
import { DataTable } from '@design-system/tables/DataTable'
import { useFieldErrors } from '@core/api/useFieldErrors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { permissionConstants } from '@core/permissions/permission.constants'
import { hasPermission } from '@core/permissions/permission.checker'
import { useShiftSchedules, useShiftAssignments, useCreateShiftSchedule, useCreateShiftAssignment } from '../hooks/useShiftSchedules'
import type { ShiftSchedule, ShiftAssignment } from '../model/hr.types'

function statusTone(status: string): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (status.toUpperCase()) {
    case 'SCHEDULED': return 'neutral'
    case 'ACTIVE': return 'success'
    case 'COMPLETED': return 'success'
    case 'CANCELLED': return 'danger'
    default: return 'neutral'
  }
}

export function ShiftSchedulingPage() {
  usePageTitle('Shift Scheduling')
  const principal = usePrincipal()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canRead = hasPermission(principal, permissionConstants.hr.shiftRead)
  const canWrite = hasPermission(principal, permissionConstants.hr.shiftWrite)

  const [dateRange, setDateRange] = useState({
    fromDate: new Date().toISOString().slice(0, 10),
    toDate: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10),
  })
  const [selectedSchedule, setSelectedSchedule] = useState<ShiftSchedule | null>(null)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [showAssignForm, setShowAssignForm] = useState(false)

  const schedulesQuery = useShiftSchedules(
    canRead && selectedOutletId
      ? { outletId: selectedOutletId, fromDate: dateRange.fromDate, toDate: dateRange.toDate }
      : undefined,
  )

  const assignmentsQuery = useShiftAssignments(selectedSchedule?.id ?? null)

  const [newSchedule, setNewSchedule] = useState({
    shiftName: '',
    shiftDate: new Date().toISOString().slice(0, 10),
    startTime: '08:00',
    endTime: '16:00',
  })
  const [scheduleFormError, setScheduleFormError] = useState<string | null>(null)
  const [newAssignment, setNewAssignment] = useState({
    employeeId: '',
    assignedRole: '',
    note: '',
  })
  const [assignFormError, setAssignFormError] = useState<string | null>(null)

  const createScheduleMutation = useCreateShiftSchedule()
  const createAssignmentMutation = useCreateShiftAssignment()
  const { getError: getScheduleFieldError } = useFieldErrors(createScheduleMutation.error)
  const { getError: getAssignFieldError } = useFieldErrors(createAssignmentMutation.error)

  if (!canRead) {
    return (
      <DashboardLayout description="Manage shift schedules for your outlet." title="Shift Scheduling">
        <PermissionDeniedInline message="You need hr.shift.read permission to view shift schedules." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      description="Manage shift schedules and assign employees to shifts."
      title="Shift Scheduling"
    >
      <div className="page-stack">
        {(!selectedOutletId || !selectedRegionId) && (
          <ReadonlyBanner message="Chọn region và outlet từ thanh điều hướng để quản lý shift schedules." />
        )}
        {/* Filters */}
        <Card title="Date Range Filter">
          <div className="field-grid">
            <Input
              label="From Date"
              onChange={(e) => setDateRange((prev) => ({ ...prev, fromDate: e.target.value }))}
              type="date"
              value={dateRange.fromDate}
            />
            <Input
              label="To Date"
              onChange={(e) => setDateRange((prev) => ({ ...prev, toDate: e.target.value }))}
              type="date"
              value={dateRange.toDate}
            />
          </div>
        </Card>

        {/* Create Shift Schedule Form */}
        {canWrite && !showCreateForm && (
          <Button id="btn-show-create-shift" onClick={() => setShowCreateForm(true)} variant="primary">
            + Create shift schedule
          </Button>
        )}

        {canWrite && showCreateForm && (
          <FormSection title="New Shift Schedule">
            <div className="field-grid">
              <Input
                error={getScheduleFieldError('shiftName')}
                label="Shift Name"
                onChange={(e) => setNewSchedule((prev) => ({ ...prev, shiftName: e.target.value }))}
                placeholder="e.g. Morning, Afternoon, Evening"
                value={newSchedule.shiftName}
              />
              <Input
                error={getScheduleFieldError('shiftDate')}
                label="Shift Date"
                onChange={(e) => setNewSchedule((prev) => ({ ...prev, shiftDate: e.target.value }))}
                type="date"
                value={newSchedule.shiftDate}
              />
              <Input
                error={getScheduleFieldError('startTime')}
                label="Start Time"
                onChange={(e) => setNewSchedule((prev) => ({ ...prev, startTime: e.target.value }))}
                type="time"
                value={newSchedule.startTime}
              />
              <Input
                error={getScheduleFieldError('endTime')}
                label="End Time"
                onChange={(e) => setNewSchedule((prev) => ({ ...prev, endTime: e.target.value }))}
                type="time"
                value={newSchedule.endTime}
              />
            </div>
            {scheduleFormError && (
              <p className="error-text" style={{ margin: '0 0 0.5rem' }}>{scheduleFormError}</p>
            )}
            <FormActions
              primaryAction={
                <Button
                  loading={createScheduleMutation.isPending}
                  onClick={async () => {
                    if (!newSchedule.shiftName.trim()) {
                      setScheduleFormError('Shift name is required.')
                      return
                    }
                    if (newSchedule.endTime <= newSchedule.startTime) {
                      setScheduleFormError('End time must be after start time.')
                      return
                    }
                    // Backend: regionId @NotNull, outletId @NotNull — must guard before using !
                    if (!selectedRegionId || !selectedOutletId) {
                      setScheduleFormError('Chọn region và outlet từ thanh điều hướng trước khi tạo shift.')
                      return
                    }
                    setScheduleFormError(null)
                    try {
                      await createScheduleMutation.mutateAsync({
                        regionId: selectedRegionId,
                        outletId: selectedOutletId,
                        shiftDate: newSchedule.shiftDate,
                        shiftName: newSchedule.shiftName.trim(),
                        startTime: newSchedule.startTime,
                        endTime: newSchedule.endTime,
                      })
                      setShowCreateForm(false)
                      setNewSchedule({ shiftName: '', shiftDate: new Date().toISOString().slice(0, 10), startTime: '08:00', endTime: '16:00' })
                    } catch (err) {
                      setScheduleFormError(err instanceof Error ? err.message : 'Failed to create shift schedule.')
                    }
                  }}
                >
                  Create
                </Button>
              }
              secondaryAction={
                <Button onClick={() => { setShowCreateForm(false); setScheduleFormError(null) }} variant="secondary">
                  Cancel
                </Button>
              }
            />
          </FormSection>
        )}

        {/* Shift Schedule Table */}
        <DataTable<ShiftSchedule>
          columns={[
            { key: 'id', header: 'ID', render: (row) => <>{row.id}</> },
            { key: 'shiftName', header: 'Shift Name', render: (row) => <>{row.shiftName}</> },
            { key: 'shiftDate', header: 'Date', render: (row) => <>{row.shiftDate}</> },
            { key: 'startTime', header: 'Start', render: (row) => <>{row.startTime}</> },
            { key: 'endTime', header: 'End', render: (row) => <>{row.endTime}</> },
            {
              key: 'status',
              header: 'Status',
              render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge>,
            },
            {
              key: 'actions',
              header: 'Actions',
              render: (row) => (
                <Button
                  onClick={() => {
                    setSelectedSchedule(row)
                    setShowAssignForm(false)
                  }}
                  size="sm"
                  variant="secondary"
                >
                  View Assignments
                </Button>
              ),
            },
          ]}
          error={schedulesQuery.error ? (schedulesQuery.error instanceof Error ? schedulesQuery.error.message : 'Failed to load shifts') : null}
          loading={schedulesQuery.isLoading}
          onRetry={() => void schedulesQuery.refetch()}
          rowKey={(row) => row.id}
          rows={schedulesQuery.data ?? []}
        />

        {/* Selected Shift Assignment Detail */}
        {selectedSchedule && (
          <Card title={`Assignments for "${selectedSchedule.shiftName}" — ${selectedSchedule.shiftDate}`}>
            <DataTable<ShiftAssignment>
              columns={[
                { key: 'id', header: 'Assignment ID', render: (row) => <>{row.id}</> },
                { key: 'employeeId', header: 'Employee ID', render: (row) => <>{row.employeeId}</> },
                { key: 'assignedRole', header: 'Role', render: (row) => <>{row.assignedRole ?? '—'}</> },
                {
                  key: 'attendanceStatus',
                  header: 'Attendance',
                  render: (row) => <Badge tone={row.attendanceStatus === 'PRESENT' ? 'success' : 'neutral'}>{row.attendanceStatus}</Badge>,
                },
                {
                  key: 'approvalStatus',
                  header: 'Approval',
                  render: (row) => (
                    <Badge tone={row.approvalStatus === 'APPROVED' ? 'success' : row.approvalStatus === 'REJECTED' ? 'danger' : 'neutral'}>
                      {row.approvalStatus}
                    </Badge>
                  ),
                },
                { key: 'note', header: 'Note', render: (row) => <>{row.note ?? '—'}</> },
              ]}
              emptyDescription="No employees assigned to this shift yet."
              emptyTitle="No assignments"
              error={assignmentsQuery.error ? 'Failed to load assignments' : null}
              loading={assignmentsQuery.isLoading}
              onRetry={() => void assignmentsQuery.refetch()}
              rowKey={(row) => row.id}
              rows={assignmentsQuery.data ?? []}
            />

            {/* Create Assignment */}
            {canWrite && !showAssignForm && (
              <div style={{ marginTop: '1rem' }}>
                <Button id="btn-show-assign-employee" onClick={() => setShowAssignForm(true)} size="sm" variant="primary">
                  + Assign employee
                </Button>
              </div>
            )}
            {canWrite && showAssignForm && (
              <FormSection title="Assign Employee to Shift">
                <div className="field-grid">
                  <Input
                    error={getAssignFieldError('employeeId')}
                    label="Employee ID"
                    onChange={(e) => setNewAssignment((prev) => ({ ...prev, employeeId: e.target.value }))}
                    type="number"
                    value={newAssignment.employeeId}
                  />
                  <Input
                    error={getAssignFieldError('assignedRole')}
                    label="Assigned Role"
                    onChange={(e) => setNewAssignment((prev) => ({ ...prev, assignedRole: e.target.value }))}
                    placeholder="e.g. Cashier, Chef, Server"
                    value={newAssignment.assignedRole}
                  />
                  <Input
                    error={getAssignFieldError('note')}
                    label="Note"
                    onChange={(e) => setNewAssignment((prev) => ({ ...prev, note: e.target.value }))}
                    value={newAssignment.note}
                  />
                </div>
                {assignFormError && (
                  <p className="error-text" style={{ margin: '0 0 0.5rem' }}>{assignFormError}</p>
                )}
                <FormActions
                  primaryAction={
                    <Button
                      loading={createAssignmentMutation.isPending}
                      onClick={async () => {
                        const parsedId = Number(newAssignment.employeeId)
                        if (!newAssignment.employeeId || !Number.isInteger(parsedId) || parsedId <= 0) {
                          setAssignFormError('Enter a valid Employee ID.')
                          return
                        }
                        setAssignFormError(null)
                        try {
                          await createAssignmentMutation.mutateAsync({
                            shiftScheduleId: selectedSchedule.id,
                            employeeId: parsedId,
                            assignedRole: newAssignment.assignedRole || undefined,
                            note: newAssignment.note || undefined,
                          })
                          setShowAssignForm(false)
                          setNewAssignment({ employeeId: '', assignedRole: '', note: '' })
                        } catch (err) {
                          setAssignFormError(err instanceof Error ? err.message : 'Failed to assign employee.')
                        }
                      }}
                    >
                      Assign
                    </Button>
                  }
                  secondaryAction={
                    <Button onClick={() => { setShowAssignForm(false); setAssignFormError(null) }} variant="secondary">
                      Cancel
                    </Button>
                  }
                />
              </FormSection>
            )}
          </Card>
        )}
      </div>
    </DashboardLayout>
  )
}
