import { Link } from 'react-router-dom'
import type { FernPrincipal } from '@core/auth/auth.types'
import { Button, DataTable } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import type { ExportJob } from '../model/reportExport.types'
import { canDownloadExport, canOpenExportJob, canPreviewExport } from '../services/reportsUiPolicy.service'
import { ExportStatusBadge } from './ExportStatusBadge'

interface ExportJobTableProps {
  jobs: ExportJob[]
  principal: FernPrincipal | null
}

export function ExportJobTable({ jobs, principal }: ExportJobTableProps) {
  const columns: Array<DataTableColumn<ExportJob>> = [
    {
      key: 'job',
      header: 'Job',
      render: (job) => `#${job.exportJobId}`,
    },
    {
      key: 'dataset',
      header: 'Dataset',
      render: (job) => job.dataset,
    },
    {
      key: 'status',
      header: 'Status',
      render: (job) => <ExportStatusBadge status={job.status} />,
    },
    {
      key: 'requestedAt',
      header: 'Requested',
      render: (job) => new Date(job.requestedAt).toLocaleString(),
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (job) => {
        const canOpen = canOpenExportJob(principal, job)
        const canPreview = canPreviewExport(principal, job)
        const canDownload = canDownloadExport(principal, job)

        return (
          <div className="table-actions">
            {canOpen ? (
              <Button asChild size="sm" variant="ghost">
                <Link to={`/reports/export-jobs/${job.exportJobId}`}>Open</Link>
              </Button>
            ) : null}
            {canPreview ? (
              <Button asChild size="sm" variant="ghost">
                <Link to={`/reports/export-jobs/${job.exportJobId}/preview`}>Preview</Link>
              </Button>
            ) : null}
            {canDownload ? (
              <Button asChild size="sm" variant="ghost">
                <Link to={`/reports/export-jobs/${job.exportJobId}/download`}>Download</Link>
              </Button>
            ) : null}
          </div>
        )
      },
    },
  ]

  return (
    <DataTable
      columns={columns}
      emptyDescription="No export jobs have been created from this browser yet."
      emptyTitle="No recent export jobs"
      rows={jobs}
    />
  )
}
