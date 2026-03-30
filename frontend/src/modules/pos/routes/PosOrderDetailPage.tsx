import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  ConfirmActionDialog,
  CurrencyInput,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  QuantityInput,
  ReadonlyBanner,
  Select,
  StatusBadge,
  Textarea,
} from '@design-system/index'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'
import { formatDateTime, formatMoney } from '@shared/formatters'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { PosPaymentHistoryTable } from '../components/PosPaymentHistoryTable'
import { useAddSalePayment, useCancelPosOrder, useCompletePosOrder, usePosOrder, useUpdatePosOrder } from '../hooks/usePosOrder'
import { selectPosQueueCounts, selectQueuedPaymentsForOrder, usePosQueueStore } from '../offline/posQueue.store'
import type { OrderLineDraft } from '../model/pos.types'
import { buildUpdateOrderPayload } from '../services/orderPayload.mapper'
import {
  canCancelOrder,
  canCompleteOrder,
  canUpdateOrder,
  isOrderReadonly,
} from '../services/orderUiPolicy.service'
import {
  calculateOutstandingAmount,
  canAddPayment,
  posPaymentMethodOptions,
  posPaymentStatusOptions,
} from '../services/paymentUiPolicy.service'
import {
  canCancelOrderAction,
  canCompleteOrderAction,
  canManageOrderUpdates,
  canReadOrders,
} from '../services/posUiPolicy.service'

function toEditableLines(lines: Array<{
  productId: number
  productCode: string
  productNameSnapshot: string
  qty: string
  note: string | null
  unitPrice: string
  lineTotal: string
  taxAmount: string
}>): OrderLineDraft[] {
  return lines.map((line) => ({
    productId: line.productId,
    productCode: line.productCode,
    productNameSnapshot: line.productNameSnapshot,
    qty: line.qty,
    note: line.note ?? '',
    unitPrice: line.unitPrice,
    lineTotal: line.lineTotal,
    taxAmount: line.taxAmount,
  }))
}

