import { useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  EntityHeader,
  ErrorState,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useConfirmAction } from '@shared/hooks/useConfirmAction'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useSupplierInvoice, useSupplierInvoiceAction } from '../hooks/useSupplierInvoice'
import type { SupplierInvoiceLine } from '../model/procurement.types'
import {
  canApproveInvoice,
  canDisputeInvoice,
  canReadInvoices,
} from '../services/procurementPermission.service'
import {
  canApproveInvoice as canApproveInvoiceStatus,
  canDisputeInvoice as canDisputeInvoiceStatus,
} from '../services/procurementWorkflow.service'

const lineColumns: Array<DataTableColumn<SupplierInvoiceLine>> = [
  { key: 'lineNumber', header: '#', render: (row) => row.lineNumber },
  { key: 'lineType', header: 'Type', render: (row) => <StatusBadge status={row.lineType} /> },
  { key: 'grLineId', header: 'GR Line', render: (row) => row.goodsReceiptLineId ?? '—' },
  { key: 'description', header: 'Description', render: (row) => row.description ?? '—' },
  { key: 'qtyInvoiced', header: 'Qty', render: (row) => row.qtyInvoiced ?? '—' },
  { key: 'unitPrice', header: 'Unit price', render: (row) => row.unitPrice ?? '—' },
  { key: 'taxAmount', header: 'Tax', render: (row) => row.taxAmount ?? '—' },
  { key: 'lineTotal', header: 'Line total', render: (row) => row.lineTotal },
]

export function SupplierInvoiceDetailPage() {
  const principal = usePrincipal()
  const params = useParams<{ invoiceId: string }>()
  const invoiceId = params.invoiceId ? Number(params.invoiceId) : null
  const confirmAction = useConfirmAction()
  const query = useSupplierInvoice(invoiceId)
  const actionMutation = useSupplierInvoiceAction()

  usePageTitle(invoiceId ? `Supplier Invoice #${invoiceId}` : 'Supplier Invoice Detail')

  if (!canReadInvoices(principal)) {
    return (
      <DashboardLayout description="Xem và phê duyệt supplier invoice." title="Supplier Invoice Detail">
        <PermissionDeniedInline message="Bạn cần quyền procurement.invoice.read để xem supplier invoice." />
      </DashboardLayout>
    )
  }

  const invoice = query.data
  const isTerminal = invoice ? ['APPROVED', 'DISPUTED', 'CANCELLED'].includes(invoice.status.toUpperCase()) : false

  return (
    <DashboardLayout
      description="Xem chi tiết và thực hiện workflow phê duyệt supplier invoice."
      title="Supplier Invoice Detail"
    >
      {query.isLoading ? (
        <Card title="Loading invoice">
          <p className="muted-text">Đang tải supplier invoice...</p>
        </Card>
      ) : null}
      {query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load invoice'}
          onAction={() => void query.refetch()}
          title="Không thể tải supplier invoice"
        />
      ) : null}
      {!query.isLoading && !query.error && !invoice ? (
        <EmptyState
          description="Supplier invoice này không tồn tại."
          title="Invoice not found"
        />
      ) : null}
      {invoice ? (
        <>
          <EntityHeader
            actions={
              <>
                {canApproveInvoice(principal) && canApproveInvoiceStatus(invoice.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => {
                      if (confirmAction('Approve this supplier invoice?')) {
                        void actionMutation.mutateAsync({ id: invoice.id, action: 'approve' })
                      }
                    }}
                    size="sm"
                  >
                    Approve
                  </Button>
                ) : null}
                {canDisputeInvoice(principal) && canDisputeInvoiceStatus(invoice.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => {
                      if (confirmAction('Mark this invoice as disputed?')) {
                        void actionMutation.mutateAsync({ id: invoice.id, action: 'dispute' })
                      }
                    }}
                    size="sm"
                    variant="danger"
                  >
                    Dispute
                  </Button>
                ) : null}
              </>
            }
            eyebrow="Supplier Invoice"
            metadata={
              <>
                <span>Supplier ID: #{invoice.supplierId}</span>
                <span>Invoice date: {invoice.invoiceDate}</span>
                {invoice.dueDate ? <span>Due: {invoice.dueDate}</span> : null}
                <span>Currency: {invoice.currencyCode}</span>
                <span>Subtotal: {invoice.subtotal}</span>
                <span>Tax: {invoice.taxAmount}</span>
                <span>Total: {invoice.totalAmount}</span>
                {invoice.approvedAt ? <span>Approved: {new Date(invoice.approvedAt).toLocaleString()}</span> : null}
              </>
            }
            status={<StatusBadge status={invoice.status} />}
            title={invoice.invoiceNumber}
          />
          {isTerminal ? (
            <ReadonlyBanner message="Invoice này đang ở trạng thái terminal và chỉ có thể xem." />
          ) : null}
          {invoice.note ? (
            <Card title="Notes">
              <p className="muted-text">{invoice.note}</p>
            </Card>
          ) : null}
          {actionMutation.error ? (
            <ErrorState
              message={
                actionMutation.error instanceof Error
                  ? actionMutation.error.message
                  : 'Failed to update invoice'
              }
              title="Không thể cập nhật supplier invoice"
            />
          ) : null}
          <DataTable
            columns={lineColumns}
            emptyDescription="Invoice này chưa có line nào."
            emptyTitle="No invoice lines"
            rows={invoice.lines}
          />
        </>
      ) : null}
    </DashboardLayout>
  )
}
