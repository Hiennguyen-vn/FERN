import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, EmptyState, PermissionDeniedInline, ReadonlyBanner } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ExportJobTable } from '../components/ExportJobTable'
import { ReportSummaryCards } from '../components/ReportSummaryCards'
import { useReportDashboard } from '../hooks/useReportDashboard'
import {
  canInspectExportJobs,
  canOpenReportDashboard,
  canReadInventoryReport,
  canReadPayrollReport,
  canReadRevenueReport,
  canViewExportJobs,
} from '../services/reportsUiPolicy.service'

export function ReportsDashboardPage() {
  usePageTitle('Reports')
  const principal = usePrincipal()
  const canOpenDashboard = canOpenReportDashboard(principal)
  const canInspectExports = canInspectExportJobs(principal)
  const dashboard = useReportDashboard(canInspectExports)
  const reportSurfaces = [
    canReadRevenueReport(principal)
      ? {
          description: 'Async export-aware daily summary with region/company filters.',
          title: 'Revenue report',
          to: '/reports/revenue',
        }
      : null,
    canReadRevenueReport(principal)
      ? {
          description: 'Thống kê doanh thu realtime theo từng outlet hôm nay. CSV export.',
          title: 'Outlet Revenue Summary',
          to: '/reports/outlet-revenue',
        }
      : null,
    canReadInventoryReport(principal)
      ? {
          description: 'Stock snapshot and inventory movement breakdown by outlet.',
          title: 'Inventory report',
          to: '/reports/inventory',
        }
      : null,
    canReadPayrollReport(principal)
      ? {
          description: 'Payroll summary, run inspection, and export workflow.',
          title: 'Payroll report',
          to: '/reports/payroll',
        }
      : null,
    canViewExportJobs(principal)
      ? {
          description: canInspectExports
            ? 'Open the async export center for previews and downloads.'
            : 'Queue export jobs from a create-only export center.',
          title: 'Export jobs',
          to: '/reports/export-jobs',
        }
      : null,
  ].filter((surface): surface is { description: string; title: string; to: string } => Boolean(surface))

  if (!canOpenDashboard) {
    return (
      <DashboardLayout
        title="Reports"
        description="Summary-first reporting workspace with export-aware flows."
      >
        <PermissionDeniedInline message="Bạn cần report.read, report.export, report.payroll.read hoặc report.payroll.export để mở reports workspace." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Reports"
      description="Command center cho revenue, inventory, payroll reports và async export jobs."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/reports/export-jobs">Open export jobs</Link>
        </Button>
      }
    >
      <ReadonlyBanner
        message={
          canInspectExports
            ? 'Mỗi report surface ưu tiên bộ lọc ổn định, summary dễ đọc và async export integration thay vì dashboard trang trí.'
            : 'Bạn đang ở create-only mode. Có thể queue export jobs, nhưng preview, download và recent-job inspection vẫn cần quyền đọc report tương ứng.'
        }
      />

      {canInspectExports ? <ReportSummaryCards items={dashboard.summaryCards} /> : null}

      <div className="card-grid dashboard-module-grid">
        {reportSurfaces.map((surface) => (
          <Card className="dashboard-module-card" key={surface.to} title={surface.title}>
            <p className="muted-text">{surface.description}</p>
            <Button asChild size="sm" variant="secondary">
              <Link to={surface.to}>Open</Link>
            </Button>
          </Card>
        ))}
      </div>

      {canInspectExports ? (
        <section className="page-stack">
          <div className="page-header">
            <div>
              <h2>Recent export activity</h2>
              <p className="muted-text">Recent jobs are stored client-side and refreshed from backend detail endpoints.</p>
            </div>
          </div>
          {dashboard.restrictedJobCount > 0 ? (
            <div className="inline-banner inline-banner-warning" role="status">
              {dashboard.restrictedJobCount} recent export job{dashboard.restrictedJobCount > 1 ? 's are' : ' is'} hidden because the current principal lacks read access for those datasets.
            </div>
          ) : null}
          {dashboard.isLoading ? (
            <Card title="Loading export activity">
              <p className="muted-text">Refreshing recent export jobs for the reports dashboard...</p>
            </Card>
          ) : null}
          {dashboard.error ? (
            <Card title="Recent export activity unavailable">
              <p className="error-text">{dashboard.error instanceof Error ? dashboard.error.message : 'Failed to load recent export activity.'}</p>
              <Button onClick={() => void dashboard.refresh()} size="sm" variant="secondary">
                Retry
              </Button>
            </Card>
          ) : null}
          {!dashboard.isLoading && !dashboard.error && dashboard.jobs.length === 0 ? (
            <EmptyState
              description="Chạy revenue, inventory hoặc payroll report để tạo export job đầu tiên cho reports workspace."
              title="No recent export jobs"
            />
          ) : null}
          {dashboard.jobs.length > 0 ? <ExportJobTable jobs={dashboard.jobs} principal={principal} /> : null}
        </section>
      ) : null}
    </DashboardLayout>
  )
}
