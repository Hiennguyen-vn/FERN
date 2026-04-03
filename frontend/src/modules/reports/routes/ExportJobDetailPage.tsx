import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { AsyncJobProgress, Button, Card, EmptyState, ErrorState, PermissionDeniedInline, ReadonlyBanner } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useExportJob } from '../hooks/useExportJob'
import { getReportsPermissionDeniedMessage, isReportsPermissionDenied } from '../services/reportsError.service'
import { canDownloadExport, canPreviewExport, getExportStatusDescription } from '../services/reportsUiPolicy.service'

export function ExportJobDetailPage() {
  const params = useParams<{ jobId: string }>()
  const jobId = params.jobId ? Number(params.jobId) : null
  const principal = usePrincipal()
  const { data: job, error, isLoading, refetch } = useExportJob(jobId)

  usePageTitle(jobId ? `Export Job #${jobId}` : 'Export Job')

  return (
    <DashboardLayout description="Poll and inspect a single export job." title={`Export Job ${jobId ?? ''}`}>
      {isLoading ? (
        <Card title="Loading export job">
          <p className="muted-text">Loading export job...</p>
        </Card>
      ) : null}
      {error && isReportsPermissionDenied(error) ? (
        <PermissionDeniedInline message={getReportsPermissionDeniedMessage('export job này')} />
      ) : null}
      {error && !isReportsPermissionDenied(error) ? (
        <ErrorState
          actionLabel="Retry"
          message={error instanceof Error ? error.message : 'Unable to load export job'}
          onAction={() => void refetch()}
          title="Không thể tải export job"
        />
      ) : null}
      {!isLoading && !error && !job ? (
        <EmptyState
          description="Export job này không tồn tại, hoặc không còn truy cập được từ frontend hiện tại."
          title="Export job not found"
        />
      ) : null}
      {job ? (
        <>
          {['COMPLETED', 'FAILED'].includes(job.status.toUpperCase()) ? (
            <ReadonlyBanner message="Export job này đã ở trạng thái terminal. Bạn chỉ có thể xem preview hoặc tải file nếu backend cho phép." />
          ) : null}
          <AsyncJobProgress
            actionLabel={canPreviewExport(principal, job) ? 'Open preview' : undefined}
            completedAt={job.completedAt}
            description={getExportStatusDescription(job)}
            onAction={canPreviewExport(principal, job) ? () => (window.location.href = `/reports/export-jobs/${job.exportJobId}/preview`) : undefined}
            requestedAt={job.requestedAt}
            status={job.status}
            title={`Export #${job.exportJobId}`}
          />
          <Card title="Metadata">
            <div className="meta-grid">
              <span>Dataset: {job.dataset}</span>
              <span>Format: {job.format}</span>
              <span>Rows: {job.rowCount ?? 'N/A'}</span>
              <span>Expires: {job.expiresAt ? new Date(job.expiresAt).toLocaleString() : 'N/A'}</span>
            </div>
            <div className="form-actions align-start">
              {canPreviewExport(principal, job) ? (
                <Button asChild size="sm" variant="secondary">
                  <Link to={`/reports/export-jobs/${job.exportJobId}/preview`}>Preview</Link>
                </Button>
              ) : null}
              {canDownloadExport(principal, job) ? (
                <Button asChild size="sm" variant="secondary">
                  <Link to={`/reports/export-jobs/${job.exportJobId}/download`}>Download</Link>
                </Button>
              ) : null}
            </div>
          </Card>
        </>
      ) : null}
    </DashboardLayout>
  )
}
