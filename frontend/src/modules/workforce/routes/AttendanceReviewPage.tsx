import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Button, Card, EmptyState, ErrorState, ReadonlyBanner, StatusBadge, PermissionDeniedInline } from '@design-system/index'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAuthStore } from '@core/auth/auth.store'
import { useAttendanceApprovals } from '../hooks/useAttendanceApprovals'
import { canApproveAttendance } from '../services/workforcePermission.service'

export function AttendanceReviewPage() {
  usePageTitle('Attendance Review')

  const principal = useAuthStore((state) => state.principal)
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canApprove = canApproveAttendance(principal)

  const approvalsQuery = useAttendanceApprovals(
    canApprove && (selectedOutletId || selectedRegionId)
      ? {
          outletId: selectedOutletId ?? undefined,
          regionId: selectedRegionId ?? undefined,
        }
      : null,
  )

  if (!canApprove) {
    return (
      <DashboardLayout description="Review attendance approvals for the selected region or outlet." title="Attendance Review">
        <PermissionDeniedInline message="Bạn cần quyền hr.attendance.review để phê duyệt chấm công." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      description="Review attendance approvals for the selected region or outlet."
      title="Attendance Review"
    >
      {!selectedOutletId && !selectedRegionId ? (
        <>
          <ReadonlyBanner message="Select at least a region or outlet in the shell before opening attendance review." />
          <EmptyState
            description="Attendance Review chỉ hiển thị queue khi scope vận hành đã được chọn rõ ràng."
            title="Approval scope required"
          />
        </>
      ) : null}
      {selectedOutletId || selectedRegionId ? (
        <>
          {approvalsQuery.isLoading ? (
            <Card title="Loading approvals">
              <p className="muted-text">Loading attendance approval queue...</p>
            </Card>
          ) : null}
          {approvalsQuery.error ? (
            <ErrorState
              actionLabel="Retry"
              message={approvalsQuery.error instanceof Error ? approvalsQuery.error.message : 'Failed to load approvals'}
              onAction={() => void approvalsQuery.refetch()}
              title="Không thể tải approval queue"
            />
          ) : null}
          {!approvalsQuery.isLoading && !approvalsQuery.error && (approvalsQuery.data ?? []).length === 0 ? (
            <EmptyState
              description="Không có approval nào chờ xử lý trong scope hiện tại."
              title="No attendance approvals"
            />
          ) : null}
          <div className="card-grid">
            {(approvalsQuery.data ?? []).map((approval) => (
              <Card key={approval.id} title={`Approval #${approval.id}`}>
                <div className="page-stack">
                  <div className="page-header">
                    <StatusBadge status={approval.status} />
                    <span className="muted-text">{approval.attendanceStatus}</span>
                  </div>
                  <div className="meta-grid">
                    <span>Shift Assignment: #{approval.shiftAssignmentId}</span>
                    <span>Business Date: {approval.businessDate ?? 'N/A'}</span>
                    <span>Work Hours: {approval.workHours ?? 'N/A'}</span>
                    <span>Overtime: {approval.overtimeHours ?? 'N/A'}</span>
                  </div>
                  <Button asChild size="sm" variant="secondary">
                    <Link to={`/workforce/attendance-approvals/${approval.shiftAssignmentId}`}>Open detail</Link>
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </>
      ) : null}
    </DashboardLayout>
  )
}
