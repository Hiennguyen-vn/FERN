import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  DataTable,
  ErrorState,
  FormSection,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrAttendanceApprovals, useHrAttendanceEvents } from '../hooks/useHr'
import type { HrAttendanceApproval, HrAttendanceEventListItem } from '../model/hr.types'
import { getHrErrorMessage } from '../services/hrError.service'
import {
  buildAttendanceSummary,
  formatDateLabel,
  formatDecimal,
  isAttendanceException,
} from '../services/hrReadModel.service'
import { canReadAttendanceSummary } from '../services/hrPermission.service'
import { toOptionalNumber } from '@shared/validators/parseInput'

const PAGE_SIZE = 50

export function AttendanceSummaryPage() {
  usePageTitle('HR Attendance Summary')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canReadSummary = canReadAttendanceSummary(principal)
  const [eventPage, setEventPage] = useState(0)
  const [filters, setFilters] = useState({
    employeeId: '',
    fromDate: '',
    outletId: selectedOutletId ? String(selectedOutletId) : '',
    regionId: selectedRegionId ? String(selectedRegionId) : '',
    toDate: '',
  })

  const attendanceFilters = useMemo(
    () => ({
      employeeId: toOptionalNumber(filters.employeeId),
      fromDate: filters.fromDate || undefined,
      outletId: toOptionalNumber(filters.outletId),
      page: eventPage,
      regionId: toOptionalNumber(filters.regionId),
      size: PAGE_SIZE,
      sort: 'desc',
      toDate: filters.toDate || undefined,
    }),
    [filters, eventPage],
  )

  const approvalFilters = useMemo(
    () => ({
      outletId: toOptionalNumber(filters.outletId),
      regionId: toOptionalNumber(filters.regionId),
    }),
    [filters.outletId, filters.regionId],
  )

  const eventsQuery = useHrAttendanceEvents(attendanceFilters, { enabled: canReadSummary })
  const approvalsQuery = useHrAttendanceApprovals(approvalFilters, { enabled: canReadSummary })

  const summary = useMemo(
    () => buildAttendanceSummary(eventsQuery.data?.items ?? [], approvalsQuery.data ?? []),
    [approvalsQuery.data, eventsQuery.data?.items],
  )
  const exceptionRows = useMemo(
    () => (approvalsQuery.data ?? []).filter((approval) => isAttendanceException(approval)),
    [approvalsQuery.data],
  )

  const exceptionColumns = useMemo<Array<DataTableColumn<HrAttendanceApproval>>>(
    () => [
      { key: 'shiftAssignment', header: 'Shift assignment', render: (approval) => `#${approval.shiftAssignmentId}` },
      {
        key: 'businessDate',
        header: 'Business date',
        render: (approval) => formatDateLabel(approval.businessDate),
      },
      {
        key: 'status',
        header: 'Approval status',
        render: (approval) => <StatusBadge status={approval.status} />,
      },
      {
        key: 'attendanceStatus',
        header: 'Attendance',
        render: (approval) => approval.attendanceStatus,
      },
      {
        key: 'overtime',
        header: 'Overtime',
        render: (approval) => formatDecimal(Number(approval.overtimeHours ?? 0), ' h'),
      },
    ],
    [],
  )

  const eventColumns = useMemo<Array<DataTableColumn<HrAttendanceEventListItem>>>(
    () => [
      { key: 'eventId', header: 'Event ID', render: (event) => `#${event.id}` },
      {
        key: 'employee',
        header: 'Employee',
        render: (event) => `#${event.employeeId}`,
      },
      {
        key: 'shiftDate',
        header: 'Shift date',
        render: (event) => formatDateLabel(event.shiftDate),
      },
      {
        key: 'eventType',
        header: 'Event',
        render: (event) => event.eventType,
      },
      {
        key: 'scope',
        header: 'Scope',
        render: (event) => `Region #${event.regionId} · Outlet #${event.outletId}`,
      },
    ],
    [],
  )

  if (!canReadSummary) {
    return (
      <DashboardLayout title="Attendance Summary" description="Summary và exception view cho attendance review">
        <PermissionDeniedInline message="Bạn cần quyền hr.attendance.review để xem attendance summary và exceptions." />
      </DashboardLayout>
    )
  }

  if (eventsQuery.error && approvalsQuery.error) {
    return (
      <DashboardLayout title="Attendance Summary" description="Summary và exception view cho attendance review">
        <ErrorState
          actionLabel="Tải lại"
          message={getHrErrorMessage(eventsQuery.error, 'Không thể tải attendance summary.')}
          onAction={() => {
            void eventsQuery.refetch()
            void approvalsQuery.refetch()
          }}
          title="Không thể tải attendance summary"
        />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Attendance Summary"
      description="Cross-check attendance events, approval queue và exceptions theo scope hiện tại."
    >
      {!filters.regionId && !filters.outletId ? (
        <ReadonlyBanner message="Bạn đang xem attendance summary trên toàn bộ accessible scope. Chọn region/outlet để review tập trung hơn." />
      ) : (
        <ReadonlyBanner message="Attendance summary là màn read-first để review workload và exceptions trước khi xử lý chi tiết." />
      )}

      <FormSection description="Filter attendance read model theo employee và phạm vi vận hành." title="Summary filters">
        <div className="field-grid">
          <Input
            label="Employee ID"
            onChange={(event) => { setFilters((current) => ({ ...current, employeeId: event.target.value })); setEventPage(0) }}
            placeholder="Optional"
            type="number"
            value={filters.employeeId}
          />
          <Input
            label="Region ID"
            onChange={(event) => { setFilters((current) => ({ ...current, regionId: event.target.value })); setEventPage(0) }}
            placeholder="Optional"
            type="number"
            value={filters.regionId}
          />
          <Input
            label="Outlet ID"
            onChange={(event) => { setFilters((current) => ({ ...current, outletId: event.target.value })); setEventPage(0) }}
            placeholder="Optional"
            type="number"
            value={filters.outletId}
          />
          <Input
            label="From date"
            onChange={(event) => { setFilters((current) => ({ ...current, fromDate: event.target.value })); setEventPage(0) }}
            type="date"
            value={filters.fromDate}
          />
          <Input
            label="To date"
            onChange={(event) => { setFilters((current) => ({ ...current, toDate: event.target.value })); setEventPage(0) }}
            type="date"
            value={filters.toDate}
          />
        </div>
        <div className="form-actions align-start">
          <Button onClick={() => {
            void eventsQuery.refetch()
            void approvalsQuery.refetch()
          }} size="sm" variant="secondary">
            Refresh summary
          </Button>
          <Button
            onClick={() => {
              setFilters({
                employeeId: '',
                fromDate: '',
                outletId: selectedOutletId ? String(selectedOutletId) : '',
                regionId: selectedRegionId ? String(selectedRegionId) : '',
                toDate: '',
              })
              setEventPage(0)
            }}
            size="sm"
            variant="ghost"
          >
            Reset filters
          </Button>
        </div>
      </FormSection>

      <div className="card-grid">
        <Card title="Total events">
          <strong>{summary.totalEvents}</strong>
        </Card>
        <Card title="Unique employees">
          <strong>{summary.uniqueEmployees}</strong>
        </Card>
        <Card title="Pending approvals">
          <strong>{summary.pendingApprovals}</strong>
        </Card>
        <Card title="Exceptions">
          <strong>{summary.exceptionCount}</strong>
        </Card>
      </div>

      <FormSection description="Queue các approval record cần chú ý hoặc follow-up." title="Attendance exceptions">
        <DataTable
          columns={exceptionColumns}
          emptyDescription="Không có exception nào cho bộ lọc hiện tại."
          emptyTitle="No attendance exceptions"
          error={approvalsQuery.error ? getHrErrorMessage(approvalsQuery.error, 'Không thể tải attendance approvals.') : null}
          errorTitle="Không thể tải attendance approvals"
          loading={approvalsQuery.isLoading}
          loadingDescription="Đang tải attendance approval queue..."
          loadingTitle="Loading approvals"
          onRetry={() => void approvalsQuery.refetch()}
          onRowClick={(approval) => navigate(`/workforce/attendance-approvals/${approval.shiftAssignmentId}`)}
          rowKey={(approval) => approval.id ?? approval.shiftAssignmentId}
          rows={exceptionRows}
        />
      </FormSection>

      <FormSection description="Recent attendance events để hỗ trợ payroll preparation và anomaly review." title="Recent attendance events">
        <DataTable
          canNext={eventsQuery.data?.hasMore ?? false}
          canPrevious={eventPage > 0}
          columns={eventColumns}
          currentPage={eventPage}
          emptyDescription="Không có attendance event nào khớp bộ lọc hiện tại."
          emptyTitle="No attendance events"
          error={eventsQuery.error ? getHrErrorMessage(eventsQuery.error, 'Không thể tải attendance events.') : null}
          errorTitle="Không thể tải attendance events"
          loading={eventsQuery.isLoading}
          loadingDescription="Đang tải attendance events..."
          loadingTitle="Loading attendance events"
          onNext={() => setEventPage((p) => p + 1)}
          onPrevious={() => setEventPage((p) => Math.max(0, p - 1))}
          onRetry={() => void eventsQuery.refetch()}
          rowKey={(event) => event.id}
          rows={eventsQuery.data?.items ?? []}
        />
      </FormSection>
    </DashboardLayout>
  )
}
