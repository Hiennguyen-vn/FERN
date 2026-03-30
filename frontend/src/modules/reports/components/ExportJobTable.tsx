import { Link } from 'react-router-dom'
import { Button, DataTable } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import type { ExportJob } from '../model/reportExport.types'
import { canDownloadExport, canPreviewExport } from '../services/reportsUiPolicy.service'
import { ExportStatusBadge } from './ExportStatusBadge'

interface ExportJobTableProps {
  jobs: ExportJob[]
}

export function ExportJobTable({ jobs }: ExportJobTableProps) {
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
      render: (job) => (
        <div className="table-actions">
          <Button asChild size="sm" variant="ghost">
            <Link to={`/reports/export-jobs/${job.exportJobId}`}>Open</Link>
          </Button>
          {canPreviewExport(job) ? (
            <Button asChild size="sm" variant="ghost">
              <Link to={`/reports/export-jobs/${job.exportJobId}/preview`}>Preview</Link>
            </Button>
          ) : null}
          {canDownloadExport(job) ? (
            <Button asChild size="sm" variant="ghost">
              <Link to={`/reports/export-jobs/${job.exportJobId}/download`}>Download</Link>
            </Button>
          ) : null}
        </div>
      ),
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
