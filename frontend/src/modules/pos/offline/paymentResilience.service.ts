import { generateIdempotencyKey } from '@core/api/idempotency'
import { addSalePayment } from '../api/pos.api'
import type { AddSalePaymentPayload, SaleOrder } from '../model/pos.types'
import { isRetryablePosQueueError } from './offlinePolicy.service'
import { usePosQueueStore, type PosQueuedPaymentAction } from './posQueue.store'

export type SubmitSalePaymentResult =
  | {
      idempotencyKey: string
      kind: 'queued'
      queuedAction: PosQueuedPaymentAction
    }
  | {
      idempotencyKey: string
      kind: 'success'
      order: SaleOrder
    }

export async function submitSalePaymentWithResilience(options: {
  idempotencyKey?: string
  isOnline: boolean
  orderId: number
  payload: AddSalePaymentPayload
}): Promise<SubmitSalePaymentResult> {
  const idempotencyKey = options.idempotencyKey ?? generateIdempotencyKey()

  if (!options.isOnline) {
    const queuedAction = usePosQueueStore.getState().enqueuePaymentAction({
      idempotencyKey,
      orderId: options.orderId,
      payload: options.payload,
    })

    return {
      idempotencyKey,
      kind: 'queued',
      queuedAction,
    }
  }

  try {
    const order = await addSalePayment(options.orderId, options.payload, idempotencyKey)

    return {
      idempotencyKey,
      kind: 'success',
      order,
    }
  } catch (error) {
    if (!isRetryablePosQueueError(error)) {
      throw error
    }

    const queuedAction = usePosQueueStore.getState().enqueuePaymentAction({
      idempotencyKey,
      orderId: options.orderId,
      payload: options.payload,
    })

    return {
      idempotencyKey,
      kind: 'queued',
      queuedAction,
    }
  }
}
