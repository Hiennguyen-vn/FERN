import type { CartDraft, OrderLineDraft, UpdateSaleOrderPayload } from '../model/pos.types'

// Backend: OrderLineInput.qty is @NotNull @DecimalMin("0.0001")
const MIN_LINE_QTY = 0.0001

function parseLineQty(raw: string | number, productId: number): number {
  const n = Number(raw)
  if (!Number.isFinite(n) || n < MIN_LINE_QTY) {
    throw new Error(
      `Qty for product #${productId} must be a number ≥ ${MIN_LINE_QTY} (got "${raw}").`,
    )
  }
  return n
}

export function buildCreateOrderPayload(draft: CartDraft, sessionId: number) {
  return {
    posSessionId: sessionId,
    orderType: draft.orderType,
    note: draft.orderNote || undefined,
    lines: draft.items.map((item) => ({
      productId: item.productId,
      qty: parseLineQty(item.qty, item.productId),
      note: item.note || undefined,
    })),
  }
}

export function buildUpdateOrderPayload(orderDraft: { note: string; lines: OrderLineDraft[] }): UpdateSaleOrderPayload {
  return {
    note: orderDraft.note || undefined,
    lines: orderDraft.lines.map((line) => ({
      productId: line.productId,
      qty: parseLineQty(line.qty, line.productId),
      note: line.note || undefined,
    })),
  }
}
