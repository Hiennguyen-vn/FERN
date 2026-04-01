import { useMemo, useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  DataTable,
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
      <div className="field-grid">
        <div>
          <Input
            label="Search payment requests"
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Invoice number, supplier, status..."
            value={searchText}
          />
          <p className="muted-text" style={{ fontSize: 'var(--text-xs)', marginTop: '0.25rem' }}>
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
          <p className="muted-text" style={{ fontSize: 'var(--text-xs)', marginTop: '0.25rem' }}>
            Status filter được gửi lên backend — chỉ trả về invoices khớp status.
          </p>
        </div>
      </div>

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

      {selectedInvoiceId ? (
        paymentRequestQuery.isLoading ? null : paymentRequestQuery.error ? (
          <ErrorState
            actionLabel="Retry detail"
            message={getFinanceErrorMessage(paymentRequestQuery.error, `Không thể tải supplier invoice #${selectedInvoiceId}.`)}
            onAction={() => void paymentRequestQuery.refetch()}
            title="Unable to load payment request"
          />
        ) : selectedRequest ? (
          <>
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

            <DataTable
              columns={lineColumns}
              emptyDescription="Supplier invoice này chưa có line detail nào."
              emptyTitle="No invoice lines"
              rowKey={(line) => line.id}
              rows={selectedRequest.lines}
            />
          </>
        ) : null
      ) : null}
    </DashboardLayout>
  )
}
