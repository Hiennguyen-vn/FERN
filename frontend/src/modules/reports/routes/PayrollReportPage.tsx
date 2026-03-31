import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  AsyncJobProgress,
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  FilterBar,
  Input,
  MaskedField,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ReportSummaryCards } from '../components/ReportSummaryCards'
import { useCreateExportJob } from '../hooks/useCreateExportJob'
import { usePayrollReport } from '../hooks/usePayrollReport'
import type {
  ReportPayrollRunAllocation,
  ReportPayrollRunEmployee,
} from '../model/payrollReport.types'
import { getReportsErrorMessage } from '../services/reportsError.service'
import {
  buildPayrollDetailSummary,
  buildPayrollSummaryCards,
  formatReportCurrency,
  formatReportDateLabel,
} from '../services/reportsReadModel.service'
import {
  canDownloadExport,
  canCreateExport,
  canOpenExportJob,
  canPreviewExport,
  canReadPayrollReportDetail,
  canReadPayrollReport,
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

export function PayrollReportPage() {
  usePageTitle('Payroll Report')
  const principal = usePrincipal()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canRead = canReadPayrollReport(principal)
  const canReadDetail = canReadPayrollReportDetail(principal)
  const createExport = useCreateExportJob()
  const [lastExportJob, setLastExportJob] = useState<Awaited<ReturnType<typeof createExport.mutateAsync>> | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [regionFilter, setRegionFilter] = useState(selectedRegionId ? String(selectedRegionId) : '')
  const [fromDate, setFromDate] = useState(firstDayOfMonthIso())
  const [toDate, setToDate] = useState(todayIso())
  const [selectedRunId, setSelectedRunId] = useState('')
  const regionOptions = useMemo<SelectOption[]>(
    () => regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    [regionIds],
  )

  const regionId = regionFilter.trim() ? Number(regionFilter) : undefined
  const canExport = canCreateExport(principal, selectedRunId ? 'PAYROLL_RUN' : 'PAYROLL_SUMMARY')
  const payrollReport = usePayrollReport(
    regionId && fromDate && toDate
      ? {
          fromDate,
          regionId,
          toDate,
        }
      : null,
    {
      enabled: canRead,
      runId: selectedRunId ? Number(selectedRunId) : undefined,
    },
  )

  const runOptions = useMemo<SelectOption[]>(
    () =>
      (payrollReport.runsQuery.data ?? []).map((run) => ({
        label: `${run.runCode} · ${run.status}`,
        value: String(run.id),
      })),
    [payrollReport.runsQuery.data],
  )

  const employeeColumns = useMemo<Array<DataTableColumn<ReportPayrollRunEmployee>>>(
    () => [
      {
        key: 'employee',
        header: 'Employee',
        render: (row) => `#${row.employeeId}`,
      },
      {
        key: 'outlet',
        header: 'Outlet',
        render: (row) => (row.outletId ? `#${row.outletId}` : '—'),
      },
      {
        key: 'businessDate',
        header: 'Business date',
        render: (row) => formatReportDateLabel(row.businessDate),
      },
      {
        key: 'grossPay',
        header: 'Gross pay',
        render: (row) => (canReadDetail ? formatReportCurrency(row.grossPay) : '••••••'),
      },
      {
        key: 'netPay',
        header: 'Net pay',
        render: (row) => (canReadDetail ? formatReportCurrency(row.netPay) : '••••••'),
      },
      {
        key: 'tax',
        header: 'Tax',
        render: (row) => (canReadDetail ? formatReportCurrency(row.taxAmount) : '••••••'),
      },
    ],
    [canReadDetail],
  )

  const allocationColumns = useMemo<Array<DataTableColumn<ReportPayrollRunAllocation>>>(
    () => [
      {
        key: 'outlet',
        header: 'Outlet',
        render: (row) => (row.outletId ? `#${row.outletId}` : '—'),
      },
      {
        key: 'amount',
        header: 'Allocated amount',
        render: (row) => formatReportCurrency(row.totalAmount),
      },
    ],
    [],
  )

  if (!canRead) {
    return (
      <DashboardLayout title="Payroll Report" description="Payroll reporting workspace for HR/Finance alignment.">
        <PermissionDeniedInline message="Bạn cần report.payroll.read để mở payroll report." />
      </DashboardLayout>
    )
  }

  const payrollDetailSummary = buildPayrollDetailSummary(payrollReport.runDetailQuery.data)

  async function queuePayrollExport() {
    if (!regionId) {
      return
    }

    setActionError(null)

    try {
      const hasRunSelection = Boolean(selectedRunId)
      const job = await createExport.mutateAsync(
        hasRunSelection
          ? {
              dataset: 'PAYROLL_RUN',
              format: 'CSV',
              payrollRunId: Number(selectedRunId),
            }
          : {
              dataset: 'PAYROLL_SUMMARY',
              format: 'CSV',
              fromDate,
              regionId,
              toDate,
            },
      )
      setLastExportJob(job)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to queue payroll report export.')
    }
  }

  return (
    <DashboardLayout
      title="Payroll Report"
      description="Summary-first payroll reporting screen aligned với HR preparation và Finance approval/payment flows."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/reports">Reports dashboard</Link>
          </Button>
          <Button asChild size="sm" variant="ghost">
            <Link to="/finance/payroll-approvals">Finance approvals</Link>
          </Button>
        </div>
      }
    >
      <ReadonlyBanner message="Payroll report ưu tiên summary + run inspection. Employee-level money fields sẽ bị masked nếu principal thiếu finance.payroll.detail.read." />

      {!canExport ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Bạn có thể đọc payroll report nhưng không thể queue payroll export vì thiếu report.payroll.export.
        </div>
      ) : null}

      <FilterBar
        actions={
          <Button
            disabled={!regionId || !canExport}
            loading={createExport.isPending}
            onClick={() => void queuePayrollExport()}
            type="button"
          >
            Queue payroll export
          </Button>
        }
        description="Region/date range xác định payroll summary. Chọn thêm payroll run để review chi tiết allocations và employee results."
        title="Payroll filters"
      >
        {regionOptions.length > 0 ? (
          <Select
            label="Region"
            onChange={(event) => setRegionFilter(event.target.value)}
            options={regionOptions}
            placeholder="Select region"
            value={regionFilter}
          />
        ) : (
          <Input
            label="Region ID"
            onChange={(event) => setRegionFilter(event.target.value)}
            placeholder="Required"
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
        <Select
          label="Payroll run"
          onChange={(event) => setSelectedRunId(event.target.value)}
          options={runOptions}
          placeholder="Optional run detail"
          value={selectedRunId}
        />
      </FilterBar>

      {actionError ? <ErrorState message={actionError} title="Không thể queue payroll export" /> : null}

      <ReportSummaryCards items={buildPayrollSummaryCards(payrollReport.summaryQuery.data)} />

      {payrollReport.summaryQuery.error ? (
        <ErrorState
          actionLabel="Retry"
          message={getReportsErrorMessage(payrollReport.summaryQuery.error, 'Không thể tải payroll summary.')}
          onAction={() => void payrollReport.summaryQuery.refetch()}
          title="Không thể tải payroll summary"
        />
      ) : null}

      {lastExportJob ? (
        <AsyncJobProgress
          actionLabel={canPreviewExport(principal, lastExportJob) ? 'Open preview' : undefined}
          completedAt={lastExportJob.completedAt}
          description={getExportStatusDescription(lastExportJob)}
          onAction={
            canPreviewExport(principal, lastExportJob)
              ? () => {
                  window.location.href = `/reports/export-jobs/${lastExportJob.exportJobId}/preview`
                }
              : undefined
          }
          requestedAt={lastExportJob.requestedAt}
          status={lastExportJob.status}
          title={`Payroll export #${lastExportJob.exportJobId}`}
        />
      ) : null}

      {!canReadDetail ? (
        <PermissionDeniedInline message="Employee-level payroll amounts đang bị masked vì thiếu finance.payroll.detail.read." />
      ) : null}

      <section className="page-stack">
        <div className="page-header">
          <div>
            <h2>Payroll runs</h2>
            <p className="muted-text">Runs loaded from payroll run lifecycle endpoints for the selected region.</p>
          </div>
        </div>
        <DataTable
          columns={[
            { key: 'runCode', header: 'Run', render: (run) => run.runCode },
            { key: 'runDate', header: 'Run date', render: (run) => formatReportDateLabel(run.runDate) },
            { key: 'status', header: 'Status', render: (run) => <StatusBadge status={run.status} /> },
            { key: 'total', header: 'Total', render: (run) => formatReportCurrency(run.totalAmount) },
          ]}
          emptyDescription={regionId ? 'Không có payroll run nào cho region hiện tại.' : 'Chọn region để tải payroll runs.'}
          emptyTitle={regionId ? 'No payroll runs' : 'Region required'}
          error={
            payrollReport.runsQuery.error
              ? getReportsErrorMessage(payrollReport.runsQuery.error, 'Không thể tải payroll runs.')
              : null
          }
          loading={payrollReport.runsQuery.isLoading}
          loadingDescription="Đang tải payroll runs..."
          loadingTitle="Đang tải payroll runs"
          onRetry={() => void payrollReport.runsQuery.refetch()}
          rowClassName={(run) => (selectedRunId === String(run.id) ? 'table-row-attention' : undefined)}
          rowKey={(run) => run.id}
          rows={payrollReport.runsQuery.data ?? []}
        />
      </section>

      {selectedRunId ? (
        <>
          <div className="card-grid">
            <MaskedField
              helperText="Number of employee rows inside the selected payroll run report."
              label="Employees"
              mode="readonly-visible"
              value={payrollDetailSummary.employeeCount}
            />
            <MaskedField
              helperText="Allocation rows grouped by outlet."
              label="Allocations"
              mode="readonly-visible"
              value={payrollDetailSummary.allocationCount}
            />
            <MaskedField
              helperText={canReadDetail ? 'Visible because detail permission is granted.' : 'Masked because payroll detail permission is missing.'}
              label="Gross total"
              mode={canReadDetail ? 'readonly-visible' : 'masked'}
              value={formatReportCurrency(payrollDetailSummary.grossTotal)}
            />
            <MaskedField
              helperText={canReadDetail ? 'Visible because detail permission is granted.' : 'Masked because payroll detail permission is missing.'}
              label="Net total"
              mode={canReadDetail ? 'readonly-visible' : 'masked'}
              value={formatReportCurrency(payrollDetailSummary.netTotal)}
            />
          </div>

          {payrollReport.runDetailQuery.error ? (
            <ErrorState
              actionLabel="Retry"
              message={getReportsErrorMessage(payrollReport.runDetailQuery.error, 'Không thể tải payroll run report.')}
              onAction={() => void payrollReport.runDetailQuery.refetch()}
              title="Không thể tải payroll run detail"
            />
          ) : null}

          {!payrollReport.runDetailQuery.isLoading && !payrollReport.runDetailQuery.error && !payrollReport.runDetailQuery.data ? (
            <EmptyState
              description="Payroll run report này không tồn tại hoặc chưa sẵn sàng trong backend report-service."
              title="No payroll run detail"
            />
          ) : null}

          <section className="page-stack">
            <div className="page-header">
              <div>
                <h2>Employee results</h2>
                <p className="muted-text">Employee-level payroll rows for the selected payroll run.</p>
              </div>
            </div>
            <DataTable
              columns={employeeColumns}
              emptyDescription="Không có employee rows trong payroll run report hiện tại."
              emptyTitle="No employee payroll rows"
              error={null}
              loading={payrollReport.runDetailQuery.isLoading}
              loadingDescription="Đang tải employee payroll rows..."
              loadingTitle="Đang tải employee rows"
              rowKey={(row) => `${row.employeeId}-${row.businessDate}-${row.outletId ?? 'na'}`}
              rows={payrollReport.runDetailQuery.data?.employees ?? []}
            />
          </section>

          <section className="page-stack">
            <div className="page-header">
              <div>
                <h2>Allocation breakdown</h2>
                <p className="muted-text">Outlet-level allocation totals for the selected payroll run.</p>
              </div>
            </div>
            <DataTable
              columns={allocationColumns}
              emptyDescription="Không có allocation rows trong payroll run report hiện tại."
              emptyTitle="No payroll allocations"
              error={null}
              loading={payrollReport.runDetailQuery.isLoading}
              loadingDescription="Đang tải allocation rows..."
              loadingTitle="Đang tải allocations"
              rowKey={(row, index) => `${row.outletId ?? 'na'}-${index}`}
              rows={payrollReport.runDetailQuery.data?.allocations ?? []}
            />
          </section>
        </>
      ) : null}

      {lastExportJob && canOpenExportJob(principal, lastExportJob) ? (
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to={`/reports/export-jobs/${lastExportJob.exportJobId}`}>Job detail</Link>
          </Button>
          {canDownloadExport(principal, lastExportJob) ? (
            <Button asChild size="sm" variant="ghost">
              <Link to={`/reports/export-jobs/${lastExportJob.exportJobId}/download`}>Download export</Link>
            </Button>
          ) : null}
        </div>
      ) : null}
    </DashboardLayout>
  )
}
