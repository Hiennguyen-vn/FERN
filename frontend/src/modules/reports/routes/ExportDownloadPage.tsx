import { useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Button, Card, EmptyState } from '@design-system/index'
import { appConfig } from '@core/config/appConfig'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { startBrowserDownload } from '../services/download.service'

export function ExportDownloadPage() {
  const params = useParams<{ jobId: string }>()
  const jobId = params.jobId ? Number(params.jobId) : null

  usePageTitle(jobId ? `Export Download #${jobId}` : 'Export Download')

  const downloadUrl = jobId !== null ? `${appConfig.apiBaseUrl}/reports/exports/${jobId}/download` : null

  useEffect(() => {
    if (downloadUrl === null) {
      return
    }

    startBrowserDownload(downloadUrl)
  }, [downloadUrl])

  return (
    <DashboardLayout description="The browser should start downloading the export artifact." title={`Download Export ${jobId ?? ''}`}>
      {downloadUrl === null ? (
        <EmptyState
          description="Thiếu export job id nên frontend chưa thể khởi động download."
          title="Download target missing"
        />
      ) : null}
      <Card title="Download started">
        <p className="muted-text">If the download does not start automatically, refresh the page and try again.</p>
        {downloadUrl ? (
          <div className="form-actions align-start">
            <Button onClick={() => startBrowserDownload(downloadUrl)} size="sm" variant="secondary">
              Retry download
            </Button>
          </div>
        ) : null}
      </Card>
    </DashboardLayout>
  )
}
