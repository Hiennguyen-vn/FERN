import { addSalePayment } from '../api/pos.api'
import type { SaleOrder } from '../model/pos.types'
import { isRetryablePosQueueError, toPosQueueErrorMessage } from './offlinePolicy.service'
import { selectRetryablePosActions, usePosQueueStore, type PosQueuedPaymentAction } from './posQueue.store'

export interface PosQueueRetryCallbacks {
  onPaymentFailed?: (action: PosQueuedPaymentAction, error: unknown) => void
  onPaymentSettled?: (action: PosQueuedPaymentAction, order: SaleOrder) => void
}

export interface PosRetrySummary {
  failed: number
  processed: number
  succeeded: number
}

export async function retryQueuedPosAction(action: PosQueuedPaymentAction, callbacks: PosQueueRetryCallbacks = {}) {
  const queueStore = usePosQueueStore.getState()
  queueStore.markRetrying(action.queueId)

  try {
    const order = await addSalePayment(action.orderId, action.payload, action.idempotencyKey)
    usePosQueueStore.getState().removeAction(action.queueId)
    callbacks.onPaymentSettled?.(action, order)

    return {
      action,
      order,
      success: true as const,
    }
  } catch (error) {
    usePosQueueStore
      .getState()
      .markFailed(action.queueId, toPosQueueErrorMessage(error), isRetryablePosQueueError(error))
    callbacks.onPaymentFailed?.(action, error)

    return {
      action,
      error,
      success: false as const,
    }
  }
}

export async function retryPendingPosActions(callbacks: PosQueueRetryCallbacks = {}): Promise<PosRetrySummary> {
  const actions = selectRetryablePosActions(usePosQueueStore.getState().actions)
  const summary: PosRetrySummary = {
    failed: 0,
    processed: 0,
    succeeded: 0,
  }

  for (const action of actions) {
    const result = await retryQueuedPosAction(action, callbacks)
    summary.processed += 1

    if (result.success) {
      summary.succeeded += 1
    } else {
      summary.failed += 1
    }
  }

  return summary
}
