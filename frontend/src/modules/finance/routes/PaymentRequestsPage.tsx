import { useEffect, useMemo, useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useFinanceSuppliers, usePaymentRequest, useRecentPaymentRequests } from '../hooks/useFinance'
import type { FinancePaymentRequestLine, RecentFinancePaymentRequestLookup } from '../model/finance.types'
import { getFinanceErrorMessage } from '../services/financeError.service'
import { saveRecentFinancePaymentRequest } from '../services/recentPaymentRequests.service'
import {
  buildPaymentRequestLabel,
  buildSupplierLabel,
  filterRecentPaymentRequests,
  formatFinanceCurrency,
  formatFinanceDateLabel,
} from '../services/financeWorkflow.service'
import { supplierUiPolicy } from '../services/supplierUiPolicy.service'

export function PaymentRequestsPage() {
  usePageTitle('Payment Requests — Finance')
  const principal = usePrincipal()
  const canOpen = supplierUiPolicy.canOpenPaymentRequestsPage(principal)
  const canReadSuppliers = supplierUiPolicy.canOpenSuppliersPage(principal)
  const [lookupInput, setLookupInput] = useState('')
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<number | null>(null)
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [validationError, setValidationError] = useState<string | null>(null)
  const recentRequests = useRecentPaymentRequests()
  const suppliersQuery = useFinanceSuppliers({ enabled: canReadSuppliers })
  const paymentRequestQuery = usePaymentRequest(selectedInvoiceId ?? 0, {
    enabled: canOpen && Boolean(selectedInvoiceId),
  })

  useEffect(() => {
    if (!paymentRequestQuery.data) {
      return
    }

    const supplier = suppliersQuery.data?.find((item) => item.id === paymentRequestQuery.data?.supplierId)
    saveRecentFinancePaymentRequest(paymentRequestQuery.data, {
      supplierCode: supplier?.supplierCode,
      supplierName: supplier?.name,
    })
    recentRequests.refresh()
  }, [paymentRequestQuery.data, suppliersQuery.data])

  const supplierLookup = useMemo(
    () => new Map((suppliersQuery.data ?? []).map((supplier) => [supplier.id, supplier] as const)),
    [suppliersQuery.data],
  )

  const filteredRecentRequests = useMemo(
    () => filterRecentPaymentRequests(recentRequests.items, searchText, statusFilter),
    [recentRequests.items, searchText, statusFilter],
  )

  const statusOptions = useMemo<SelectOption[]>(() => {
    const statuses = Array.from(
      new Set(
        recentRequests.items
          .map((item) => item.paymentRequest.status)
          .concat(paymentRequestQuery.data?.status ? [paymentRequestQuery.data.status] : [])
          .filter(Boolean),
      ),
    )

    return [
      { label: 'Tất cả trạng thái', value: 'ALL' },
      ...statuses.map((status) => ({ label: status, value: status })),
    ]
  }, [paymentRequestQuery.data?.status, recentRequests.items])

  const recentColumns = useMemo<Array<DataTableColumn<RecentFinancePaymentRequestLookup>>>(
    () => [
      {
        key: 'invoice',
        header: 'Payment request',
        render: (row) => buildPaymentRequestLabel(row.paymentRequest),
      },
      {
        key: 'supplier',
        header: 'Supplier',
        render: (row) =>
          row.supplierCode && row.supplierName
            ? `${row.supplierCode} · ${row.supplierName}`
            : `#${row.paymentRequest.supplierId}`,
      },
      {
        key: 'dueDate',
        header: 'Due date',
        render: (row) => formatFinanceDateLabel(row.paymentRequest.dueDate),
      },
      {
        key: 'total',
        header: 'Total',
        render: (row) => formatFinanceCurrency(row.paymentRequest.totalAmount, row.paymentRequest.currencyCode),
      },
      {
        key: 'status',
        header: 'Status',
        render: (row) => <StatusBadge status={row.paymentRequest.status} />,
      },
    ],
    [],
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

  function submitLookup() {
    const parsed = Number(lookupInput)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập invoice ID hợp lệ để lookup payment request.')
      return
    }

    setValidationError(null)
    setSelectedInvoiceId(parsed)
  }

  if (!canOpen) {
    return (
      <DashboardLayout title="Payment Requests" description="Lookup-first supplier invoice review console cho Finance">
        <PermissionDeniedInline message="Bạn cần quyền procurement.invoice.read hoặc permission payables liên quan để mở payment requests." />
      </DashboardLayout>
    )
  }

  const selectedRequest = paymentRequestQuery.data
  const selectedSupplier = selectedRequest ? supplierLookup.get(selectedRequest.supplierId) : null

  return (
    <DashboardLayout
      title="Payment Requests"
      description="Lookup-first supplier invoice review console vì backend hiện chưa publish public invoice list endpoint."
    >
      <ReadonlyBanner message="Payment requests đang publish theo mô hình lookup-first + recent history. Trang này không giả lập invoice queue khi backend chưa có list endpoint công khai." />

      <Card title="Lookup payment request">
        <div className="field-grid">
          <Input
            label="Invoice ID"
            onChange={(event) => setLookupInput(event.target.value)}
            placeholder="VD: 10001"
            type="number"
            value={lookupInput}
          />
          <Input
            label="Tìm trong recent payment requests"
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Invoice number, supplier, status..."
            value={searchText}
          />
          <Select
            label="Status filter"
            onChange={(event) => setStatusFilter(event.target.value)}
            options={statusOptions}
            value={statusFilter}
          />
        </div>
        <div className="form-actions align-start">
          <Button onClick={submitLookup} size="sm">
            Lookup invoice
          </Button>
          {recentRequests.items.length > 0 ? (
            <Button onClick={recentRequests.clear} size="sm" variant="ghost">
              Clear recent
            </Button>
          ) : null}
        </div>
        {validationError ? <p className="error-text">{validationError}</p> : null}
      </Card>

      {selectedInvoiceId ? (
        paymentRequestQuery.isLoading ? (
          <Card title="Đang tải payment request">
            <p className="muted-text">Đang tải supplier invoice #{selectedInvoiceId}...</p>
          </Card>
        ) : paymentRequestQuery.error ? (
          <ErrorState
            actionLabel="Retry lookup"
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

      {recentRequests.items.length === 0 ? (
        <EmptyState
          description="Chưa có payment request nào được lookup gần đây. Dùng invoice ID để inspect một supplier invoice cụ thể."
          title="No recent payment requests"
        />
      ) : (
        <DataTable
          columns={recentColumns}
          emptyDescription="Recent payment requests không có bản ghi nào khớp bộ lọc hiện tại."
          emptyTitle="No matching recent payment requests"
          onRowClick={(row) => {
            setSelectedInvoiceId(row.paymentRequest.id)
            setLookupInput(String(row.paymentRequest.id))
            setValidationError(null)
          }}
          rowKey={(row) => row.paymentRequest.id}
          rows={filteredRecentRequests}
        />
      )}
    </DashboardLayout>
  )
}
