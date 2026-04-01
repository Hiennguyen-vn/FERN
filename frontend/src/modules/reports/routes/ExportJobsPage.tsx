import { useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, EmptyState, ErrorState, PermissionDeniedInline, ReadonlyBanner } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useCreateExportJob } from '../hooks/useCreateExportJob'
import { useExportJobs } from '../hooks/useExportJobs'
import { ExportJobCard } from '../components/ExportJobCard'
import { ExportJobTable } from '../components/ExportJobTable'
import { ReportFilterPanel } from '../components/ReportFilterPanel'
import {
  canCreateExport,
  canInspectExportJobs,
  canReadRevenueReport,
  canViewExportJobs,
  getCreatableExportDatasets,
} from '../services/reportsUiPolicy.service'

export function ExportJobsPage() {
  usePageTitle('Export Jobs')
  const principal = usePrincipal()
  const canOpenPage = canViewExportJobs(principal)
  const canInspectJobs = canInspectExportJobs(principal)
  const canQueueExports = canCreateExport(principal)
  const allowedDatasets = getCreatableExportDatasets(principal)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const createExport = useCreateExportJob()
  const exportJobs = useExportJobs({ enabled: canInspectJobs })

  if (!canOpenPage) {
    return (
      <DashboardLayout
        description="Create export jobs and monitor recent jobs as part of the broader reports workspace."
        title="Export Jobs"
      >
        <PermissionDeniedInline message="Bạn cần report.read, report.export, report.payroll.read hoặc report.payroll.export để mở export jobs." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/reports">Reports dashboard</Link>
          </Button>
          {canReadRevenueReport(principal) ? (
            <Button asChild size="sm" variant="ghost">
              <Link to="/reports/revenue">Revenue report</Link>
            </Button>
          ) : null}
        </div>
      }
      description="Create export jobs and monitor recent jobs as part of the broader reports workspace."
      title="Export Jobs"
    >
      <ReadonlyBanner
        message={
          canInspectJobs
            ? 'Export jobs hiển thị preview/download theo quyền đọc dataset tương ứng. Export creation vẫn tách biệt khỏi read access.'
            : 'Create-only mode: bạn có thể queue export jobs cho dataset được phép, nhưng recent job inspection, preview và download vẫn cần quyền đọc report tương ứng.'
        }
      />

      {canQueueExports ? (
        <ReportFilterPanel
          allowedDatasets={allowedDatasets}
          isSubmitting={createExport.isPending}
          onSubmit={async (payload) => {
            setSubmitError(null)
            try {
              await createExport.mutateAsync(payload)
            } catch (error) {
              setSubmitError(error instanceof Error ? error.message : 'Failed to queue export job')
            }
          }}
        />
      ) : (
        <Card title="Create export job unavailable">
          <p className="muted-text">Current principal can inspect readable export jobs here, but cannot queue a new export because no export permission is assigned.</p>
        </Card>
      )}

      {submitError ? (
        <ErrorState message={submitError} title="Không thể queue export job" />
      ) : null}

      {canInspectJobs ? (
        <section className="page-stack">
          <div className="page-header">
            <div>
              <h2>Export job history</h2>
              <p className="muted-text">Job history is loaded from the backend export queue and filtered server-side by readable datasets.</p>
            </div>
          </div>
          {exportJobs.isLoading ? (
            <Card title="Loading export jobs">
              <p className="muted-text">Refreshing export jobs from the server-backed history endpoint...</p>
            </Card>
          ) : null}
          {exportJobs.error ? (
            <ErrorState
              actionLabel="Retry"
              message={exportJobs.error instanceof Error ? exportJobs.error.message : 'Failed to load export jobs'}
              onAction={() => void exportJobs.refresh()}
              title="Không thể tải export jobs"
            />
          ) : null}
          {!exportJobs.isLoading && !exportJobs.error && exportJobs.jobs.length === 0 ? (
            <EmptyState
              description="Queue export đầu tiên ở panel phía trên để bắt đầu theo dõi progress, preview và download từ server-backed history."
              title="No export jobs yet"
            />
          ) : null}
          {exportJobs.jobs.length > 0 ? <ExportJobTable jobs={exportJobs.jobs} principal={principal} /> : null}
          {exportJobs.jobs.length > 0 ? (
            <div className="card-grid">
              {exportJobs.jobs.map((job) => (
                <ExportJobCard
                  key={job.exportJobId}
                  job={job}
                  principal={principal}
                />
              ))}
            </div>
          ) : null}
        </section>
      ) : (
        <Card title="Recent export inspection requires read access">
          <p className="muted-text">The backend only exposes export job detail endpoints behind report read permissions, so this screen stays in create-only mode for export-only users.</p>
        </Card>
      )}
    </DashboardLayout>
  )
}
