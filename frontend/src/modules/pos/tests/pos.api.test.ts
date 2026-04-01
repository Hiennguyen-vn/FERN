import { describe, expect, it, vi, afterEach } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { addSalePayment, createSaleOrder, openPosSession, updateSaleOrder } from '../api/pos.api'

const MOCK_SESSION = {
  id: 1,
  sessionCode: 'POS-001',
  regionId: 1,
  outletId: 101,
  terminalId: null,
  currencyCode: 'VND',
  cashierUserId: 7,
  managerUserId: null,
  businessDate: '2026-03-29',
  status: 'OPEN',
  note: null,
  openedAt: '2026-03-29T01:00:00Z',
  closedAt: null,
  reconciledAt: null,
  expectedCashAmount: null,
  countedCashAmount: null,
  discrepancyAmount: null,
}

describe('pos.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('parses X-Session-Existed header when opening session', async () => {
    vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: MOCK_SESSION,
      headers: { 'x-session-existed': 'true' },
    } as any)

    const result = await openPosSession({
      regionId: 1,
      outletId: 101,
      currencyCode: 'VND',
      businessDate: '2026-03-29',
    })

    expect(result.sessionExisted).toBe(true)
    expect(result.session.sessionCode).toBe('POS-001')
  })

  /**
   * Backend OpenSessionRequest.terminalId — optional, max 64 chars, pattern ^[A-Za-z0-9_-]+$
   * When provided, the backend uses it for session affinity (X-Session-Existed: true).
   */
  it('forwards terminalId in open-session payload when provided', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: { ...MOCK_SESSION, terminalId: 'TERMINAL-01' },
      headers: { 'x-session-existed': 'false' },
    } as any)

    await openPosSession({
      regionId: 1,
      outletId: 101,
      currencyCode: 'VND',
      businessDate: '2026-03-29',
      terminalId: 'TERMINAL-01',
    })

    expect(postSpy).toHaveBeenCalledWith('/pos-sessions', expect.objectContaining({
      terminalId: 'TERMINAL-01',
    }))
  })

  it('omits terminalId from open-session payload when not provided', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: MOCK_SESSION,
      headers: { 'x-session-existed': 'false' },
    } as any)

    await openPosSession({
      regionId: 1,
      outletId: 101,
      currencyCode: 'VND',
      businessDate: '2026-03-29',
    })

    const sentPayload = postSpy.mock.calls[0]?.[1] as Record<string, unknown>
    expect(sentPayload).not.toHaveProperty('terminalId')
  })

  it('drops legacy currencyCode when creating a sale order', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 5 } } as any)

    await createSaleOrder({
      posSessionId: 1,
      orderType: 'DINE_IN',
      currencyCode: 'VND',
      note: 'Table 8',
      lines: [{ productId: 10, qty: 2, note: 'Less ice' }],
    })

    expect(postSpy).toHaveBeenCalledWith('/sale-orders', {
      posSessionId: 1,
      orderType: 'DINE_IN',
      note: 'Table 8',
      lines: [{ productId: 10, qty: 2, note: 'Less ice' }],
    })
  })

  /**
   * Backend POST /sale-orders/{id}/payments requires Idempotency-Key header.
   * The auto-generated key must be present on every call to prevent duplicate charges.
   */
  it('includes Idempotency-Key header when adding a sale payment (auto-generated)', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 5 } } as any)

    await addSalePayment(5, { paymentMethod: 'CASH', amount: 50000 })

    const [, , config] = postSpy.mock.calls[0] as [string, unknown, { headers: Record<string, string> }]
    expect(config?.headers?.['Idempotency-Key']).toBeTruthy()
    expect(typeof config.headers['Idempotency-Key']).toBe('string')
  })

  /**
   * When the caller provides an explicit idempotency key (e.g. for retry),
   * that exact key must be forwarded — not a newly generated one.
   */
  it('uses caller-supplied Idempotency-Key when provided', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 5 } } as any)
    const KEY = 'my-retry-key-abc123'

    await addSalePayment(5, { paymentMethod: 'CARD', amount: 100000 }, KEY)

    const [, , config] = postSpy.mock.calls[0] as [string, unknown, { headers: Record<string, string> }]
    expect(config?.headers?.['Idempotency-Key']).toBe(KEY)
  })

  /**
   * Backend UpdateSaleOrderRequest.lines is @NotEmpty — the caller must always
   * provide at least one line. The frontend type enforces this structurally.
   */
  it('sends lines in updateSaleOrder payload', async () => {
    const patchSpy = vi.spyOn(gatewayClient, 'patch').mockResolvedValue({ data: { id: 5 } } as any)

    await updateSaleOrder(5, {
      lines: [{ productId: 99, qty: 3 }],
    })

    expect(patchSpy).toHaveBeenCalledWith('/sale-orders/5', expect.objectContaining({
      lines: [{ productId: 99, qty: 3 }],
    }))
  })
})
