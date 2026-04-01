import { useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Button, Card, DataTable, EmptyState, EntityHeader, ErrorState, PermissionDeniedInline, ReadonlyBanner, StatusBadge } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useConfirmAction } from '@shared/hooks/useConfirmAction'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useGoodsReceipt, useGoodsReceiptAction } from '../hooks/useGoodsReceipt'
import type { GoodsReceiptLine } from '../model/procurement.types'
import {
  canCancelGoodsReceipt,
  canPostGoodsReceipt,
  canReceiveGoodsReceipt,
} from '../services/goodsReceiptUiPolicy.service'
import { canReadGoodsReceipts } from '../services/procurementPermission.service'

export function GoodsReceiptDetailPage() {
  const principal = usePrincipal()
  const params = useParams<{ goodsReceiptId: string }>()
  const goodsReceiptId = params.goodsReceiptId ? Number(params.goodsReceiptId) : null
  const confirmAction = useConfirmAction()
  const query = useGoodsReceipt(goodsReceiptId)
  const actionMutation = useGoodsReceiptAction()

  usePageTitle(goodsReceiptId ? `Goods Receipt #${goodsReceiptId}` : 'Goods Receipt Detail')

  if (!canReadGoodsReceipts(principal)) {
    return (
      <DashboardLayout description="Inspect goods receipt lifecycle and post inventory-affecting actions." title="Goods Receipt Detail">
        <PermissionDeniedInline message="Bạn cần quyền procurement.gr.read để xem goods receipt." />
      </DashboardLayout>
    )
  }

  const receipt = query.data
  const isReadonly = receipt ? ['POSTED', 'CANCELLED'].includes(receipt.status.toUpperCase()) : false

  const columns: Array<DataTableColumn<GoodsReceiptLine>> = [
    { key: 'id', header: 'Line ID', render: (row) => `#${row.id}` },
    { key: 'purchaseOrderLineId', header: 'PO Line', render: (row) => row.purchaseOrderLineId ?? 'N/A' },
    { key: 'ingredientId', header: 'Ingredient', render: (row) => `#${row.ingredientId}` },
    { key: 'qtyReceived', header: 'Qty Received', render: (row) => row.qtyReceived },
    { key: 'unitCost', header: 'Unit Cost', render: (row) => row.unitCost },
    { key: 'lineTotal', header: 'Line Total', render: (row) => row.lineTotal },
  ]

  return (
    <DashboardLayout
      description="Inspect goods receipt lifecycle and post inventory-affecting actions."
      title="Goods Receipt Detail"
    >
      {query.isLoading ? (
        <Card title="Loading goods receipt">
          <p className="muted-text">Loading goods receipt header, lines and workflow state...</p>
        </Card>
      ) : null}
      {query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load goods receipt'}
          onAction={() => void query.refetch()}
          title="Không thể tải goods receipt"
        />
      ) : null}
      {!query.isLoading && !query.error && !receipt ? (
        <EmptyState
          description="Goods receipt này không tồn tại, hoặc không còn truy cập được từ frontend hiện tại."
          title="Goods receipt not found"
        />
      ) : null}
      {receipt ? (
        <>
          <EntityHeader
            actions={
              <>
                {canReceiveGoodsReceipt(receipt.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => void actionMutation.mutateAsync({ id: receipt.id, action: 'receive' })}
                    size="sm"
                  >
                    Receive
                  </Button>
                ) : null}
                {canPostGoodsReceipt(receipt.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => {
                      if (confirmAction('Post this goods receipt to inventory?')) {
                        void actionMutation.mutateAsync({ id: receipt.id, action: 'post' })
                      }
                    }}
                    size="sm"
                  >
                    Post
                  </Button>
                ) : null}
                {canCancelGoodsReceipt(receipt.status) ? (
                  <Button
                    loading={actionMutation.isPending}
                    onClick={() => {
                      if (confirmAction('Cancel this goods receipt?')) {
                        void actionMutation.mutateAsync({ id: receipt.id, action: 'cancel' })
                      }
                    }}
                    size="sm"
                    variant="danger"
                  >
                    Cancel
                  </Button>
                ) : null}
              </>
            }
            eyebrow="Goods Receipt"
            metadata={
              <>
                <span>PO ID: #{receipt.purchaseOrderId}</span>
                <span>Supplier ID: #{receipt.supplierId}</span>
                <span>Receipt Time: {new Date(receipt.receiptTime).toLocaleString()}</span>
                <span>Business Date: {receipt.businessDate}</span>
                <span>Total Amount: {receipt.totalAmount}</span>
                <span>Lot Number: {receipt.supplierLotNumber ?? 'N/A'}</span>
              </>
            }
            status={<StatusBadge status={receipt.status} />}
            title={receipt.receiptNumber}
          />
          {isReadonly ? (
            <ReadonlyBanner message="Goods receipt này đang ở trạng thái terminal và hiện ở chế độ chỉ đọc." />
          ) : null}
          <Card title="Notes">
            <p className="muted-text">{receipt.note || 'No notes.'}</p>
          </Card>
          {actionMutation.error ? (
            <ErrorState
              message={actionMutation.error instanceof Error ? actionMutation.error.message : 'Failed to update goods receipt'}
              title="Không thể cập nhật goods receipt"
            />
          ) : null}
          <DataTable
            columns={columns}
            emptyDescription="Goods receipt này chưa có line nhận hàng nào."
            emptyTitle="No goods receipt lines"
            rows={receipt.lines}
          />
        </>
      ) : null}
    </DashboardLayout>
  )
}
