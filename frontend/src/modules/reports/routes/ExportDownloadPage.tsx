import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, EmptyState, ErrorState, PermissionDeniedInline } from '@design-system/index'
import { appConfig } from '@core/config/appConfig'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useExportJob } from '../hooks/useExportJob'
import { startBrowserDownload } from '../services/download.service'
import { getReportsPermissionDeniedMessage, isReportsPermissionDenied } from '../services/reportsError.service'
import { canDownloadExport } from '../services/reportsUiPolicy.service'

export function ExportDownloadPage() {
  const params = useParams<{ jobId: string }>()
  const jobId = params.jobId ? Number(params.jobId) : null
  const principal = usePrincipal()
  const { data: job, error, isLoading, refetch } = useExportJob(jobId)
  const [hasStartedDownload, setHasStartedDownload] = useState(false)

  usePageTitle(jobId ? `Export Download #${jobId}` : 'Export Download')

  // Use backend-provided signed download URL when available (e.g. S3 presigned).
  // Fall back to the gateway streaming endpoint only if the job has no downloadUrl.
  const downloadUrl = jobId !== null
    ? (job?.downloadUrl ?? `${appConfig.apiBaseUrl}/reports/exports/${jobId}/download`)
    : null
  const canStartDownload = Boolean(job && downloadUrl && canDownloadExport(principal, job))

  useEffect(() => {
    if (!canStartDownload || hasStartedDownload || downloadUrl === null) {
      return
    }

    startBrowserDownload(downloadUrl)
    setHasStartedDownload(true)
  }, [canStartDownload, downloadUrl, hasStartedDownload])

  return (
    <DashboardLayout description="The browser should start downloading the export artifact." title={`Download Export ${jobId ?? ''}`}>
      {downloadUrl === null ? (
        <EmptyState
          description="Thiếu export job id nên frontend chưa thể khởi động download."
          title="Download target missing"
        />
      ) : null}
      {downloadUrl && isLoading ? (
        <Card title="Preparing download">
          <p className="muted-text">Checking export job status and access before starting the browser download...</p>
        </Card>
      ) : null}
      {downloadUrl && error && isReportsPermissionDenied(error) ? (
        <PermissionDeniedInline message={getReportsPermissionDeniedMessage('export download này')} />
      ) : null}
      {downloadUrl && error && !isReportsPermissionDenied(error) ? (
        <ErrorState
          actionLabel="Retry"
          message={error instanceof Error ? error.message : 'Unable to prepare export download'}
          onAction={() => void refetch()}
          title="Không thể chuẩn bị export download"
        />
      ) : null}
      {downloadUrl && !isLoading && !error && !job ? (
        <EmptyState
          description="Export job này không tồn tại, hoặc không còn truy cập được từ frontend hiện tại."
          title="Export job not found"
        />
      ) : null}
      {downloadUrl && job && !canDownloadExport(principal, job) ? (
        <EmptyState
          description="Export artifact chỉ có thể tải khi job đã COMPLETED và principal có quyền đọc dataset tương ứng."
          title="Download not ready"
        />
      ) : null}
      {downloadUrl && job && canDownloadExport(principal, job) ? (
        <Card title={hasStartedDownload ? 'Download started' : 'Download ready'}>
          <p className="muted-text">
            {hasStartedDownload
              ? 'If the download does not start automatically, refresh the page and try again.'
              : 'Export artifact is ready. Start the browser download from this page.'}
          </p>
          <div className="form-actions align-start">
            <Button onClick={() => startBrowserDownload(downloadUrl)} size="sm" variant="secondary">
              Retry download
            </Button>
          </div>
        </Card>
      ) : null}
    </DashboardLayout>
  )
}
