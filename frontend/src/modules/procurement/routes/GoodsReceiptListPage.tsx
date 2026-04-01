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
import { useGoodsReceipts } from '../hooks/useGoodsReceipt'
import type { GoodsReceipt } from '../model/procurement.types'
import { canReadGoodsReceipts } from '../services/procurementPermission.service'

const statusOptions: SelectOption[] = [
  { label: 'All statuses', value: '' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Received', value: 'RECEIVED' },
  { label: 'Posted', value: 'POSTED' },
  { label: 'Cancelled', value: 'CANCELLED' },
]

export function GoodsReceiptListPage() {
  usePageTitle('Goods Receipts')

  const principal = usePrincipal()
  const [purchaseOrderId, setPurchaseOrderId] = useState('')
  const [outletId, setOutletId] = useState('')
  const [status, setStatus] = useState('')

  const canRead = canReadGoodsReceipts(principal)

  const query = useGoodsReceipts(
    canRead
      ? {
          purchaseOrderId: purchaseOrderId ? Number(purchaseOrderId) : undefined,
          outletId: outletId ? Number(outletId) : undefined,
          status: status || undefined,
          limit: 50,
        }
      : undefined,
  )

  const columns: Array<DataTableColumn<GoodsReceipt>> = [
    { key: 'id', header: 'ID', render: (row) => <Link to={`/procurement/goods-receipts/${row.id}`}>#{row.id}</Link> },
    { key: 'receiptNumber', header: 'Receipt #', render: (row) => row.receiptNumber },
    { key: 'purchaseOrderId', header: 'PO', render: (row) => row.purchaseOrderId ? <Link to={`/procurement/purchase-orders/${row.purchaseOrderId}`}>#{row.purchaseOrderId}</Link> : 'N/A' },
    { key: 'supplierId', header: 'Supplier', render: (row) => `#${row.supplierId}` },
    { key: 'outletId', header: 'Outlet', render: (row) => `#${row.outletId}` },
    { key: 'totalAmount', header: 'Total', render: (row) => row.totalAmount },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  if (!canRead) {
    return (
      <DashboardLayout description="Browse and manage goods receipts." title="Goods Receipts">
        <PermissionDeniedInline message="Bạn cần quyền procurement.gr.read để xem goods receipts." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <Link to="/procurement/goods-receipts/new">
          <Button>New GR</Button>
        </Link>
      }
      description="Browse and manage goods receipts."
      title="Goods Receipts"
    >
      <Card title="Filters">
        <div className="field-grid">
          <Input
            label="Purchase Order ID"
            onChange={(e) => setPurchaseOrderId(e.target.value)}
            placeholder="Optional"
            value={purchaseOrderId}
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
          message={query.error instanceof Error ? query.error.message : 'Failed to load goods receipts'}
          onAction={() => void query.refetch()}
          title="Không thể tải goods receipts"
        />
      ) : null}

      {!query.error ? (
        <DataTable
          columns={columns}
          emptyDescription="Chưa có goods receipt nào phù hợp với bộ lọc."
          emptyTitle="No goods receipts"
          loading={query.isLoading}
          rows={query.data ?? []}
        />
      ) : null}
    </DashboardLayout>
  )
}
