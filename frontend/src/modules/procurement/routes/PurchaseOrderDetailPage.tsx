import { useParams, Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Button, Card, DataTable, EmptyState, EntityHeader, ErrorState, PermissionDeniedInline, ReadonlyBanner, StatusBadge } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useConfirmAction } from '@shared/hooks/useConfirmAction'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAuthStore } from '@core/auth/auth.store'
import { usePurchaseOrder, usePurchaseOrderAction } from '../hooks/usePurchaseOrder'
import type { PurchaseOrderLine } from '../model/procurement.types'
import {
  canApprovePurchaseOrder,
  canCancelPurchaseOrder,
  canIssuePurchaseOrder,
  canSubmitPurchaseOrder,
} from '../services/purchaseOrderUiPolicy.service'
import { canReadPurchaseOrders } from '../services/procurementPermission.service'

export function PurchaseOrderDetailPage() {
  const principal = useAuthStore((state) => state.principal)
  const params = useParams<{ purchaseOrderId: string }>()
  const purchaseOrderId = params.purchaseOrderId ? Number(params.purchaseOrderId) : null
  const confirmAction = useConfirmAction()
  const query = usePurchaseOrder(purchaseOrderId)
  const actionMutation = usePurchaseOrderAction()

  usePageTitle(purchaseOrderId ? `Purchase Order #${purchaseOrderId}` : 'Purchase Order Detail')

  if (!canReadPurchaseOrders(principal)) {
    return <PermissionDeniedInline message="Bạn cần quyền procurement.po.read để xem purchase order." />
  }

  const columns: Array<DataTableColumn<PurchaseOrderLine>> = [
    { key: 'lineNumber', header: 'Line', render: (row) => row.lineNumber },
    { key: 'ingredientId', header: 'Ingredient', render: (row) => `#${row.ingredientId}` },
    { key: 'uomCode', header: 'UOM', render: (row) => row.uomCode },
    { key: 'qtyOrdered', header: 'Qty Ordered', render: (row) => row.qtyOrdered },
    { key: 'qtyReceived', header: 'Qty Received', render: (row) => row.qtyReceived },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  const po = query.data
  const isReadonly = po ? ['ISSUED', 'CANCELLED'].includes(po.status.toUpperCase()) : false

  return (
    <DashboardLayout
      description="Inspect purchase order lifecycle and trigger workflow transitions."
      title="Purchase Order Detail"
    >
      {query.isLoading ? (
        <Card title="Loading purchase order">
          <p className="muted-text">Loading purchase order header, lines and lifecycle state...</p>
        </Card>
      ) : null}
      {query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load purchase order'}
          onAction={() => void query.refetch()}
          title="Không thể tải purchase order"
        />
      ) : null}
      {!query.isLoading && !query.error && !po ? (
        <EmptyState
          description="Purchase order này không tồn tại, hoặc không còn truy cập được từ frontend hiện tại."
          title="Purchase order not found"
        />
      ) : null}
      {po ? (
        <>
          <EntityHeader
            actions={
              <>
                {canSubmitPurchaseOrder(po.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => {
                      if (confirmAction('Submit this purchase order?')) {
                        void actionMutation.mutateAsync({ id: po.id, action: 'submit' })
                      }
                    }}
                    size="sm"
                  >
                    Submit
                  </Button>
                ) : null}
                {canApprovePurchaseOrder(po.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => void actionMutation.mutateAsync({ id: po.id, action: 'approve' })}
                    size="sm"
                  >
                    Approve
                  </Button>
                ) : null}
                {canIssuePurchaseOrder(po.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => void actionMutation.mutateAsync({ id: po.id, action: 'issue' })}
                    size="sm"
                  >
                    Issue
                  </Button>
                ) : null}
                {canCancelPurchaseOrder(po.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => {
                      if (confirmAction('Cancel this purchase order?')) {
                        void actionMutation.mutateAsync({ id: po.id, action: 'cancel' })
                      }
                    }}
                    size="sm"
                    variant="danger"
                  >
                    Cancel
                  </Button>
                ) : null}
                <Button asChild size="sm" variant="secondary">
                  <Link to={`/procurement/goods-receipts/new?purchaseOrderId=${po.id}`}>Create GR</Link>
                </Button>
              </>
            }
            eyebrow="Purchase Order"
            metadata={
              <>
                <span>Supplier ID: #{po.supplierId}</span>
                <span>Region ID: #{po.regionId}</span>
                <span>Outlet ID: #{po.outletId}</span>
                <span>Order Date: {po.orderDate}</span>
                <span>Expected Delivery: {po.expectedDeliveryDate ?? 'N/A'}</span>
                <span>Total: {po.totalAmount}</span>
              </>
            }
            status={<StatusBadge status={po.status} />}
            title={po.poNumber}
          />
          {isReadonly ? (
            <ReadonlyBanner message="Purchase order này đang ở trạng thái terminal và hiện ở chế độ chỉ đọc." />
          ) : null}
          <Card title="Notes">
            <p className="muted-text">{po.note || 'No notes.'}</p>
          </Card>
          {actionMutation.error ? (
            <ErrorState
              message={actionMutation.error instanceof Error ? actionMutation.error.message : 'Failed to update purchase order'}
              title="Không thể cập nhật purchase order"
            />
          ) : null}
          <DataTable
            columns={columns}
            emptyDescription="Purchase order này chưa có line nào."
            emptyTitle="No purchase order lines"
            rows={po.lines}
          />
        </>
      ) : null}
    </DashboardLayout>
  )
}
