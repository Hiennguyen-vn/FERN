import { useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import {
  Button,
  Card,
  DataTable,
  ErrorState,
  Input,
  PermissionDeniedInline,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useSupplierInvoices } from '../hooks/useSupplierInvoice'
import type { SupplierInvoice } from '../model/procurement.types'
import { canReadInvoices, canReviewInvoice } from '../services/procurementPermission.service'

const statusOptions: SelectOption[] = [
  { label: 'All statuses', value: '' },
  { label: 'Received', value: 'RECEIVED' },
  { label: 'Matched', value: 'MATCHED' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Disputed', value: 'DISPUTED' },
  { label: 'Cancelled', value: 'CANCELLED' },
]

export function SupplierInvoiceListPage() {
  usePageTitle('Supplier Invoices')

  const principal = usePrincipal()
  const [supplierId, setSupplierId] = useState('')
  const [outletId, setOutletId] = useState('')
  const [status, setStatus] = useState('')

  const canRead = canReadInvoices(principal)

  const query = useSupplierInvoices(
    canRead
      ? {
          supplierId: supplierId ? Number(supplierId) : undefined,
          outletId: outletId ? Number(outletId) : undefined,
          status: status || undefined,
          limit: 50,
        }
      : undefined,
  )

  const columns: Array<DataTableColumn<SupplierInvoice>> = [
    { key: 'id', header: 'ID', render: (row) => <Link to={`/finance/supplier-invoices/${row.id}`}>#{row.id}</Link> },
    { key: 'invoiceNumber', header: 'Invoice #', render: (row) => row.invoiceNumber },
    { key: 'supplierId', header: 'Supplier', render: (row) => `#${row.supplierId}` },
    { key: 'outletId', header: 'Outlet', render: (row) => `#${row.outletId}` },
    { key: 'invoiceDate', header: 'Invoice Date', render: (row) => String(row.invoiceDate) },
    { key: 'totalAmount', header: 'Total', render: (row) => row.totalAmount },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  if (!canRead) {
    return (
      <DashboardLayout description="Browse and manage supplier invoices." title="Supplier Invoices">
        <PermissionDeniedInline message="Bạn cần quyền procurement.invoice.read để xem supplier invoices." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        canReviewInvoice(principal) ? (
          <Link to="/finance/supplier-invoices/new">
            <Button>New Invoice</Button>
          </Link>
        ) : undefined
      }
      description="Browse and manage supplier invoices."
      title="Supplier Invoices"
    >
      <Card title="Filters">
        <div className="field-grid">
          <Input
            label="Supplier ID"
            onChange={(e) => setSupplierId(e.target.value)}
            placeholder="Optional"
            value={supplierId}
          />
          <Input
            label="Outlet ID"
            onChange={(e) => setOutletId(e.target.value)}
            placeholder="Optional"
            value={outletId}
          />
          <Select
            label="Status"
            onChange={(e) => setStatus(e.target.value)}
            options={statusOptions}
            value={status}
          />
        </div>
      </Card>

      {query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load supplier invoices'}
          onAction={() => void query.refetch()}
          title="Không thể tải supplier invoices"
        />
      ) : null}

      {!query.error ? (
        <DataTable
          columns={columns}
          emptyDescription="Chưa có supplier invoice nào phù hợp với bộ lọc."
          emptyTitle="No supplier invoices"
          loading={query.isLoading}
          rows={query.data ?? []}
        />
      ) : null}
    </DashboardLayout>
  )
}
