import { Link } from 'react-router-dom'
import type { FernPrincipal } from '@core/auth/auth.types'
import { Button, Card } from '@design-system/index'
import type { ExportJob } from '../model/reportExport.types'
import { canDownloadExport, canOpenExportJob, canPreviewExport, getExportStatusDescription } from '../services/reportsUiPolicy.service'
import { ExportStatusBadge } from './ExportStatusBadge'

interface ExportJobCardProps {
  job: ExportJob
  principal: FernPrincipal | null
  onRemove?: (jobId: number) => void
}

export function ExportJobCard({ job, principal, onRemove }: ExportJobCardProps) {
  const canOpen = canOpenExportJob(principal, job)
  const canPreview = canPreviewExport(principal, job)
  const canDownload = canDownloadExport(principal, job)

  return (
    <Card title={`Export #${job.exportJobId}`}>
      <div className="page-stack">
        <div className="page-header">
          <ExportStatusBadge status={job.status} />
          <span className="muted-text">{job.dataset}</span>
        </div>
        <p className="muted-text">{getExportStatusDescription(job)}</p>
        <div className="meta-grid">
          <span>Format: {job.format}</span>
          <span>Requested: {new Date(job.requestedAt).toLocaleString()}</span>
          <span>Rows: {job.rowCount ?? 'N/A'}</span>
        </div>
        <div className="form-actions align-start">
          {canOpen ? (
            <Button asChild size="sm" variant="secondary">
              <Link to={`/reports/export-jobs/${job.exportJobId}`}>Details</Link>
            </Button>
          ) : null}
          {canPreview ? (
            <Button asChild size="sm" variant="secondary">
              <Link to={`/reports/export-jobs/${job.exportJobId}/preview`}>Preview</Link>
            </Button>
          ) : null}
          {canDownload ? (
            <Button asChild size="sm" variant="secondary">
              <Link to={`/reports/export-jobs/${job.exportJobId}/download`}>Download</Link>
            </Button>
          ) : null}
          {onRemove ? (
            <Button onClick={() => onRemove(job.exportJobId)} size="sm" variant="ghost">
              Remove
            </Button>
          ) : null}
        </div>
      </div>
    </Card>
  )
}
