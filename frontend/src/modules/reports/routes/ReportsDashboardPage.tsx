import { Link } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
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
  const reportModeStats = canInspectExports
    ? dashboard.summaryCards.map((item) => ({ label: item.label, value: item.value }))
    : reportSurfaces.slice(0, 2).map((item) => ({ label: item.title, value: 'Ready' }))

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

      <section className="reports-command-grid">
        <div className="surface-panel reports-surface-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Published reports</p>
              <h2>Available surfaces</h2>
            </div>
          </div>
          <div className="reports-surface-list">
            {reportSurfaces.map((surface) => (
              <Link className="reports-surface-row" key={surface.to} to={surface.to}>
                <div className="reports-surface-copy">
                  <strong>{surface.title}</strong>
                  <p className="muted-text">{surface.description}</p>
                </div>
                <span className="reports-surface-open">
                  <AppIcon name="arrow_outward" size="sm" />
                </span>
              </Link>
            ))}
          </div>
        </div>

        <aside className="surface-panel reports-note-panel">
          <p className="eyebrow">Export mode</p>
          <strong className="action-summary-title">
            {canInspectExports ? 'Readable export history enabled' : 'Create-only export center'}
          </strong>
          <p className="muted-text">
            {canInspectExports
              ? 'Use report surfaces for analysis, then inspect backend-backed export jobs in one place.'
              : 'Queue new jobs first. Preview, download, and history inspection remain permission-gated.'}
          </p>
          <div className="reports-note-stats">
            {reportModeStats.map((item) => (
              <div className="reports-note-stat" key={item.label}>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
              </div>
            ))}
          </div>
          <Button asChild size="sm" variant="secondary">
            <Link to="/reports/export-jobs">Open export jobs</Link>
          </Button>
        </aside>
      </section>

      {canInspectExports ? (
        <section className="page-stack">
          <div className="page-header">
            <div>
              <h2>Export activity</h2>
              <p className="muted-text">Export activity is loaded from the backend history endpoint and filtered by readable datasets.</p>
            </div>
          </div>
          {dashboard.isLoading ? (
            <Card title="Loading export activity">
              <p className="muted-text">Refreshing export jobs for the reports dashboard...</p>
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
              title="No export jobs"
            />
          ) : null}
          {dashboard.jobs.length > 0 ? <ExportJobTable jobs={dashboard.jobs} principal={principal} /> : null}
        </section>
      ) : null}
    </DashboardLayout>
  )
}
