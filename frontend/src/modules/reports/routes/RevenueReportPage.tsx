import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  AsyncJobProgress,
  Button,
  EmptyState,
  ErrorState,
  FilterBar,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
} from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ExportPreviewPanel } from '../components/ExportPreviewPanel'
import { ReportSummaryCards } from '../components/ReportSummaryCards'
import { useRevenueReport } from '../hooks/useRevenueReport'
import { formatReportDateLabel, formatReportCurrency } from '../services/reportsReadModel.service'
import {
  canCreateExport,
  canDownloadExport,
  canOpenExportJob,
  canPreviewExport,
  canReadRevenueReport,
  getExportStatusDescription,
} from '../services/reportsUiPolicy.service'

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function firstDayOfMonthIso() {
  const date = new Date()
  date.setDate(1)
  return date.toISOString().slice(0, 10)
}

export function RevenueReportPage() {
  usePageTitle('Revenue Report')
  const principal = usePrincipal()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canRead = canReadRevenueReport(principal)
  const isSystemScoped = Boolean(principal?.scopeRoots.system)
  const regionOptions = useMemo<SelectOption[]>(
    () => regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    [regionIds],
  )
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso())
  const [toDate, setToDate] = useState(todayIso())
  const [regionFilter, setRegionFilter] = useState(selectedRegionId ? String(selectedRegionId) : '')
  const [limit, setLimit] = useState('20')
  const [submitError, setSubmitError] = useState<string | null>(null)
  const revenueReport = useRevenueReport()

  if (!canRead) {
    return (
      <DashboardLayout
        title="Revenue Report"
        description="Async export-aware revenue reporting workspace."
      >
        <PermissionDeniedInline message="Bạn cần report.read để mở revenue report." />
      </DashboardLayout>
    )
  }

  const regionId = regionFilter.trim() ? Number(regionFilter) : undefined
  const canExport = canCreateExport(
    principal,
    regionId || !isSystemScoped ? 'REGION_DAILY_SUMMARY' : 'COMPANY_DAILY_SUMMARY',
  )
  const canRunReport = Boolean(fromDate && toDate && (regionId || isSystemScoped))

  const summaryCards = [
    {
      description: revenueReport.activeFilters?.dataset === 'COMPANY_DAILY_SUMMARY' ? 'Company-wide daily summary.' : 'Region-scoped daily summary.',
      label: 'Report scope',
      tone: 'info' as const,
      value: revenueReport.activeFilters?.dataset === 'COMPANY_DAILY_SUMMARY' ? 'Company' : revenueReport.activeFilters?.regionId ? `Region #${revenueReport.activeFilters.regionId}` : 'Not run yet',
    },
    {
      description: 'Preview rows currently loaded from export preview endpoint.',
      label: 'Preview rows',
      value: revenueReport.summary.rowCount,
    },
    {
      description: 'Best-effort aggregated revenue amount from preview rows.',
      label: 'Revenue total',
      tone: 'success' as const,
      value: formatReportCurrency(revenueReport.summary.totalRevenue),
    },
    {
      description: 'Best-effort aggregated order count from preview rows.',
      label: 'Orders',
      tone: 'warning' as const,
      value: revenueReport.summary.totalOrders ?? '—',
    },
  ]

  return (
    <DashboardLayout
      title="Revenue Report"
      description="Daily revenue summary driven by async export preview so report filters and export workflow stay aligned."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/reports">Reports dashboard</Link>
          </Button>
          <Button asChild size="sm" variant="ghost">
            <Link to="/reports/export-jobs">Export jobs</Link>
          </Button>
        </div>
      }
    >
      <ReadonlyBanner message="Revenue report chạy qua export job workflow để tránh giả lập synchronous API không tồn tại. Bộ lọc hiện tại tạo export job rồi load preview khi job sẵn sàng." />

      {!canExport ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Revenue report dùng export pipeline hiện có. Bạn có thể mở màn hình vì có report.read, nhưng cần report.export để queue report run mới.
        </div>
      ) : null}

      {!isSystemScoped && !regionId ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Chọn region trong app shell hoặc nhập region filter trước khi chạy revenue report.
        </div>
      ) : null}

      <form
        onSubmit={async (event) => {
          event.preventDefault()
          if (!canRunReport) {
            return
          }

          setSubmitError(null)

          try {
            await revenueReport.runReport({
              fromDate,
              limit: Number(limit) || 20,
              regionId,
              toDate,
            })
          } catch (error) {
            setSubmitError(error instanceof Error ? error.message : 'Failed to queue revenue report export.')
          }
        }}
      >
        <FilterBar
          actions={
            <Button disabled={!canRunReport || !canExport} loading={revenueReport.isCreating} type="submit">
              Run revenue report
            </Button>
          }
          description="Chạy revenue report theo region/company summary rồi dùng preview/download từ export pipeline hiện có."
          title="Revenue filters"
        >
          {regionOptions.length > 0 ? (
            <Select
              label="Region"
              onChange={(event) => setRegionFilter(event.target.value)}
              options={regionOptions}
              placeholder={isSystemScoped ? 'Company-wide summary' : 'Select region'}
              value={regionFilter}
            />
          ) : (
            <Input
              label="Region ID"
              onChange={(event) => setRegionFilter(event.target.value)}
              placeholder={isSystemScoped ? 'Optional for company summary' : 'Region required'}
              type="number"
              value={regionFilter}
            />
          )}
          <Input
            label="From date"
            onChange={(event) => setFromDate(event.target.value)}
            type="date"
            value={fromDate}
          />
          <Input
            label="To date"
            onChange={(event) => setToDate(event.target.value)}
            type="date"
            value={toDate}
          />
          <Input
            label="Preview limit"
            onChange={(event) => setLimit(event.target.value)}
            placeholder="20"
            type="number"
            value={limit}
          />
        </FilterBar>
      </form>

      {submitError ? (
        <ErrorState message={submitError} title="Không thể queue revenue report" />
      ) : null}

      <ReportSummaryCards items={summaryCards} />

      {revenueReport.exportJob ? (
        <AsyncJobProgress
          actionLabel={canPreviewExport(principal, revenueReport.exportJob) ? 'Open preview' : undefined}
          completedAt={revenueReport.exportJob.completedAt}
          description={getExportStatusDescription(revenueReport.exportJob)}
          onAction={
            canPreviewExport(principal, revenueReport.exportJob)
              ? () => {
                  window.location.href = `/reports/export-jobs/${revenueReport.exportJob?.exportJobId}/preview`
                }
              : undefined
          }
          requestedAt={revenueReport.exportJob.requestedAt}
          status={revenueReport.exportJob.status}
          title={`Revenue export #${revenueReport.exportJob.exportJobId}`}
        />
      ) : (
        <EmptyState
          description="Chọn date range và chạy revenue report để load summary cards cùng preview rows."
          title="Revenue report chưa chạy"
        />
      )}

      {revenueReport.exportJobError ? (
        <ErrorState
          actionLabel="Retry"
          message={revenueReport.exportJobError instanceof Error ? revenueReport.exportJobError.message : 'Failed to poll revenue export job.'}
          onAction={() => window.location.reload()}
          title="Không thể tải revenue export job"
        />
      ) : null}

      {revenueReport.previewError ? (
        <ErrorState
          message={revenueReport.previewError instanceof Error ? revenueReport.previewError.message : 'Failed to load revenue preview.'}
          title="Không thể tải revenue preview"
        />
      ) : null}

      {revenueReport.preview ? (
        <section className="page-stack">
          <div className="page-header">
            <div>
              <h2>Revenue preview</h2>
              <p className="muted-text">
                Preview rows for {revenueReport.activeFilters?.dataset ?? 'selected revenue dataset'} from{' '}
                {formatReportDateLabel(revenueReport.activeFilters?.fromDate)} đến{' '}
                {formatReportDateLabel(revenueReport.activeFilters?.toDate)}.
              </p>
            </div>
            {revenueReport.exportJob ? (
              <div className="form-actions align-start">
                {canOpenExportJob(principal, revenueReport.exportJob) ? (
                  <Button asChild size="sm" variant="secondary">
                    <Link to={`/reports/export-jobs/${revenueReport.exportJob.exportJobId}`}>Job detail</Link>
                  </Button>
                ) : null}
                {canPreviewExport(principal, revenueReport.exportJob) ? (
                  <Button asChild size="sm" variant="secondary">
                    <Link to={`/reports/export-jobs/${revenueReport.exportJob.exportJobId}/preview`}>Preview route</Link>
                  </Button>
                ) : null}
                {canDownloadExport(principal, revenueReport.exportJob) ? (
                  <Button asChild size="sm" variant="ghost">
                    <Link to={`/reports/export-jobs/${revenueReport.exportJob.exportJobId}/download`}>Download</Link>
                  </Button>
                ) : null}
              </div>
            ) : null}
          </div>
          <ExportPreviewPanel rows={revenueReport.preview.rows} />
        </section>
      ) : null}
    </DashboardLayout>
  )
}
