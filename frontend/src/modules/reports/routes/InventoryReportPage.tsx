import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  AsyncJobProgress,
  Button,
  DataTable,
  ErrorState,
  FilterBar,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ReportSummaryCards } from '../components/ReportSummaryCards'
import { useCreateExportJob } from '../hooks/useCreateExportJob'
import { useInventoryReport } from '../hooks/useInventoryReport'
import type { ReportInventoryTransaction, ReportStockBalance } from '../model/inventoryReport.types'
import { getReportsErrorMessage } from '../services/reportsError.service'
import {
  formatReportCurrency,
  formatReportDateLabel as formatDate,
} from '../services/reportsReadModel.service'
import {
  canExportReports,
  canPreviewExport,
  canReadReports,
  getExportStatusDescription,
} from '../services/reportsUiPolicy.service'

const txnTypeOptions: SelectOption[] = [
  { label: 'All transaction types', value: 'ALL' },
  { label: 'RECEIPT', value: 'RECEIPT' },
  { label: 'ISSUE', value: 'ISSUE' },
  { label: 'ADJUSTMENT', value: 'ADJUSTMENT' },
  { label: 'WASTE', value: 'WASTE' },
  { label: 'COUNT', value: 'COUNT' },
]

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function defaultFromIso() {
  const date = new Date()
  date.setDate(date.getDate() - 7)
  return date.toISOString().slice(0, 10)
}

