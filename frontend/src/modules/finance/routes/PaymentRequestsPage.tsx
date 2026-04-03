import { useMemo, useState } from 'react'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  DataTable,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  Input,
  PermissionDeniedInline,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useFinanceSuppliers, usePaymentRequest, usePaymentRequests } from '../hooks/useFinance'
import type { FinancePaymentRequest, FinancePaymentRequestLine } from '../model/finance.types'
import { getFinanceErrorMessage } from '../services/financeError.service'
import {
  buildPaymentRequestLabel,
  buildSupplierLabel,
  formatFinanceCurrency,
  formatFinanceDateLabel,
} from '../services/financeWorkflow.service'
import { supplierUiPolicy } from '../services/supplierUiPolicy.service'

export function PaymentRequestsPage() {
  usePageTitle('Payment Requests — Finance')
  const principal = usePrincipal()
  const canOpen = supplierUiPolicy.canOpenPaymentRequestsPage(principal)
  const canReadSuppliers = supplierUiPolicy.canOpenSuppliersPage(principal)
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<number | null>(null)
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const suppliersQuery = useFinanceSuppliers({ enabled: canReadSuppliers })
  const paymentRequestsQuery = usePaymentRequests(
    {
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      // Backend GET /supplier-invoices is a flat list (no hasMore), default limit is 200.
      // Client-side search below operates on this batch.
      limit: 200,
    },
    { enabled: canOpen },
  )
  const paymentRequestQuery = usePaymentRequest(selectedInvoiceId ?? 0, {
    enabled: canOpen && Boolean(selectedInvoiceId),
  })

  const supplierLookup = useMemo(
    () => new Map((suppliersQuery.data ?? []).map((supplier) => [supplier.id, supplier] as const)),
    [suppliersQuery.data],
  )

  const visibleRequests = useMemo(
    () =>
      (paymentRequestsQuery.data ?? []).filter((item) => {
        const normalized = searchText.trim().toLowerCase()
        if (!normalized) {
          return true
        }
        const supplier = supplierLookup.get(item.supplierId)
        return [item.id, item.invoiceNumber, item.status, supplier?.supplierCode, supplier?.name]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(normalized))
      }),
    [paymentRequestsQuery.data, searchText, supplierLookup],
  )

  const statusOptions = useMemo<SelectOption[]>(() => {
    const statuses = Array.from(
      new Set(
        (paymentRequestsQuery.data ?? [])
          .map((item) => item.status)
          .concat(paymentRequestQuery.data?.status ? [paymentRequestQuery.data.status] : [])
          .filter(Boolean),
      ),
    )

    return [
      { label: 'Tất cả trạng thái', value: 'ALL' },
      ...statuses.map((status) => ({ label: status, value: status })),
    ]
  }, [paymentRequestQuery.data?.status, paymentRequestsQuery.data])

  const requestColumns = useMemo<Array<DataTableColumn<FinancePaymentRequest>>>(
    () => [
      {
        key: 'invoice',
        header: 'Payment request',
        render: (row) => buildPaymentRequestLabel(row),
      },
      {
        key: 'supplier',
        header: 'Supplier',
        render: (row) => buildSupplierLabel(row.supplierId, supplierLookup.get(row.supplierId)),
      },
      {
        key: 'dueDate',
        header: 'Due date',
        render: (row) => formatFinanceDateLabel(row.dueDate),
      },
      {
        key: 'total',
        header: 'Total',
        render: (row) => formatFinanceCurrency(row.totalAmount, row.currencyCode),
      },
      {
        key: 'status',
        header: 'Status',
        render: (row) => <StatusBadge status={row.status} />,
      },
    ],
    [supplierLookup],
  )

  const lineColumns = useMemo<Array<DataTableColumn<FinancePaymentRequestLine>>>(
    () => [
      { key: 'lineNumber', header: 'Line', render: (line) => line.lineNumber },
      { key: 'description', header: 'Description', render: (line) => line.description ?? 'No description' },
      { key: 'goodsReceiptLineId', header: 'GR line', render: (line) => (line.goodsReceiptLineId ? `#${line.goodsReceiptLineId}` : '—') },
      { key: 'qty', header: 'Qty invoiced', render: (line) => (line.qtyInvoiced ?? '—') },
      { key: 'lineTotal', header: 'Line total', render: (line) => formatFinanceCurrency(line.lineTotal) },
    ],
    [],
  )
  const queueSummary = useMemo(() => {
    const requests = paymentRequestsQuery.data ?? []
    const actionableStatuses = new Set(['SUBMITTED', 'PENDING', 'RECEIVED', 'MATCHED', 'DISPUTED'])
    const actionable = requests.filter((item) => actionableStatuses.has(item.status.toUpperCase()))
    const approved = requests.filter((item) => item.status.toUpperCase() === 'APPROVED')
    const loadedValue = requests.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0)
    const approvedValue = approved.reduce((sum, item) => sum + (item.totalAmount ?? 0), 0)
    const supplierCount = new Set(requests.map((item) => item.supplierId)).size
    const dueSoon = [...requests]
      .filter((item) => Boolean(item.dueDate))
      .sort((left, right) => Date.parse(left.dueDate ?? '') - Date.parse(right.dueDate ?? ''))
      .slice(0, 4)

    return {
      actionableCount: actionable.length,
      approvedValue,
      dueSoon,
      loadedValue,
      supplierCount,
    }
  }, [paymentRequestsQuery.data])

  if (!canOpen) {
    return (
      <DashboardLayout title="Payment Requests" description="Supplier invoice review console cho Finance">
        <PermissionDeniedInline message="Bạn cần quyền procurement.invoice.read hoặc permission payables liên quan để mở payment requests." />
      </DashboardLayout>
    )
  }

  const selectedRequest = paymentRequestQuery.data
  const selectedSupplier = selectedRequest ? supplierLookup.get(selectedRequest.supplierId) : null

  return (
    <DashboardLayout
      title="Payment Requests"
      description="Supplier invoice review console backed by the real invoice queue."
    >
      <section className="surface-panel command-stage" aria-label="Payment request command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Finance queue</span>
            <span className="meta-chip">Loaded batch {paymentRequestsQuery.data?.length ?? 0}</span>
            <span className={selectedRequest ? 'meta-chip-success' : 'meta-chip'}>
              {selectedRequest ? `Detail open #${selectedRequest.id}` : 'Detail rail idle'}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Finance / Payment Requests</p>
            <strong className="action-summary-title">Current payables slice</strong>
            <p className="muted-text">
              Queue-first invoice review for supplier payables, due timing, and approval posture
              without leaving the finance workspace.
            </p>
          </div>
          <div className="meta-grid">
            <span>Visible after filter: {visibleRequests.length}</span>
            <span>Actionable requests: {queueSummary.actionableCount}</span>
            <span>Suppliers represented: {queueSummary.supplierCount}</span>
            <span>Status filter: {statusFilter}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Due next</span>
            <strong>Supplier watchlist</strong>
            <p>
              Keep the earliest due invoices in view so the queue can be worked by urgency before
              opening full detail.
            </p>
          </div>
          <div className="command-support-list">
            {queueSummary.dueSoon.length > 0 ? (
              queueSummary.dueSoon.map((item) => (
                <article className="command-support-item" key={item.id}>
                  <div className="command-support-copy">
                    <strong>{buildPaymentRequestLabel(item)}</strong>
                    <span className="muted-text">
                      {buildSupplierLabel(item.supplierId, supplierLookup.get(item.supplierId))}
                    </span>
                    <span className="muted-text">Due {formatFinanceDateLabel(item.dueDate)}</span>
                  </div>
                  <div className="command-support-stack">
                    <span className="command-support-metric">
                      {formatFinanceCurrency(item.totalAmount, item.currencyCode)}
                    </span>
                    <StatusBadge status={item.status} />
                  </div>
                </article>
              ))
            ) : (
              <div className="command-empty-note">
                No dated invoices are loaded yet. Select another status or load a broader queue to
                populate the watchlist.
              </div>
            )}
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Payment request summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge success">Loaded</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Queue value in batch</span>
            <strong className="workspace-stat-value">
              {formatFinanceCurrency(queueSummary.loadedValue)}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="priority_high" />
            </span>
            <span className="workspace-stat-badge warning">Action</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Requests requiring action</span>
            <strong className="workspace-stat-value">{queueSummary.actionableCount}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="task_alt" />
            </span>
            <span className="workspace-stat-badge">Approved</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Approved value</span>
            <strong className="workspace-stat-value">
              {formatFinanceCurrency(queueSummary.approvedValue)}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="factory" />
            </span>
            <span className="workspace-stat-badge">Supplier base</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Suppliers represented</span>
            <strong className="workspace-stat-value">{queueSummary.supplierCount}</strong>
          </div>
        </article>
      </section>

      <section className="surface-panel command-filter-panel">
        <div className="state-panel-heading">
          <span className="eyebrow">Queue filters</span>
          <h2 className="card-title">Review scope</h2>
          <p className="muted-text">
            Search within the loaded batch and narrow server-side by invoice lifecycle state.
          </p>
        </div>
        <div className="field-grid">
          <div>
            <Input
              label="Search payment requests"
              onChange={(event) => setSearchText(event.target.value)}
              placeholder="Invoice number, supplier, status..."
              value={searchText}
            />
            <p className="muted-text field-hint">
              Lọc trên tập dữ liệu đã tải (tối đa 200 invoice). Backend không hỗ trợ server-side search.
            </p>
          </div>
          <div>
            <Select
              label="Status filter"
              onChange={(event) => setStatusFilter(event.target.value)}
              options={statusOptions}
              value={statusFilter}
            />
            <p className="muted-text field-hint">
              Status filter được gửi lên backend — chỉ trả về invoices khớp status.
            </p>
          </div>
        </div>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main command-table-stack">
          <DataTable
            columns={requestColumns}
            emptyDescription="Không có payment request nào khớp bộ lọc hiện tại."
            emptyTitle="No matching payment requests"
            error={paymentRequestsQuery.error ? getFinanceErrorMessage(paymentRequestsQuery.error, 'Không thể tải payment request queue.') : null}
            loading={paymentRequestsQuery.isLoading}
            loadingDescription="Đang tải payment requests..."
            loadingTitle="Đang tải payment requests"
            onRetry={() => void paymentRequestsQuery.refetch()}
            onRowClick={(row) => setSelectedInvoiceId(row.id)}
            rowKey={(row) => row.id}
            rows={visibleRequests}
          />

          {selectedRequest ? (
            <section className="surface-panel command-table-stack">
              <div className="state-panel-heading">
                <span className="eyebrow">Invoice lines</span>
                <h2 className="card-title">Line detail</h2>
                <p className="muted-text">
                  Match and payable line detail for the currently selected supplier invoice.
                </p>
              </div>
              <DataTable
                columns={lineColumns}
                emptyDescription="Supplier invoice này chưa có line detail nào."
                emptyTitle="No invoice lines"
                rowKey={(line) => line.id}
                rows={selectedRequest.lines}
              />
            </section>
          ) : null}
        </div>

        <aside className="surface-grid-side">
          {selectedInvoiceId ? (
            paymentRequestQuery.isLoading ? null : paymentRequestQuery.error ? (
              <ErrorState
                actionLabel="Retry detail"
                message={getFinanceErrorMessage(paymentRequestQuery.error, `Không thể tải supplier invoice #${selectedInvoiceId}.`)}
                onAction={() => void paymentRequestQuery.refetch()}
                title="Unable to load payment request"
              />
            ) : selectedRequest ? (
              <section className="surface-panel command-table-stack">
                <EntityHeader
                  eyebrow="Finance / Payment Request"
                  metadata={
                    <>
                      <span>Invoice ID: #{selectedRequest.id}</span>
                      <span>Supplier: {buildSupplierLabel(selectedRequest.supplierId, selectedSupplier)}</span>
                      <span>Due: {formatFinanceDateLabel(selectedRequest.dueDate)}</span>
                    </>
                  }
                  status={<StatusBadge status={selectedRequest.status} />}
                  title={buildPaymentRequestLabel(selectedRequest)}
                />

                <FormSection description="Lifecycle và payable context của supplier invoice được tra cứu." title="Request summary">
                  <div className="meta-grid">
                    <span>Invoice date: {formatFinanceDateLabel(selectedRequest.invoiceDate)}</span>
                    <span>Approved at: {formatFinanceDateLabel(selectedRequest.approvedAt)}</span>
                    <span>Region: {selectedRequest.regionId ? `#${selectedRequest.regionId}` : 'No region'}</span>
                    <span>Outlet: {selectedRequest.outletId ? `#${selectedRequest.outletId}` : 'No outlet'}</span>
                    <span>Total: {formatFinanceCurrency(selectedRequest.totalAmount, selectedRequest.currencyCode)}</span>
                    <span>Tax: {formatFinanceCurrency(selectedRequest.taxAmount, selectedRequest.currencyCode)}</span>
                  </div>
                  {selectedRequest.note ? <p className="muted-text">Note: {selectedRequest.note}</p> : null}
                </FormSection>
              </section>
            ) : null
          ) : (
            <section className="surface-panel state-panel">
              <div className="state-panel-heading">
                <span className="eyebrow">Detail rail</span>
                <h2 className="card-title">Select a request</h2>
                <p className="muted-text">
                  Open a payment request from the queue to inspect supplier context, due timing, and
                  invoice lines.
                </p>
              </div>
              <EmptyState
                description="No payment request is selected. Pick a row from the queue to load payable detail."
                title="Detail rail idle"
              />
            </section>
          )}
        </aside>
      </div>
    </DashboardLayout>
  )
}
