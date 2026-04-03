import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  DataTable,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
  Input,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePayrollApprovalQueue } from '../hooks/useFinance'
import type { FinancePayrollRun } from '../model/finance.types'
import { getFinanceErrorMessage } from '../services/financeError.service'
import {
  formatFinanceCurrency,
  formatFinanceDateLabel,
  matchesFinanceSearch,
} from '../services/financeWorkflow.service'
import { payrollApprovalUiPolicy } from '../services/payrollApprovalUiPolicy.service'

export function PayrollApprovalPage() {
  usePageTitle('Payroll Approvals — Finance')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canOpen = payrollApprovalUiPolicy.canOpenPayrollQueue(principal)
  const [regionFilter, setRegionFilter] = useState(
    selectedRegionId ? String(selectedRegionId) : regionIds.length === 1 ? String(regionIds[0]) : '',
  )
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState('SUBMITTED')

  const activeRegionId =
    regionFilter.trim() && Number.isFinite(Number(regionFilter)) ? Number(regionFilter) : undefined
  const canQuery = Boolean(activeRegionId) || Boolean(principal?.scopeRoots.system)
  const runsQuery = usePayrollApprovalQueue(
    { regionId: activeRegionId },
    { enabled: canOpen && canQuery },
  )

  const statusOptions: SelectOption[] = [
    { label: 'Actionable (SUBMITTED)', value: 'SUBMITTED' },
    { label: 'All statuses', value: 'ALL' },
    { label: 'DRAFT', value: 'DRAFT' },
    { label: 'REJECTED', value: 'REJECTED' },
    { label: 'APPROVED', value: 'APPROVED' },
    { label: 'PAID', value: 'PAID' },
    { label: 'CANCELLED', value: 'CANCELLED' },
  ]

  const regionOptions = useMemo<SelectOption[]>(
    () => regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    [regionIds],
  )

  const filteredRuns = useMemo(() => {
    return (runsQuery.data ?? []).filter((run) => {
      const matchesStatus = statusFilter === 'ALL' || run.status.toUpperCase() === statusFilter.toUpperCase()
      const matchesSearchQuery = matchesFinanceSearch(
        [run.id, run.runCode, run.payrollPeriodId, run.note, run.status],
        searchText,
      )
      return matchesStatus && matchesSearchQuery
    })
  }, [runsQuery.data, searchText, statusFilter])

  const summary = useMemo(() => {
    const runs = runsQuery.data ?? []
    return {
      approved: runs.filter((run) => run.status.toUpperCase() === 'APPROVED').length,
      paid: runs.filter((run) => run.status.toUpperCase() === 'PAID').length,
      submitted: runs.filter((run) => run.status.toUpperCase() === 'SUBMITTED').length,
      total: runs.length,
      totalAmount: runs.reduce((sum, run) => sum + (run.totalAmount ?? 0), 0),
    }
  }, [runsQuery.data])
  const approvalFocus = useMemo(() => {
    return [...filteredRuns]
      .sort((left, right) => (right.totalAmount ?? 0) - (left.totalAmount ?? 0))
      .slice(0, 4)
  }, [filteredRuns])

  const columns = useMemo<Array<DataTableColumn<FinancePayrollRun>>>(
    () => [
      {
        key: 'run',
        header: 'Run',
        render: (run) => (
          <div className="compact-stack">
            <strong>{run.runCode}</strong>
            <span className="muted-text">Period #{run.payrollPeriodId}</span>
          </div>
        ),
      },
      {
        key: 'runDate',
        header: 'Run date',
        render: (run) => formatFinanceDateLabel(run.runDate),
      },
      {
        key: 'submittedAt',
        header: 'Submitted at',
        render: (run) => formatFinanceDateLabel(run.submittedAt),
      },
      {
        key: 'total',
        header: 'Total',
        render: (run) => formatFinanceCurrency(run.totalAmount),
      },
      {
        key: 'status',
        header: 'Status',
        render: (run) => <StatusBadge status={run.status} />,
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Payroll Approvals" description="Approval queue cho payroll runs đang chờ Finance review">
        <PermissionDeniedInline message="Bạn cần finance.payroll.read, finance.payroll.approve hoặc finance.payroll.pay để mở finance payroll workspace." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Payroll Approvals"
      description="Approval-aware finance queue với mặc định tập trung vào các payroll runs đang chờ phê duyệt."
    >
      {!canQuery ? (
        <ReadonlyBanner message="Chọn region trong app shell trước khi load payroll approval queue. Payroll APIs yêu cầu scope region rõ ràng trừ khi principal có system scope." />
      ) : (
        <ReadonlyBanner message="Queue mặc định tập trung vào trạng thái SUBMITTED. Chuyển bộ lọc nếu cần inspect APPROVED, PAID hoặc historical runs." />
      )}

      <section className="surface-panel command-stage" aria-label="Payroll approval command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Finance review</span>
            <span className="meta-chip">
              Scope {activeRegionId ? `Region #${activeRegionId}` : principal?.scopeRoots.system ? 'System' : 'Region required'}
            </span>
            <span className={statusFilter === 'SUBMITTED' ? 'meta-chip-success' : 'meta-chip'}>
              Status {statusFilter}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Finance / Payroll Approvals</p>
            <strong className="action-summary-title">Current payroll review slice</strong>
            <p className="muted-text">
              Review submitted payroll runs, sort by value, and open the decision workspace only when
              the current region and lifecycle state are correct.
            </p>
          </div>
          <div className="meta-grid">
            <span>Total runs loaded: {summary.total}</span>
            <span>Visible after filter: {filteredRuns.length}</span>
            <span>Submitted: {summary.submitted}</span>
            <span>Approved or paid: {summary.approved + summary.paid}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Approval focus</span>
            <strong>
              {approvalFocus.length > 0 ? `${approvalFocus.length} highest-value runs in view` : 'No runs in the current slice'}
            </strong>
            <p>
              Keep the largest payroll runs visible here so approval effort starts with the most
              material records.
            </p>
          </div>
          <div className="command-support-list">
            {approvalFocus.length > 0 ? (
              approvalFocus.map((run) => (
                <article className="command-support-item" key={run.id}>
                  <div className="command-support-copy">
                    <strong>{run.runCode}</strong>
                    <span className="muted-text">Period #{run.payrollPeriodId}</span>
                    <span className="muted-text">Run date {formatFinanceDateLabel(run.runDate)}</span>
                  </div>
                  <div className="command-support-stack">
                    <span className="command-support-metric">
                      {formatFinanceCurrency(run.totalAmount)}
                    </span>
                    <StatusBadge status={run.status} />
                  </div>
                </article>
              ))
            ) : (
              <div className="command-empty-note">
                No payroll runs match the current scope and status filter. Adjust region or lifecycle
                status to populate the review rail.
              </div>
            )}
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Payroll approval summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="receipt_long" />
            </span>
            <span className="workspace-stat-badge success">Queue</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Runs loaded</span>
            <strong className="workspace-stat-value">{summary.total}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="pending_actions" />
            </span>
            <span className="workspace-stat-badge warning">Action</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Submitted runs</span>
            <strong className="workspace-stat-value">{summary.submitted}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge">Value</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Loaded payroll value</span>
            <strong className="workspace-stat-value">{formatFinanceCurrency(summary.totalAmount)}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="task_alt" />
            </span>
            <span className="workspace-stat-badge">Settled</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Approved + paid</span>
            <strong className="workspace-stat-value">{summary.approved + summary.paid}</strong>
          </div>
        </article>
      </section>

      <section className="surface-panel command-filter-panel">
        <div className="state-panel-heading">
          <span className="eyebrow">Queue filters</span>
          <h2 className="card-title">Review scope</h2>
          <p className="muted-text">
            Narrow the payroll queue by region, free-text search, and lifecycle state before opening
            a run detail.
          </p>
        </div>
        <div className="field-grid">
          {regionOptions.length > 0 ? (
            <Select
              label="Region"
              onChange={(event) => setRegionFilter(event.target.value)}
              options={regionOptions}
              placeholder={principal?.scopeRoots.system ? 'All regions (system scope)' : 'Select region'}
              value={regionFilter}
            />
          ) : (
            <Input
              label="Region ID"
              onChange={(event) => setRegionFilter(event.target.value)}
              placeholder={principal?.scopeRoots.system ? 'Optional region filter' : 'Region required'}
              type="number"
              value={regionFilter}
            />
          )}
          <Input
            label="Tìm run code, period hoặc note"
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="VD: RUN-202603"
            value={searchText}
          />
          <Select
            label="Status"
            onChange={(event) => setStatusFilter(event.target.value)}
            options={statusOptions}
            value={statusFilter}
          />
        </div>
      </section>

      <DataTable
        columns={columns}
        emptyDescription={
          canQuery
            ? 'Không có payroll run nào khớp bộ lọc hiện tại.'
            : 'Chọn region hợp lệ trước khi xem payroll approval queue.'
        }
        emptyTitle={canQuery ? 'No matching payroll runs' : 'Region required'}
        error={runsQuery.error ? getFinanceErrorMessage(runsQuery.error, 'Không thể tải payroll approval queue.') : null}
        loading={runsQuery.isLoading}
        loadingDescription="Đang tải payroll approval queue..."
        loadingTitle="Đang tải payroll approvals"
        onRetry={() => void runsQuery.refetch()}
        onRowClick={(run) => navigate(`/finance/payroll-approvals/${run.id}`)}
        rowClassName={(run) => (run.status.toUpperCase() === 'SUBMITTED' ? 'table-row-attention' : undefined)}
        rowKey={(run) => run.id}
        rows={filteredRuns}
      />
    </DashboardLayout>
  )
}
