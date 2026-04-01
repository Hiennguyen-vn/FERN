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
import type { DataTableColumn } from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { usePurchaseOrders } from '../hooks/usePurchaseOrder'
import type { PurchaseOrder } from '../model/procurement.types'
import { canReadPurchaseOrders } from '../services/procurementPermission.service'

const statusOptions: SelectOption[] = [
  { label: 'All statuses', value: '' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Submitted', value: 'SUBMITTED' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Ordered', value: 'ORDERED' },
  { label: 'Partially received', value: 'PARTIALLY_RECEIVED' },
  { label: 'Completed', value: 'COMPLETED' },
  { label: 'Cancelled', value: 'CANCELLED' },
]

export function PurchaseOrderListPage() {
  usePageTitle('Purchase Orders')

  const principal = usePrincipal()
  const [outletId, setOutletId] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const [status, setStatus] = useState('')

  const canRead = canReadPurchaseOrders(principal)

  const query = usePurchaseOrders(
    canRead
      ? {
          outletId: outletId ? Number(outletId) : undefined,
          supplierId: supplierId ? Number(supplierId) : undefined,
          status: status || undefined,
          limit: 50,
        }
      : undefined,
  )

  const columns: Array<DataTableColumn<PurchaseOrder>> = [
    { key: 'id', header: 'ID', render: (row) => <Link to={`/procurement/purchase-orders/${row.id}`}>#{row.id}</Link> },
    { key: 'poNumber', header: 'PO Number', render: (row) => row.poNumber },
    { key: 'supplierId', header: 'Supplier', render: (row) => `#${row.supplierId}` },
    { key: 'outletId', header: 'Outlet', render: (row) => `#${row.outletId}` },
    { key: 'orderDate', header: 'Order Date', render: (row) => String(row.orderDate) },
    { key: 'totalAmount', header: 'Total', render: (row) => row.totalAmount },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  if (!canRead) {
    return (
      <DashboardLayout description="Browse and manage purchase orders." title="Purchase Orders">
        <PermissionDeniedInline message="Bạn cần quyền procurement.po.read để xem purchase orders." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <Link to="/procurement/purchase-orders/new">
          <Button>New PO</Button>
        </Link>
      }
      description="Browse and manage purchase orders."
      title="Purchase Orders"
    >
      <Card title="Filters">
        <div className="field-grid">
          <Input
            label="Outlet ID"
            onChange={(e) => setOutletId(e.target.value)}
            placeholder="Optional"
            value={outletId}
          />
          <Input
            label="Supplier ID"
            onChange={(e) => setSupplierId(e.target.value)}
            placeholder="Optional"
            value={supplierId}
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
          message={query.error instanceof Error ? query.error.message : 'Failed to load purchase orders'}
          onAction={() => void query.refetch()}
          title="Không thể tải purchase orders"
        />
      ) : null}

      {!query.error ? (
        <DataTable
          columns={columns}
          emptyDescription="Chưa có purchase order nào phù hợp với bộ lọc."
          emptyTitle="No purchase orders"
          loading={query.isLoading}
          rows={query.data ?? []}
        />
      ) : null}
    </DashboardLayout>
  )
}