export function InventoryReportPage() {
  usePageTitle('Inventory Report')
  const principal = usePrincipal()
  const { selectedOutletId } = useScopeContext()
  const canRead = canReadReports(principal)
  const canExport = canExportReports(principal)
  const createExport = useCreateExportJob()
  const [lastExportJob, setLastExportJob] = useState<Awaited<ReturnType<typeof createExport.mutateAsync>> | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [outletFilter, setOutletFilter] = useState(selectedOutletId ? String(selectedOutletId) : '')
  const [ingredientFilter, setIngredientFilter] = useState('')
  const [fromDate, setFromDate] = useState(defaultFromIso())
  const [toDate, setToDate] = useState(todayIso())
  const [txnType, setTxnType] = useState('ALL')
  const appliedOutletId = outletFilter.trim() ? Number(outletFilter) : undefined
  const appliedIngredientId = ingredientFilter.trim() ? Number(ingredientFilter) : undefined
  const inventoryReport = useInventoryReport(
    {
      from: fromDate || undefined,
      ingredientId: Number.isFinite(appliedIngredientId) ? appliedIngredientId : undefined,
      outletId: Number.isFinite(appliedOutletId) ? appliedOutletId : undefined,
      page: 0,
      size: 25,
      to: toDate || undefined,
      txnType: txnType === 'ALL' ? undefined : txnType,
    },
    { enabled: canRead && Boolean(appliedOutletId) },
  )

  const balanceColumns = useMemo<Array<DataTableColumn<ReportStockBalance>>>(
    () => [
      {
        key: 'ingredient',
        header: 'Ingredient',
        render: (row) => `#${row.ingredientId}`,
      },
      {
        key: 'onHand',
        header: 'On hand',
        render: (row) => Number(row.qtyOnHand).toLocaleString('vi-VN'),
      },
      {
        key: 'available',
        header: 'Available',
        render: (row) => Number(row.qtyAvailable).toLocaleString('vi-VN'),
      },
      {
        key: 'reserved',
        header: 'Reserved',
        render: (row) => Number(row.qtyReserved).toLocaleString('vi-VN'),
      },
      {
        key: 'unitCost',
        header: 'Unit cost',
        render: (row) => formatReportCurrency(row.unitCost),
      },
    ],
    [],
  )

  const transactionColumns = useMemo<Array<DataTableColumn<ReportInventoryTransaction>>>(
    () => [
      {
        key: 'businessDate',
        header: 'Business date',
        render: (row) => formatDate(row.businessDate),
      },
      {
        key: 'ingredient',
        header: 'Ingredient',
        render: (row) => `#${row.ingredientId}`,
      },
      {
        key: 'txnType',
        header: 'Type',
        render: (row) => <StatusBadge status={row.txnType} />,
      },
      {
        key: 'qtyChange',
        header: 'Qty change',
        render: (row) => Number(row.qtyChange).toLocaleString('vi-VN'),
      },
      {
        key: 'reference',
        header: 'Reference',
        render: (row) => row.sourceReferenceId ?? '—',
      },
    ],
    [],
  )

  if (!canRead) {
    return (
      <DashboardLayout title="Inventory Report" description="Operational inventory reporting workspace.">
        <PermissionDeniedInline message="Bạn cần report.read hoặc report.export để mở inventory report." />
      </DashboardLayout>
    )
  }

  const summaryCards = [
    {
      description: 'Stock balance rows returned for the selected outlet and ingredient filter.',
      label: 'Balance rows',
      value: inventoryReport.summary.balanceRows,
    },
    {
      description: 'Distinct ingredients currently present in the stock snapshot.',
      label: 'Ingredients',
      tone: 'info' as const,
      value: inventoryReport.summary.ingredients,
    },
    {
      description: 'Total available quantity across current stock rows.',
      label: 'Available qty',
      tone: 'success' as const,
      value: inventoryReport.summary.availableQuantity.toLocaleString('vi-VN'),
    },
    {
      description: 'Net quantity delta across visible transaction rows.',
      label: 'Txn delta',
      tone: 'warning' as const,
      value: inventoryReport.summary.txnQuantityDelta.toLocaleString('vi-VN'),
    },
  ]

  async function queueInventoryExport() {
    if (!appliedOutletId) {
      return
    }

    setActionError(null)

    try {
      const job = await createExport.mutateAsync({
        dataset: 'INVENTORY_MOVEMENT_FACT',
        format: 'CSV',
        fromDate: fromDate || undefined,
        limit: 50,
        outletId: appliedOutletId,
        toDate: toDate || undefined,
      })
      setLastExportJob(job)
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to queue inventory export.')
    }
  }

  return (
    <DashboardLayout
      title="Inventory Report"
      description="Operational stock snapshot + movement breakdown using current inventory read-side endpoints."
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
      <ReadonlyBanner message="Inventory report dùng read-side operational APIs hiện có: stock balances cho snapshot và inventory transactions cho movement breakdown. Chọn outlet rõ ràng để giữ report ổn định." />

      {!selectedOutletId ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Chưa chọn outlet ở app shell. Inventory report cần outlet rõ ràng để query stock balances và inventory transactions.
        </div>
      ) : null}

      <FilterBar
        actions={
          <div className="form-actions align-start">
            <Button
              disabled={!appliedOutletId || !canExport}
              loading={createExport.isPending}
              onClick={() => void queueInventoryExport()}
              type="button"
            >
              Queue export
            </Button>
          </div>
        }
        description="Bộ lọc này điều khiển cả stock snapshot, transaction breakdown, và inventory movement export."
        title="Inventory filters"
      >
        <Input
          label="Outlet ID"
          onChange={(event) => setOutletFilter(event.target.value)}
          placeholder="Required"
          type="number"
          value={outletFilter}
        />
        <Input
          label="Ingredient ID"
          onChange={(event) => setIngredientFilter(event.target.value)}
          placeholder="Optional"
          type="number"
          value={ingredientFilter}
        />
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
          label="Transaction type"
          onChange={(event) => setTxnType(event.target.value)}
          options={txnTypeOptions}
          value={txnType}
        />
      </FilterBar>

      {actionError ? <ErrorState message={actionError} title="Không thể queue inventory export" /> : null}

      <ReportSummaryCards items={summaryCards} />

      {lastExportJob ? (
        <AsyncJobProgress
          actionLabel={canPreviewExport(lastExportJob) ? 'Open preview' : undefined}
          completedAt={lastExportJob.completedAt}
          description={getExportStatusDescription(lastExportJob)}
          onAction={
            canPreviewExport(lastExportJob)
              ? () => {
                  window.location.href = `/reports/export-jobs/${lastExportJob.exportJobId}/preview`
                }
              : undefined
          }
          requestedAt={lastExportJob.requestedAt}
          status={lastExportJob.status}
          title={`Inventory export #${lastExportJob.exportJobId}`}
        />
      ) : null}

      <section className="page-stack">
        <div className="page-header">
          <div>
            <h2>Stock snapshot</h2>
            <p className="muted-text">Current stock balances for the selected outlet and ingredient filter.</p>
          </div>
        </div>
        <DataTable
          columns={balanceColumns}
          emptyDescription={
            appliedOutletId
              ? 'Không có stock balance rows nào khớp bộ lọc hiện tại.'
              : 'Nhập outlet ID hợp lệ để tải stock snapshot.'
          }
          emptyTitle={appliedOutletId ? 'No stock balance rows' : 'Outlet required'}
          error={
            inventoryReport.balanceQuery.error
              ? getReportsErrorMessage(inventoryReport.balanceQuery.error, 'Không thể tải stock snapshot.')
              : null
          }
          loading={inventoryReport.balanceQuery.isLoading}
          loadingDescription="Đang tải stock balances..."
          loadingTitle="Đang tải stock snapshot"
          onRetry={() => void inventoryReport.balanceQuery.refetch()}
          rowKey={(row) => `${row.outletId}-${row.ingredientId}`}
          rows={inventoryReport.balanceQuery.data?.items ?? []}
        />
      </section>

      <section className="page-stack">
        <div className="page-header">
          <div>
            <h2>Movement breakdown</h2>
            <p className="muted-text">Recent inventory transactions filtered by current outlet/date/type selection.</p>
          </div>
        </div>
        <DataTable
          columns={transactionColumns}
          emptyDescription={
            appliedOutletId
              ? 'Không có inventory transaction nào khớp bộ lọc hiện tại.'
              : 'Nhập outlet ID hợp lệ để tải movement breakdown.'
          }
          emptyTitle={appliedOutletId ? 'No inventory transactions' : 'Outlet required'}
          error={
            inventoryReport.transactionQuery.error
              ? getReportsErrorMessage(
                  inventoryReport.transactionQuery.error,
                  'Không thể tải inventory movements.',
                )
              : null
          }
          loading={inventoryReport.transactionQuery.isLoading}
          loadingDescription="Đang tải inventory transactions..."
          loadingTitle="Đang tải movement breakdown"
          onRetry={() => void inventoryReport.transactionQuery.refetch()}
          rowKey={(row) => row.id}
          rows={inventoryReport.transactionQuery.data?.items ?? []}
        />
      </section>
    </DashboardLayout>
  )
}
