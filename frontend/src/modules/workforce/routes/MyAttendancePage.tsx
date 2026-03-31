import { useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  Pagination,
  ReadonlyBanner,
  Select,
  PermissionDeniedInline,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAuthStore } from '@core/auth/auth.store'
import { useAttendanceEvents } from '../hooks/useAttendanceEvents'
import { useRecordAttendanceEvent } from '../hooks/useRecordAttendanceEvent'
import type { AttendanceEventListItem } from '../model/workforce.types'
import { canRecordAttendance } from '../services/workforcePermission.service'

function toOptionalNumber(value: string): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

export function MyAttendancePage() {
  usePageTitle('My Attendance')

  const principal = useAuthStore((state) => state.principal)
  const { selectedOutletId, selectedRegionId } = useScopeContext()

  if (!canRecordAttendance(principal)) {
    return <PermissionDeniedInline message="You don't have permission to record attendance events" />
  }

  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState({
    employeeId: '',
    fromDate: '',
    toDate: '',
  })
  const [form, setForm] = useState({
    employeeId: '',
    shiftAssignmentId: '',
    eventType: 'CLOCK_IN',
    eventTime: new Date().toISOString().slice(0, 16),
    sourceSystem: 'FERN_FRONTEND',
  })
  const size = 20

  const eventsQuery = useAttendanceEvents(
    selectedOutletId && selectedRegionId
      ? {
          outletId: selectedOutletId,
          regionId: selectedRegionId,
          employeeId: toOptionalNumber(filters.employeeId),
          fromDate: filters.fromDate || undefined,
          toDate: filters.toDate || undefined,
          page,
          size,
          sort: 'desc',
        }
      : null,
  )
  const recordMutation = useRecordAttendanceEvent()

  const columns: Array<DataTableColumn<AttendanceEventListItem>> = [
    { key: 'id', header: 'Event ID', render: (row) => `#${row.id}` },
    { key: 'employeeId', header: 'Employee', render: (row) => `#${row.employeeId}` },
    { key: 'shiftAssignmentId', header: 'Shift Assignment', render: (row) => `#${row.shiftAssignmentId}` },
    { key: 'eventType', header: 'Event', render: (row) => row.eventType },
    { key: 'shiftDate', header: 'Shift Date', render: (row) => row.shiftDate },
    { key: 'eventTime', header: 'Event Time', render: (row) => new Date(row.eventTime).toLocaleString() },
  ]

  return (
    <DashboardLayout
      description="View recent attendance events and record a new event against the selected outlet."
      title="My Attendance"
    >
      {!selectedOutletId || !selectedRegionId ? (
        <>
          <ReadonlyBanner message="Select both a region and outlet in the shell before using attendance screens." />
          <EmptyState
            description="Attendance screens cần cả region và outlet để record event đúng phạm vi và chỉ hiển thị dữ liệu liên quan."
            title="Attendance context required"
          />
        </>
      ) : null}

      {selectedOutletId && selectedRegionId ? (
        <>
      <FormSection description="Record a new attendance event using the live HR service endpoint." title="Record attendance event">
        <div className="field-grid">
          <Input
            label="Employee ID"
            onChange={(event) => setForm((current) => ({ ...current, employeeId: event.target.value }))}
            value={form.employeeId}
          />
          <Input
            label="Shift Assignment ID"
            onChange={(event) => setForm((current) => ({ ...current, shiftAssignmentId: event.target.value }))}
            value={form.shiftAssignmentId}
          />
          <Select
            label="Event Type"
            onChange={(event) => setForm((current) => ({ ...current, eventType: event.target.value }))}
            options={[
              { label: 'Clock In', value: 'CLOCK_IN' },
              { label: 'Clock Out', value: 'CLOCK_OUT' },
              { label: 'Break Start', value: 'BREAK_START' },
              { label: 'Break End', value: 'BREAK_END' },
            ]}
            value={form.eventType}
          />
          <Input
            label="Event Time"
            onChange={(event) => setForm((current) => ({ ...current, eventTime: event.target.value }))}
            type="datetime-local"
            value={form.eventTime}
          />
        </div>
        <FormActions
          primaryAction={
            <Button
              disabled={!selectedOutletId || !selectedRegionId}
              loading={recordMutation.isPending}
              onClick={() => {
                if (!selectedOutletId || !selectedRegionId) {
                  return
                }

                void recordMutation.mutateAsync({
                  employeeId: Number(form.employeeId),
                  shiftAssignmentId: Number(form.shiftAssignmentId),
                  eventType: form.eventType as 'CLOCK_IN' | 'CLOCK_OUT' | 'BREAK_START' | 'BREAK_END',
                  eventTime: new Date(form.eventTime).toISOString(),
                  outletId: selectedOutletId,
                  regionId: selectedRegionId,
                  sourceSystem: form.sourceSystem,
                })
              }}
            >
              Record event
            </Button>
          }
        />
        {recordMutation.error ? (
          <ErrorState
            message={recordMutation.error instanceof Error ? recordMutation.error.message : 'Failed to record event'}
            title="Không thể ghi nhận attendance event"
          />
        ) : null}
      </FormSection>

      <Card title="Attendance event filters">
        <div className="field-grid">
          <Input
            label="Employee ID"
            onChange={(event) => {
              setFilters((current) => ({ ...current, employeeId: event.target.value }))
              setPage(0)
            }}
            value={filters.employeeId}
          />
          <Input
            label="From date"
            onChange={(event) => {
              setFilters((current) => ({ ...current, fromDate: event.target.value }))
              setPage(0)
            }}
            type="date"
            value={filters.fromDate}
          />
          <Input
            label="To date"
            onChange={(event) => {
              setFilters((current) => ({ ...current, toDate: event.target.value }))
              setPage(0)
            }}
            type="date"
            value={filters.toDate}
          />
        </div>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription={
          filters.employeeId || filters.fromDate || filters.toDate
            ? 'Không có attendance event nào khớp bộ lọc hiện tại.'
            : 'Chưa có attendance event nào trong phạm vi đang xem.'
        }
        emptyTitle="No attendance events"
        error={eventsQuery.error instanceof Error ? eventsQuery.error.message : null}
        errorTitle="Không thể tải attendance events"
        loading={eventsQuery.isLoading}
        loadingDescription="Loading attendance events for the selected scope..."
        loadingTitle="Loading attendance events"
        onRetry={() => void eventsQuery.refetch()}
        rows={eventsQuery.data?.items ?? []}
      />
      <Pagination
        canNext={Boolean(eventsQuery.data?.hasMore)}
        canPrevious={page > 0}
        currentPage={page}
        onNext={() => setPage((value) => value + 1)}
        onPrevious={() => setPage((value) => Math.max(0, value - 1))}
      />
        </>
      ) : null}
    </DashboardLayout>
  )
}
