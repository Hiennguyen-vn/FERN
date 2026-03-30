import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Card,
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
    }
  }, [runsQuery.data])

  const columns = useMemo<Array<DataTableColumn<FinancePayrollRun>>>(
    () => [
      {
        key: 'run',
        header: 'Run',
        render: (run) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
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

      <Card title="Queue summary">
        <div className="meta-grid">
          <span>Total runs: {summary.total}</span>
          <span>Submitted: {summary.submitted}</span>
          <span>Approved: {summary.approved}</span>
          <span>Paid: {summary.paid}</span>
        </div>
      </Card>

      <Card title="Queue filters">
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
      </Card>

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
