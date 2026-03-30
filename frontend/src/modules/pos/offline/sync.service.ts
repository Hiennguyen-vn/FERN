import { retryPendingPosActions, type PosQueueRetryCallbacks, type PosRetrySummary } from './retry.service'
import { usePosQueueStore, type PosQueueSyncSummary } from './posQueue.store'

let syncPromise: Promise<PosQueueSyncSummary> | null = null

function toSyncSummary(summary: PosRetrySummary, startedAt: string): PosQueueSyncSummary {
  return {
    ...summary,
    startedAt,
  }
}

export async function syncPendingPosActions(callbacks: PosQueueRetryCallbacks = {}) {
  if (syncPromise) {
    return syncPromise
  }

  const startedAt = new Date().toISOString()
  usePosQueueStore.getState().setSyncing(true)

  syncPromise = retryPendingPosActions(callbacks)
    .then((summary) => {
      const nextSummary = toSyncSummary(summary, startedAt)
      usePosQueueStore.getState().setLastSyncSummary(nextSummary)
      return nextSummary
    })
    .finally(() => {
      usePosQueueStore.getState().setSyncing(false)
      syncPromise = null
    })

  return syncPromise
}
