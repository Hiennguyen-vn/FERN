import { Button, Card, EmptyState, FormActions, QuantityInput, Select, Textarea } from '@design-system/index'
import { formatMoney } from '@shared/formatters'
import type { CartDraft, PosOrderType } from '../model/pos.types'
import { calculateCartEstimatedTotal } from '../services/catalogResolver.service'

interface PosCartPanelProps {
  canSubmit: boolean
  createLabel?: string
  draft: CartDraft
  isSubmitting?: boolean
  onCreate: () => void
  onItemNoteChange: (productId: number, note: string) => void
  onOrderNoteChange: (note: string) => void
  onOrderTypeChange: (orderType: PosOrderType) => void
  onQtyChange: (productId: number, qty: string) => void
  onRemove: (productId: number) => void
}

export function PosCartPanel({
  canSubmit,
  createLabel = 'Create order',
  draft,
  isSubmitting,
  onCreate,
  onItemNoteChange,
  onOrderNoteChange,
  onOrderTypeChange,
  onQtyChange,
  onRemove,
}: PosCartPanelProps) {
  const total = calculateCartEstimatedTotal(draft)
  const currencyCode = draft.items[0]?.currencyCode ?? 'VND'

  return (
    <Card className="pos-cart-panel" title="Cart">
      <div className="page-stack">
        <Select
          label="Order type"
          onChange={(event) => onOrderTypeChange(event.target.value as PosOrderType)}
          options={[
            { label: 'Dine in', value: 'DINE_IN' },
            { label: 'Takeaway', value: 'TAKEAWAY' },
          ]}
          value={draft.orderType}
        />

        {draft.items.length === 0 ? (
          <EmptyState description="Chọn món từ catalog để bắt đầu tạo đơn." title="Cart is empty" />
        ) : (
          <div className="page-stack">
            {draft.items.map((item) => (
              <div className="pos-cart-line" key={item.productId}>
                <div className="pos-cart-line-main">
                  <strong>{item.productName}</strong>
                  <span className="muted-text">
                    {item.productCode} · {formatMoney(item.unitPrice, item.currencyCode)}
                  </span>
                  <Textarea
                    label="Line note"
                    onChange={(event) => onItemNoteChange(item.productId, event.target.value)}
                    placeholder="Optional note"
                    rows={2}
                    value={item.note}
                  />
                </div>
                <div className="pos-cart-line-actions">
                  <QuantityInput
                    label="Qty"
                    min="0.0001"
                    onChange={(event) => onQtyChange(item.productId, event.target.value)}
                    step="0.0001"
                    value={item.qty}
                  />
                  <Button onClick={() => onRemove(item.productId)} size="sm" variant="ghost">
                    Remove
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}

        <Textarea
          label="Order note"
          onChange={(event) => onOrderNoteChange(event.target.value)}
          placeholder="Optional order note"
          rows={3}
          value={draft.orderNote}
        />
        <div className="page-header pos-cart-total">
          <span className="muted-text">Estimated total</span>
          <strong>{formatMoney(total, currencyCode)}</strong>
        </div>
        <FormActions
          primaryAction={
            <Button disabled={!canSubmit} loading={isSubmitting} onClick={onCreate}>
              {createLabel}
            </Button>
          }
        />
      </div>
    </Card>
  )
}
