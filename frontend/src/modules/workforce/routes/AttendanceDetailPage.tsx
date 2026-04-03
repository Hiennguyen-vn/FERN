import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { Button, Card, EmptyState, ErrorState, ReadonlyBanner, StatusBadge, Textarea } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAttendanceApproval } from '../hooks/useAttendanceApprovals'
import { useReviewAttendance } from '../hooks/useReviewAttendance'
import { canReviewAttendance } from '../services/attendanceUiPolicy.service'

export function AttendanceDetailPage() {
  const params = useParams<{ shiftAssignmentId: string }>()
  const shiftAssignmentId = params.shiftAssignmentId ? Number(params.shiftAssignmentId) : null
  const [comments, setComments] = useState('')
  const approvalQuery = useAttendanceApproval(shiftAssignmentId)
  const reviewMutation = useReviewAttendance()

  usePageTitle(shiftAssignmentId ? `Attendance Detail #${shiftAssignmentId}` : 'Attendance Detail')

  const approval = approvalQuery.data
  const isReadonly = approval ? !canReviewAttendance(approval.status) : false

  return (
    <DashboardLayout
      description="Review or reject a single attendance approval record."
      title={`Attendance Detail ${shiftAssignmentId ?? ''}`}
    >
      {approvalQuery.isLoading ? (
        <Card title="Loading attendance approval">
          <p className="muted-text">Loading attendance approval...</p>
        </Card>
      ) : null}
      {approvalQuery.error ? (
        <ErrorState
          actionLabel="Retry"
          message={approvalQuery.error instanceof Error ? approvalQuery.error.message : 'Failed to load attendance approval'}
          onAction={() => void approvalQuery.refetch()}
          title="Không thể tải attendance approval"
        />
      ) : null}
      {!approvalQuery.isLoading && !approvalQuery.error && !approval ? (
        <EmptyState
          description="Attendance approval này không tồn tại, hoặc không còn truy cập được từ frontend hiện tại."
          title="Attendance approval not found"
        />
      ) : null}
      {approval ? (
        <>
          <Card title={`Approval #${approval.id}`}>
            <div className="page-stack">
              <div className="page-header">
                <StatusBadge status={approval.status} />
                <span className="muted-text">{approval.attendanceStatus}</span>
              </div>
              <div className="meta-grid">
                <span>Shift Assignment: #{approval.shiftAssignmentId}</span>
                <span>Business Date: {approval.businessDate ?? 'N/A'}</span>
                <span>Approved At: {approval.approvedAt ? new Date(approval.approvedAt).toLocaleString() : 'N/A'}</span>
                <span>Approved By: {approval.approvedByUserId ?? 'N/A'}</span>
              </div>
              <p className="muted-text">{approval.comments || 'No review comments yet.'}</p>
            </div>
          </Card>
          {isReadonly ? (
            <ReadonlyBanner message="Attendance approval này đã ở trạng thái terminal và hiện ở chế độ chỉ đọc." />
          ) : null}

          <Card title="Review decision">
            <div className="page-stack">
              <Textarea
                label="Comments"
                onChange={(event) => setComments(event.target.value)}
                placeholder="Add review comments"
                readOnly={isReadonly}
                value={comments}
              />
              {canReviewAttendance(approval.status) ? (
                <div className="form-actions align-start">
                  <Button
                    loading={reviewMutation.isPending}
                    onClick={() => {
                      if (shiftAssignmentId === null) {
                        return
                      }

                      void reviewMutation.mutateAsync({
                        shiftAssignmentId,
                        action: 'approve',
                        comments,
                      })
                    }}
                  >
                    Approve
                  </Button>
                  <Button
                    loading={reviewMutation.isPending}
                    onClick={() => {
                      if (shiftAssignmentId === null) {
                        return
                      }

                      void reviewMutation.mutateAsync({
                        shiftAssignmentId,
                        action: 'reject',
                        comments,
                      })
                    }}
                    variant="danger"
                  >
                    Reject
                  </Button>
                </div>
              ) : null}
              {reviewMutation.error ? (
                <ErrorState
                  message={reviewMutation.error instanceof Error ? reviewMutation.error.message : 'Failed to review attendance'}
                  title="Không thể review attendance"
                />
              ) : null}
            </div>
          </Card>
        </>
      ) : null}
    </DashboardLayout>
  )
}
