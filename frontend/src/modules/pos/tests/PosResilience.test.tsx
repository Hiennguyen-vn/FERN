import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AddSalePaymentPayload } from '../model/pos.types'
import { assertPosActionOnline } from '../offline/offlinePolicy.service'
import { submitSalePaymentWithResilience } from '../offline/paymentResilience.service'
import { usePosQueueStore } from '../offline/posQueue.store'
import { retryQueuedPosAction } from '../offline/retry.service'
import { syncPendingPosActions } from '../offline/sync.service'

const posApi = vi.hoisted(() => ({
  addSalePayment: vi.fn(),
}))

vi.mock('../api/pos.api', () => ({
  addSalePayment: posApi.addSalePayment,
}))

const paymentPayload: AddSalePaymentPayload = {
  amount: 150000,
  paymentMethod: 'CASH',
  status: 'SUCCESS',
}

function resetQueueState() {
  usePosQueueStore.getState().clear()
  window.localStorage.removeItem('fern_pos_queue_storage')
}

describe('POS resilience', () => {
  beforeEach(() => {
    posApi.addSalePayment.mockReset()
    resetQueueState()
  })

  afterEach(() => {
    resetQueueState()
  })

  it('enqueues a pending payment action when the POS is offline', async () => {
    const result = await submitSalePaymentWithResilience({
      isOnline: false,
      orderId: 900,
      payload: paymentPayload,
    })

    if (result.kind !== 'queued') {
      throw new Error('Expected payment to be queued while offline')
    }

    expect(result.kind).toBe('queued')
    expect(result.idempotencyKey).toBe(result.queuedAction.idempotencyKey)
    expect(usePosQueueStore.getState().actions).toEqual([
      expect.objectContaining({
        idempotencyKey: result.idempotencyKey,
        orderId: 900,
        retryable: true,
        status: 'PENDING',
      }),
    ])
  })

  it('reuses the same Idempotency-Key when retrying the same queued payment', async () => {
    const queuedResult = await submitSalePaymentWithResilience({
      isOnline: false,
      orderId: 901,
      payload: paymentPayload,
    })

    if (queuedResult.kind !== 'queued') {
      throw new Error('Expected payment to be queued while offline')
    }

    posApi.addSalePayment.mockResolvedValue({
      id: 901,
      orderNumber: 'SO-901',
      payments: [],
    })

    await retryQueuedPosAction(queuedResult.queuedAction)

    expect(posApi.addSalePayment).toHaveBeenCalledWith(901, paymentPayload, queuedResult.idempotencyKey)
    expect(usePosQueueStore.getState().actions).toHaveLength(0)
  })

  it('creates a new Idempotency-Key for a genuinely new payment attempt', async () => {
    const firstResult = await submitSalePaymentWithResilience({
      isOnline: false,
      orderId: 902,
      payload: paymentPayload,
    })
    const secondResult = await submitSalePaymentWithResilience({
      isOnline: false,
      orderId: 902,
      payload: paymentPayload,
    })

    if (firstResult.kind !== 'queued' || secondResult.kind !== 'queued') {
      throw new Error('Expected payment attempts to be queued while offline')
    }

    expect(firstResult.idempotencyKey).not.toBe(secondResult.idempotencyKey)
    expect(usePosQueueStore.getState().actions).toHaveLength(2)
  })

  it('keeps the same Idempotency-Key when an online payment attempt falls back into the queue after a retryable failure', async () => {
    posApi.addSalePayment.mockRejectedValue(new Error('Network offline'))

    const result = await submitSalePaymentWithResilience({
      isOnline: true,
      orderId: 905,
      payload: paymentPayload,
    })

    if (result.kind !== 'queued') {
      throw new Error('Expected retryable payment failure to enqueue the action')
    }

    expect(posApi.addSalePayment).toHaveBeenCalledWith(905, paymentPayload, result.idempotencyKey)
    expect(usePosQueueStore.getState().actions).toEqual([
      expect.objectContaining({
        idempotencyKey: result.idempotencyKey,
        orderId: 905,
        status: 'PENDING',
      }),
    ])
  })

  it('reconnect sync processing flushes queued payments and settles them on success', async () => {
    const queuedResult = await submitSalePaymentWithResilience({
      isOnline: false,
      orderId: 903,
      payload: paymentPayload,
    })

    if (queuedResult.kind !== 'queued') {
      throw new Error('Expected payment to be queued while offline')
    }
    posApi.addSalePayment.mockResolvedValue({
      completedAt: null,
      createdAt: '2026-03-30T12:00:00.000Z',
      currencyCode: 'VND',
      discountAmount: 0,
      id: 903,
      lines: [],
      note: null,
      orderNumber: 'SO-903',
      orderType: 'DINE_IN',
      outletId: 101,
      paymentStatus: 'PAID',
      payments: [],
      posSessionId: 501,
      regionId: 1,
      status: 'OPEN',
      subtotal: 150000,
      taxAmount: 0,
      totalAmount: 150000,
    })

    const summary = await syncPendingPosActions()

    expect(posApi.addSalePayment).toHaveBeenCalledWith(903, paymentPayload, queuedResult.idempotencyKey)
    expect(summary.succeeded).toBe(1)
    expect(usePosQueueStore.getState().actions).toHaveLength(0)
    expect(usePosQueueStore.getState().lastSyncSummary?.succeeded).toBe(1)
  })

  it('keeps a clear failed state when retry does not succeed', async () => {
    const queuedResult = await submitSalePaymentWithResilience({
      isOnline: false,
      orderId: 904,
      payload: paymentPayload,
    })

    if (queuedResult.kind !== 'queued') {
      throw new Error('Expected payment to be queued while offline')
    }

    posApi.addSalePayment.mockRejectedValue(new Error('Gateway timeout'))

    const retryResult = await retryQueuedPosAction(queuedResult.queuedAction)

    expect(retryResult.success).toBe(false)
    expect(usePosQueueStore.getState().actions).toEqual([
      expect.objectContaining({
        idempotencyKey: queuedResult.idempotencyKey,
        lastError: 'Gateway timeout',
        retryable: true,
        status: 'FAILED',
      }),
    ])
  })

  it('blocks unsafe POS lifecycle actions while offline', () => {
    expect(() => assertPosActionOnline('OPEN_SESSION', false)).toThrow('Không thể mở session khi POS đang offline.')
    expect(() => assertPosActionOnline('CLOSE_SESSION', false)).toThrow('Không thể đóng session khi POS đang offline.')
    expect(() => assertPosActionOnline('COMPLETE_ORDER', false)).toThrow('Không thể complete order khi POS đang offline.')
    expect(() => assertPosActionOnline('CANCEL_ORDER', false)).toThrow('Không thể cancel order khi POS đang offline.')
    expect(() => assertPosActionOnline('RECONCILE_SESSION', false)).toThrow(
      'Không thể reconcile session khi POS đang offline.',
    )
  })
})
