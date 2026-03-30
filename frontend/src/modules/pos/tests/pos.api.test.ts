import { describe, expect, it, vi, afterEach } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { openPosSession } from '../api/pos.api'

describe('pos.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('parses X-Session-Existed header when opening session', async () => {
    vi.spyOn(gatewayClient, 'post').mockResolvedValue({
      data: {
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
      },
      headers: {
        'x-session-existed': 'true',
      },
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
})
