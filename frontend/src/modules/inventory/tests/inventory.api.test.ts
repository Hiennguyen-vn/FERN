import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import {
  cancelStockAdjustment,
  cancelStockCountSession,
  cancelWasteRecord,
  createStockAdjustment,
  createStockCountSession,
  createWasteRecord,
  getInventoryTransactions,
  getStockBalances,
  getStockCountSession,
  listStockCountSessions,
  postStockAdjustment,
  postStockCountSession,
  postWasteRecord,
  startStockCountSession,
  updateStockCountLines,
} from '../api/inventory.api'

describe('inventory.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('reads stock balances and transactions with correct paths', async () => {
    const getSpy = vi.spyOn(gatewayClient, 'get').mockResolvedValue({ data: { items: [] } } as any)

    await getStockBalances({ outletId: 101, page: 0, size: 20 })
    await getInventoryTransactions({ outletId: 101, page: 0, size: 20 })

    expect(getSpy).toHaveBeenNthCalledWith(1, '/stock-balances', { params: { outletId: 101, page: 0, size: 20 } })
    expect(getSpy).toHaveBeenNthCalledWith(2, '/inventory-transactions', { params: { outletId: 101, page: 0, size: 20 } })
  })

  it('lists and reads stock count sessions with correct paths', async () => {
    const getSpy = vi.spyOn(gatewayClient, 'get').mockResolvedValue({ data: { items: [] } } as any)

    await listStockCountSessions({ outletId: 101, page: 0, size: 20 })
    expect(getSpy).toHaveBeenCalledWith('/stock-count-sessions', { params: { outletId: 101, page: 0, size: 20 } })

    await listStockCountSessions({ outletId: 101, status: 'DRAFT', page: 1, size: 10 })
    expect(getSpy).toHaveBeenCalledWith('/stock-count-sessions', {
      params: { outletId: 101, status: 'DRAFT', page: 1, size: 10 },
    })

    getSpy.mockResolvedValueOnce({ data: { id: 5, status: 'DRAFT', lines: [] } } as any)
    await getStockCountSession(5)
    expect(getSpy).toHaveBeenCalledWith('/stock-count-sessions/5')
  })

  it('calls correct paths for stock adjustment lifecycle', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 10, status: 'DRAFT' } } as any)

    const adj = await createStockAdjustment({ outletId: 101, ingredientId: 200 } as any)
    expect(postSpy).toHaveBeenCalledWith('/stock-adjustments', { outletId: 101, ingredientId: 200 })
    expect(adj.id).toBe(10)

    await postStockAdjustment(10, 'key-adj-1')
    expect(postSpy).toHaveBeenCalledWith('/stock-adjustments/10/post', null, {
      headers: { 'Idempotency-Key': 'key-adj-1' },
    })

    await cancelStockAdjustment(10)
    expect(postSpy).toHaveBeenCalledWith('/stock-adjustments/10/cancel')
  })

  it('calls correct paths for waste record lifecycle', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 20, status: 'DRAFT' } } as any)

    await createWasteRecord({ outletId: 101, ingredientId: 200, qty: 2 } as any)
    expect(postSpy).toHaveBeenCalledWith('/waste-records', { outletId: 101, ingredientId: 200, qty: 2 })

    await postWasteRecord(20, 'key-waste-1')
    expect(postSpy).toHaveBeenCalledWith('/waste-records/20/post', null, {
      headers: { 'Idempotency-Key': 'key-waste-1' },
    })

    await cancelWasteRecord(20)
    expect(postSpy).toHaveBeenCalledWith('/waste-records/20/cancel')
  })

  it('calls correct paths for stock count session lifecycle', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 30, status: 'DRAFT' } } as any)
    const putSpy = vi.spyOn(gatewayClient, 'put').mockResolvedValue({ data: { id: 30, status: 'COUNTING' } } as any)

    await createStockCountSession({ outletId: 101, ingredientIds: [200] } as any)
    expect(postSpy).toHaveBeenCalledWith('/stock-count-sessions', { outletId: 101, ingredientIds: [200] })

    await startStockCountSession(30)
    expect(postSpy).toHaveBeenCalledWith('/stock-count-sessions/30/start')

    await updateStockCountLines(30, { lines: [{ ingredientId: 200, actualQty: 22 }] })
    expect(putSpy).toHaveBeenCalledWith('/stock-count-sessions/30/lines', {
      lines: [{ ingredientId: 200, actualQty: 22 }],
    })

    await postStockCountSession(30, 'key-count-1')
    expect(postSpy).toHaveBeenCalledWith('/stock-count-sessions/30/post', null, {
      headers: { 'Idempotency-Key': 'key-count-1' },
    })

    await cancelStockCountSession(30)
    expect(postSpy).toHaveBeenCalledWith('/stock-count-sessions/30/cancel')
  })
})
