import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Card, EmptyState, ErrorState, PermissionDeniedInline } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { getExportPreview } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import { ExportPreviewPanel } from '../components/ExportPreviewPanel'
import { getReportsPermissionDeniedMessage, isReportsPermissionDenied } from '../services/reportsError.service'

export function ExportPreviewPage() {
  const params = useParams<{ jobId: string }>()
  const jobId = params.jobId ? Number(params.jobId) : null
  const previewQuery = useQuery({
    enabled: jobId !== null,
    queryKey: reportQueryKeys.exportPreview(jobId ?? 0),
    queryFn: () => getExportPreview(jobId as number),
    refetchInterval: 3_000,
  })

  usePageTitle(jobId ? `Export Preview #${jobId}` : 'Export Preview')

  return (
    <DashboardLayout description="Preview rows returned by the export preview endpoint." title={`Export Preview ${jobId ?? ''}`}>
      {previewQuery.isLoading ? (
        <Card title="Loading preview">
          <p className="muted-text">Fetching preview rows...</p>
        </Card>
      ) : null}
      {previewQuery.error && isReportsPermissionDenied(previewQuery.error) ? (
        <PermissionDeniedInline message={getReportsPermissionDeniedMessage('export preview này')} />
      ) : null}
      {previewQuery.error && !isReportsPermissionDenied(previewQuery.error) ? (
        <ErrorState
          actionLabel="Retry"
          message={previewQuery.error instanceof Error ? previewQuery.error.message : 'Unable to load preview'}
          onAction={() => void previewQuery.refetch()}
          title="Không thể tải export preview"
        />
      ) : null}
      {!previewQuery.isLoading && !previewQuery.error && !previewQuery.data ? (
        <EmptyState
          description="Chưa có preview nào cho export job này, hoặc export job không tồn tại."
          title="Preview not available"
        />
      ) : null}
      {previewQuery.data ? <ExportPreviewPanel rows={previewQuery.data.rows} /> : null}
    </DashboardLayout>
  )
}
