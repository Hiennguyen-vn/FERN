import { Link } from 'react-router-dom'
import { useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Button, Card, EmptyState, ErrorState } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useCreateExportJob } from '../hooks/useCreateExportJob'
import { useExportJobs } from '../hooks/useExportJobs'
import { removeRecentExportJobId } from '../services/exportHistory.service'
import { ExportJobCard } from '../components/ExportJobCard'
import { ExportJobTable } from '../components/ExportJobTable'
import { ReportFilterPanel } from '../components/ReportFilterPanel'

export function ExportJobsPage() {
  usePageTitle('Export Jobs')

  const [historyVersion, setHistoryVersion] = useState(0)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const createExport = useCreateExportJob()
  const exportJobs = useExportJobs()

  return (
    <DashboardLayout
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/reports">Reports dashboard</Link>
          </Button>
          <Button asChild size="sm" variant="ghost">
            <Link to="/reports/revenue">Revenue report</Link>
          </Button>
        </div>
      }
      description="Create export jobs and monitor recent jobs as part of the broader reports workspace."
      title="Export Jobs"
    >
      <ReportFilterPanel
        isSubmitting={createExport.isPending}
        onSubmit={async (payload) => {
          setSubmitError(null)
          try {
            await createExport.mutateAsync(payload)
            setHistoryVersion((value) => value + 1)
          } catch (error) {
            setSubmitError(error instanceof Error ? error.message : 'Failed to queue export job')
          }
        }}
      />

      {submitError ? (
        <ErrorState message={submitError} title="Không thể queue export job" />
      ) : null}

      <section className="page-stack">
        <div className="page-header">
          <div>
            <h2>Recent export jobs</h2>
            <p className="muted-text">Recent jobs are stored client-side because the backend does not expose a list endpoint. Revenue, inventory, and payroll reports all feed this queue.</p>
          </div>
        </div>
        {exportJobs.isLoading ? (
          <Card title="Loading recent jobs">
            <p className="muted-text">Refreshing recent export jobs from the job detail endpoints...</p>
          </Card>
        ) : null}
        {exportJobs.error ? (
          <ErrorState
            actionLabel="Retry"
            message={exportJobs.error instanceof Error ? exportJobs.error.message : 'Failed to load recent export jobs'}
            onAction={() => void exportJobs.refresh()}
            title="Không thể tải recent export jobs"
          />
        ) : null}
        {!exportJobs.isLoading && !exportJobs.error && exportJobs.jobs.length === 0 ? (
          <EmptyState
            description="Queue export đầu tiên ở panel phía trên để bắt đầu theo dõi progress, preview và download."
            title="No recent export jobs"
          />
        ) : null}
        {exportJobs.jobs.length > 0 ? <ExportJobTable jobs={exportJobs.jobs} /> : null}
        {exportJobs.jobs.length > 0 ? (
          <div className="card-grid">
            {exportJobs.jobs.map((job) => (
              <ExportJobCard
                key={`${historyVersion}-${job.exportJobId}`}
                job={job}
                onRemove={(jobId) => {
                  removeRecentExportJobId(jobId)
                  setHistoryVersion((value) => value + 1)
                }}
              />
            ))}
          </div>
        ) : null}
      </section>
    </DashboardLayout>
  )
}