export function PosOrderDetailPage() {
  const params = useParams<{ orderId: string }>()
  const orderId = params.orderId ? Number(params.orderId) : null
  const principal = usePrincipal()
  const isOnline = useNetworkStatus()
  const [draftNote, setDraftNote] = useState('')
  const [draftLines, setDraftLines] = useState<OrderLineDraft[]>([])
  const [paymentMethod, setPaymentMethod] = useState('CASH')
  const [paymentStatus, setPaymentStatus] = useState<'SUCCESS' | 'FAILED' | 'CANCELLED'>('SUCCESS')
  const [paymentAmount, setPaymentAmount] = useState('')
  const [paymentTime, setPaymentTime] = useState(new Date().toISOString().slice(0, 16))
  const [transactionRef, setTransactionRef] = useState('')
  const [paymentNote, setPaymentNote] = useState('')
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false)
  const [completeDialogOpen, setCompleteDialogOpen] = useState(false)

  const canReadOrderPermission = canReadOrders(principal)
  const canUpdatePermission = canManageOrderUpdates(principal)
  const canCancelPermission = canCancelOrderAction(principal)
  const canCompletePermission = canCompleteOrderAction(principal)

  const orderQuery = usePosOrder(orderId, canReadOrderPermission)
  const updateMutation = useUpdatePosOrder()
  const addPaymentMutation = useAddSalePayment()
  const completeMutation = useCompletePosOrder()
  const cancelMutation = useCancelPosOrder()
  const queuedActions = usePosQueueStore((state) => state.actions)
  const queuedPaymentsForOrder = orderId !== null ? selectQueuedPaymentsForOrder(queuedActions, orderId) : []
  const queuedPaymentCounts = selectPosQueueCounts(queuedPaymentsForOrder)
  const failedQueuedPayment = queuedPaymentsForOrder.find((action) => action.status === 'FAILED')

  usePageTitle(orderQuery.data ? `Order ${orderQuery.data.orderNumber}` : 'Order Detail')

  useEffect(() => {
    if (!orderQuery.data) {
      return
    }

    setDraftNote(orderQuery.data.note ?? '')
    setDraftLines(toEditableLines(orderQuery.data.lines))
    setPaymentAmount(String(calculateOutstandingAmount(orderQuery.data)))
    setPaymentMethod('CASH')
    setPaymentStatus('SUCCESS')
    setPaymentTime(new Date().toISOString().slice(0, 16))
    setTransactionRef('')
    setPaymentNote('')
  }, [orderQuery.data])

  if (!canReadOrderPermission) {
    return (
      <section className="page-stack pos-order-detail-page">
        <PermissionDeniedInline
          message="Cần quyền `pos.order.read` để xem chi tiết order, payment history và lifecycle actions."
          title="Không thể mở order"
        />
      </section>
    )
  }

  if (orderQuery.isLoading) {
    return (
      <section className="page-stack pos-order-detail-page">
        <Card title="Loading order">
          <p className="muted-text">Loading canonical sale order and payment history...</p>
        </Card>
      </section>
    )
  }

  if (orderQuery.error) {
    return (
      <section className="page-stack pos-order-detail-page">
        <ErrorState
          actionLabel="Retry"
          message={orderQuery.error instanceof Error ? orderQuery.error.message : 'Failed to load order'}
          onAction={() => void orderQuery.refetch()}
          title="Không thể tải order"
        />
      </section>
    )
  }

  const order = orderQuery.data
  const canUpdateCurrentOrder = Boolean(order && canUpdatePermission && canUpdateOrder(order, isOnline))
  const canAddPaymentForOrder = Boolean(order && canUpdatePermission && canAddPayment(order, isOnline))
  const paymentSectionReadonly = Boolean(order && (!canUpdatePermission || !canAddPayment(order, isOnline)))

  if (!order) {
    return (
      <section className="page-stack">
        <EmptyState
          description="Order này không tồn tại, hoặc không còn nằm trong scope hiện tại."
          title="Order not found"
        />
      </section>
    )
  }

  return (
    <section className="page-stack pos-order-detail-page">
      <EntityHeader
        actions={
          <>
            <Button asChild size="sm" variant="secondary">
              <Link to={`/pos/sessions/${order.posSessionId}`}>View session</Link>
            </Button>
            {canCompletePermission && canCompleteOrder(order, isOnline) ? (
              <Button loading={completeMutation.isPending} onClick={() => setCompleteDialogOpen(true)} size="sm">
                Complete order
              </Button>
            ) : null}
            {canCancelPermission && canCancelOrder(order, isOnline) ? (
              <Button loading={cancelMutation.isPending} onClick={() => setCancelDialogOpen(true)} size="sm" variant="danger">
                Cancel order
              </Button>
            ) : null}
          </>
        }
        eyebrow="POS Order"
        metadata={
          <>
            <span>Order #{order.id}</span>
            <span>Session #{order.posSessionId}</span>
            <span>Type: {order.orderType}</span>
            <span>Payment: {order.paymentStatus}</span>
            <span>Created: {formatDateTime(order.createdAt)}</span>
            <span>Total: {formatMoney(order.totalAmount, order.currencyCode)}</span>
          </>
        }
        status={<StatusBadge status={order.status} />}
        title={order.orderNumber}
      />

      {!isOnline ? (
        <ReadonlyBanner message="POS đang offline. Update order, complete và cancel bị tạm khóa. Payment mới vẫn có thể được queue và retry tự động khi kết nối trở lại." />
      ) : null}

      {isOrderReadonly(order) ? (
        <ReadonlyBanner message="Order này không còn editable. Chỉ có thể xem trạng thái và lịch sử payment." />
      ) : null}

      <FormSection description="Chỉ cho phép sửa qty, note và order note khi order đang OPEN và chưa có successful payment." title="Order lines">
        {!canUpdatePermission ? (
          <PermissionDeniedInline
            message="Bạn có thể xem order nhưng không có quyền `pos.order.update` để sửa line hoặc order note."
            title="Không thể cập nhật order"
          />
        ) : null}
        <div className="page-stack">
          {draftLines.map((line, index) => (
            <div className="surface-panel pos-line-editor" key={`${line.productId}-${index}`}>
              <div className="field-grid">
                <Input label="Product" readOnly value={`${line.productCode} · ${line.productNameSnapshot}`} />
                <QuantityInput
                  label="Qty"
                  min="0.0001"
                  onChange={(event) =>
                    setDraftLines((current) =>
                      current.map((item, itemIndex) => (itemIndex === index ? { ...item, qty: event.target.value } : item)),
                    )
                  }
                  readOnly={!canUpdateCurrentOrder}
                  step="0.0001"
                  value={line.qty}
                />
                <Input label="Unit price" readOnly value={formatMoney(line.unitPrice, order.currencyCode)} />
                <Input label="Line total" readOnly value={formatMoney(line.lineTotal, order.currencyCode)} />
              </div>
              <Textarea
                label="Line note"
                onChange={(event) =>
                  setDraftLines((current) =>
                    current.map((item, itemIndex) => (itemIndex === index ? { ...item, note: event.target.value } : item)),
                  )
                }
                readOnly={!canUpdateCurrentOrder}
                rows={2}
                value={line.note}
              />
            </div>
          ))}
        </div>

        <Textarea
          label="Order note"
          onChange={(event) => setDraftNote(event.target.value)}
          readOnly={!canUpdateCurrentOrder}
          rows={3}
          value={draftNote}
        />

        <FormActions
          primaryAction={
            <Button
              disabled={!canUpdateCurrentOrder}
              loading={updateMutation.isPending}
              onClick={() =>
                void updateMutation.mutateAsync({
                  orderId: order.id,
                  payload: buildUpdateOrderPayload({
                    note: draftNote,
                    lines: draftLines,
                  }),
                })
              }
            >
              Update order
            </Button>
          }
        />
        {updateMutation.error ? (
          <ErrorState
            message={updateMutation.error instanceof Error ? updateMutation.error.message : 'Failed to update order'}
            title="Không thể cập nhật order"
          />
        ) : null}
      </FormSection>

      <FormSection
        description="Payment submit sẽ sinh explicit Idempotency-Key trên frontend cho từng lần gửi. Nếu mạng chập chờn, cùng một attempt sẽ được retry với đúng key đó."
        title="Add payment"
      >
        {!canUpdatePermission ? (
          <PermissionDeniedInline
            message="Cần quyền `pos.order.update` để ghi nhận payment mới trên order này."
            title="Không thể thêm payment"
          />
        ) : null}
        {paymentSectionReadonly && canUpdatePermission ? (
          <ReadonlyBanner
            message={
              'Order hiện không còn nhận payment mới theo lifecycle hiện tại.'
            }
          />
        ) : null}
        {!paymentSectionReadonly && queuedPaymentCounts.totalCount > 0 ? (
          <div
            className={
              queuedPaymentCounts.failedCount > 0 ? 'inline-banner inline-banner-danger' : 'inline-banner inline-banner-warning'
            }
            role="status"
          >
            {queuedPaymentCounts.retryingCount > 0 ? (
              <p>
                Đang retry {queuedPaymentCounts.retryingCount} payment queued cho order này với đúng Idempotency-Key ban đầu.
              </p>
            ) : queuedPaymentCounts.failedCount > 0 ? (
              <p>
                {queuedPaymentCounts.failedCount} payment queued chưa sync xong cho order này.
                {failedQueuedPayment?.lastError ? ` Lỗi gần nhất: ${failedQueuedPayment.lastError}` : ''}
              </p>
            ) : (
              <p>
                {queuedPaymentCounts.pendingCount} payment đã được giữ ở hàng chờ cho order này và sẽ retry khi POS reconnect.
              </p>
            )}
          </div>
        ) : null}
        {!paymentSectionReadonly && !isOnline ? (
          <ReadonlyBanner message="Payment submit hiện sẽ được queue tại máy này và retry tự động với cùng Idempotency-Key sau khi reconnect." />
        ) : null}
        <div className="field-grid">
          <Select
            disabled={!canAddPaymentForOrder}
            label="Payment method"
            onChange={(event) => setPaymentMethod(event.target.value)}
            options={posPaymentMethodOptions.map((option) => ({ label: option.label, value: option.value }))}
            value={paymentMethod}
          />
          <Select
            disabled={!canAddPaymentForOrder}
            label="Payment status"
            onChange={(event) => setPaymentStatus(event.target.value as 'SUCCESS' | 'FAILED' | 'CANCELLED')}
            options={posPaymentStatusOptions.map((option) => ({ label: option.label, value: option.value }))}
            value={paymentStatus}
          />
          <CurrencyInput
            disabled={!canAddPaymentForOrder}
            label="Amount"
            min="0.01"
            onChange={(event) => setPaymentAmount(event.target.value)}
            value={paymentAmount}
          />
          <Input
            disabled={!canAddPaymentForOrder}
            label="Payment time"
            onChange={(event) => setPaymentTime(event.target.value)}
            type="datetime-local"
            value={paymentTime}
          />
          <Input
            disabled={!canAddPaymentForOrder}
            label="Transaction ref"
            onChange={(event) => setTransactionRef(event.target.value)}
            placeholder="Optional reference"
            value={transactionRef}
          />
        </div>
        <Textarea
          disabled={!canAddPaymentForOrder}
          label="Payment note"
          onChange={(event) => setPaymentNote(event.target.value)}
          placeholder="Optional note"
          rows={3}
          value={paymentNote}
        />
        <div className="page-header">
          <span className="muted-text">Outstanding amount</span>
          <strong>{formatMoney(calculateOutstandingAmount(order), order.currencyCode)}</strong>
        </div>
        <FormActions
          primaryAction={
            <Button
              disabled={!canAddPaymentForOrder || Number(paymentAmount) <= 0}
              loading={addPaymentMutation.isPending}
              onClick={async () => {
                const result = await addPaymentMutation.mutateAsync({
                  orderId: order.id,
                  payload: {
                    paymentMethod: paymentMethod as any,
                    amount: Number(paymentAmount),
                    paymentTime: new Date(paymentTime).toISOString(),
                    transactionRef: transactionRef || undefined,
                    note: paymentNote || undefined,
                    status: paymentStatus,
                  },
                })

                if (result.kind === 'success' || result.kind === 'queued') {
                  setPaymentAmount(result.kind === 'success' ? String(calculateOutstandingAmount(result.order)) : '')
                  setPaymentMethod('CASH')
                  setPaymentStatus('SUCCESS')
                  setPaymentTime(new Date().toISOString().slice(0, 16))
                  setTransactionRef('')
                  setPaymentNote('')
                }
              }}
            >
              {isOnline ? 'Add payment' : 'Queue payment'}
            </Button>
          }
        />
        {addPaymentMutation.error ? (
          <ErrorState
            message={addPaymentMutation.error instanceof Error ? addPaymentMutation.error.message : 'Failed to add payment'}
            title="Không thể thêm payment"
          />
        ) : null}
      </FormSection>

      <Card title="Totals">
        <div className="pos-summary-grid">
          <span>Subtotal: {formatMoney(order.subtotal, order.currencyCode)}</span>
          <span>Tax: {formatMoney(order.taxAmount, order.currencyCode)}</span>
          <span>Discount: {formatMoney(order.discountAmount, order.currencyCode)}</span>
          <span>Total: {formatMoney(order.totalAmount, order.currencyCode)}</span>
          <span>Outstanding: {formatMoney(calculateOutstandingAmount(order), order.currencyCode)}</span>
          <span>Completed at: {formatDateTime(order.completedAt)}</span>
        </div>
      </Card>

      <Card title="Payment history">
        <PosPaymentHistoryTable currencyCode={order.currencyCode} payments={order.payments} />
      </Card>

      {completeMutation.error ? (
        <ErrorState
          message={completeMutation.error instanceof Error ? completeMutation.error.message : 'Failed to complete order'}
          title="Không thể complete order"
        />
      ) : null}

      {cancelMutation.error ? (
        <ErrorState
          message={cancelMutation.error instanceof Error ? cancelMutation.error.message : 'Failed to cancel order'}
          title="Không thể cancel order"
        />
      ) : null}

      <ConfirmActionDialog
        confirmLabel="Complete order"
        description="Order chỉ được complete khi successful payments đã phủ đủ total amount."
        onCancel={() => setCompleteDialogOpen(false)}
        onConfirm={async () => {
          if (!order) {
            return
          }

          await completeMutation.mutateAsync(order.id)
          setCompleteDialogOpen(false)
        }}
        open={completeDialogOpen}
        title="Complete this order?"
      />

      <ConfirmActionDialog
        confirmLabel="Cancel order"
        danger
        description="Orders có successful payment sẽ bị backend chặn hủy."
        onCancel={() => setCancelDialogOpen(false)}
        onConfirm={async () => {
          if (!order) {
            return
          }

          await cancelMutation.mutateAsync(order.id)
          setCancelDialogOpen(false)
        }}
        open={cancelDialogOpen}
        title="Cancel this order?"
      />
    </section>
  )
}
