import type { CartDraft, OrderLineDraft, UpdateSaleOrderPayload } from '../model/pos.types'

export function buildCreateOrderPayload(draft: CartDraft, sessionId: number) {
  return {
    posSessionId: sessionId,
    orderType: draft.orderType,
    note: draft.orderNote || undefined,
    lines: draft.items.map((item) => ({
      productId: item.productId,
      qty: Number(item.qty),
      note: item.note || undefined,
    })),
  }
}

export function buildUpdateOrderPayload(orderDraft: { note: string; lines: OrderLineDraft[] }): UpdateSaleOrderPayload {
  return {
    note: orderDraft.note || undefined,
    lines: orderDraft.lines.map((line) => ({
      productId: line.productId,
      qty: Number(line.qty),
      note: line.note || undefined,
    })),
  }
}
