import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { AddSalePaymentPayload } from '../model/pos.types'

export type PosQueuedActionType = 'ADD_SALE_PAYMENT'
export type PosQueuedActionStatus = 'PENDING' | 'RETRYING' | 'FAILED'

export interface PosQueuedPaymentAction {
  queueId: string
  actionType: PosQueuedActionType
  status: PosQueuedActionStatus
  retryable: boolean
  idempotencyKey: string
  orderId: number
  payload: AddSalePaymentPayload
  createdAt: string
  updatedAt: string
  attempts: number
  lastError: string | null
}

export interface PosQueueSyncSummary {
  failed: number
  processed: number
  startedAt: string
  succeeded: number
}

interface PosQueueState {
  actions: PosQueuedPaymentAction[]
  isSyncing: boolean
  lastSyncSummary: PosQueueSyncSummary | null
  clear: () => void
  enqueuePaymentAction: (input: {
    idempotencyKey: string
    orderId: number
    payload: AddSalePaymentPayload
    retryable?: boolean
  }) => PosQueuedPaymentAction
  markFailed: (queueId: string, errorMessage: string, retryable: boolean) => void
  markRetrying: (queueId: string) => void
  removeAction: (queueId: string) => void
  setLastSyncSummary: (summary: PosQueueSyncSummary | null) => void
  setSyncing: (isSyncing: boolean) => void
}

function toSafeActions(actions: PosQueuedPaymentAction[] | undefined) {
  return (actions ?? []).map((action) => ({
    ...action,
    status: action.status === 'RETRYING' ? 'PENDING' : action.status,
  }))
}

export function selectPosQueueCounts(actions: PosQueuedPaymentAction[]) {
  const pendingCount = actions.filter((action) => action.status === 'PENDING').length
  const retryingCount = actions.filter((action) => action.status === 'RETRYING').length
  const failedCount = actions.filter((action) => action.status === 'FAILED').length
  const retryableFailedCount = actions.filter((action) => action.status === 'FAILED' && action.retryable).length

  return {
    failedCount,
    pendingCount,
    retryableFailedCount,
    retryingCount,
    totalCount: actions.length,
  }
}

export function selectQueuedPaymentsForOrder(actions: PosQueuedPaymentAction[], orderId: number) {
  return actions.filter((action) => action.orderId === orderId)
}

export function selectRetryablePosActions(actions: PosQueuedPaymentAction[]) {
  return actions.filter((action) => action.status === 'PENDING' || (action.status === 'FAILED' && action.retryable))
}

export const usePosQueueStore = create<PosQueueState>()(
  persist(
    (set) => ({
      actions: [],
      isSyncing: false,
      lastSyncSummary: null,
      clear: () =>
        set({
          actions: [],
          isSyncing: false,
          lastSyncSummary: null,
        }),
      enqueuePaymentAction: ({ idempotencyKey, orderId, payload, retryable = true }) => {
        const nextAction: PosQueuedPaymentAction = {
          queueId: idempotencyKey,
          actionType: 'ADD_SALE_PAYMENT',
          attempts: 0,
          createdAt: new Date().toISOString(),
          idempotencyKey,
          lastError: null,
          orderId,
          payload,
          retryable,
          status: 'PENDING',
          updatedAt: new Date().toISOString(),
        }

        set((state) => ({
          actions: [...state.actions.filter((action) => action.queueId !== nextAction.queueId), nextAction].sort((left, right) =>
            left.createdAt.localeCompare(right.createdAt),
          ),
          lastSyncSummary: null,
        }))

        return nextAction
      },
      markFailed: (queueId, errorMessage, retryable) =>
        set((state) => ({
          actions: state.actions.map((action) =>
            action.queueId === queueId
              ? {
                  ...action,
                  lastError: errorMessage,
                  retryable,
                  status: 'FAILED',
                  updatedAt: new Date().toISOString(),
                }
              : action,
          ),
        })),
      markRetrying: (queueId) =>
        set((state) => ({
          actions: state.actions.map((action) =>
            action.queueId === queueId
              ? {
                  ...action,
                  attempts: action.attempts + 1,
                  status: 'RETRYING',
                  updatedAt: new Date().toISOString(),
                }
              : action,
          ),
        })),
      removeAction: (queueId) =>
        set((state) => ({
          actions: state.actions.filter((action) => action.queueId !== queueId),
        })),
      setLastSyncSummary: (lastSyncSummary) => set({ lastSyncSummary }),
      setSyncing: (isSyncing) => set({ isSyncing }),
    }),
    {
      merge: (persistedState, currentState) => {
        const nextState = (persistedState ?? {}) as Partial<PosQueueState>

        return {
          ...currentState,
          ...nextState,
          actions: toSafeActions(nextState.actions),
          isSyncing: false,
          lastSyncSummary: null,
        }
      },
      name: 'fern_pos_queue_storage',
      partialize: (state) => ({
        actions: state.actions,
      }),
      storage: createJSONStorage(() => localStorage),
    },
  ),
)
