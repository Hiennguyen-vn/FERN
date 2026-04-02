import { describe, expect, it } from 'vitest'
import { buildCreateOrderPayload, buildUpdateOrderPayload } from '../services/orderPayload.mapper'
import type { CartDraft, OrderLineDraft } from '../model/pos.types'

function makeCartItem(overrides?: Partial<CartDraft['items'][number]>): CartDraft['items'][number] {
  return {
    productId: 1,
    productCode: 'P001',
    productName: 'Test Product',
    categoryCode: 'FOOD',
    currencyCode: 'VND',
    unitPrice: '10.00',
    qty: '2',
    note: '',
    ...overrides,
  }
}

function makeCartDraft(overrides?: Partial<CartDraft>): CartDraft {
  return {
    outletId: 101,
    orderType: 'DINE_IN',
    orderNote: '',
    items: [makeCartItem()],
    ...overrides,
  }
}

function makeLineDraft(overrides?: Partial<OrderLineDraft>): OrderLineDraft {
  return {
    productId: 1,
    productCode: 'P001',
    productNameSnapshot: 'Test Product',
    qty: '2',
    note: '',
    unitPrice: '10.00',
    lineTotal: '20.00',
    taxAmount: '0.00',
    ...overrides,
  }
}

describe('buildCreateOrderPayload', () => {
  it('maps valid cart to correct payload shape', () => {
    const payload = buildCreateOrderPayload(
      makeCartDraft({ items: [makeCartItem({ productId: 1, qty: '3.5', note: 'extra sauce' })] }),
      42,
    )
    expect(payload).toEqual({
      posSessionId: 42,
      orderType: 'DINE_IN',
      note: undefined,
      lines: [{ productId: 1, qty: 3.5, note: 'extra sauce' }],
    })
  })

  it('passes through numeric qty as number', () => {
    const payload = buildCreateOrderPayload(
      makeCartDraft({ items: [makeCartItem({ productId: 5, qty: '0.0001' })] }),
      1,
    )
    expect(payload.lines[0].qty).toBe(0.0001)
  })

  // Backend: OrderLineInput.qty @NotNull @DecimalMin("0.0001")
  it('throws when qty is empty string (NaN)', () => {
    expect(() =>
      buildCreateOrderPayload(makeCartDraft({ items: [makeCartItem({ qty: '' })] }), 1),
    ).toThrow(/qty.*product.*must be a number.*0\.0001/i)
  })

  it('throws when qty is non-numeric string', () => {
    expect(() =>
      buildCreateOrderPayload(makeCartDraft({ items: [makeCartItem({ qty: 'abc' })] }), 1),
    ).toThrow()
  })

  it('throws when qty is zero', () => {
    expect(() =>
      buildCreateOrderPayload(makeCartDraft({ items: [makeCartItem({ qty: '0' })] }), 1),
    ).toThrow()
  })

  it('throws when qty is negative', () => {
    expect(() =>
      buildCreateOrderPayload(makeCartDraft({ items: [makeCartItem({ qty: '-1' })] }), 1),
    ).toThrow()
  })

  it('throws when qty is below minimum (0.00009)', () => {
    expect(() =>
      buildCreateOrderPayload(makeCartDraft({ items: [makeCartItem({ qty: '0.00009' })] }), 1),
    ).toThrow()
  })

  it('omits note from order when empty', () => {
    const payload = buildCreateOrderPayload(makeCartDraft({ orderNote: '' }), 1)
    expect(payload.note).toBeUndefined()
  })

  it('includes order note when present', () => {
    const payload = buildCreateOrderPayload(makeCartDraft({ orderNote: 'Rush order' }), 1)
    expect(payload.note).toBe('Rush order')
  })
})

describe('buildUpdateOrderPayload', () => {
  it('maps valid lines to correct payload', () => {
    const payload = buildUpdateOrderPayload({
      note: 'Updated note',
      lines: [makeLineDraft({ productId: 2, qty: '1.5' })],
    })
    expect(payload.lines[0]).toMatchObject({ productId: 2, qty: 1.5 })
    expect(payload.note).toBe('Updated note')
  })

  // Backend: UpdateSaleOrderRequest.lines @NotEmpty — mapper enforces per-line qty
  it('throws when any line has invalid qty', () => {
    expect(() =>
      buildUpdateOrderPayload({
        note: '',
        lines: [makeLineDraft({ qty: 'NaN_value' })],
      }),
    ).toThrow()
  })

  it('throws when qty is zero on update', () => {
    expect(() =>
      buildUpdateOrderPayload({
        note: '',
        lines: [makeLineDraft({ qty: '0' })],
      }),
    ).toThrow()
  })

  it('omits line note when empty string', () => {
    const payload = buildUpdateOrderPayload({
      note: '',
      lines: [makeLineDraft({ qty: '2', note: '' })],
    })
    expect(payload.lines[0].note).toBeUndefined()
  })

  it('includes line note when present', () => {
    const payload = buildUpdateOrderPayload({
      note: '',
      lines: [makeLineDraft({ qty: '2', note: 'no spice' })],
    })
    expect(payload.lines[0].note).toBe('no spice')
  })
})
