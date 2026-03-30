import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'
import { posQueryKeys } from '../api/pos.queries'
import { syncPendingPosActions } from './sync.service'
import { selectPosQueueCounts, usePosQueueStore } from './posQueue.store'

export function useOfflineQueue() {
  const queryClient = useQueryClient()
  const isOnline = useNetworkStatus()
  const actions = usePosQueueStore((state) => state.actions)
  const isSyncing = usePosQueueStore((state) => state.isSyncing)
  const lastSyncSummary = usePosQueueStore((state) => state.lastSyncSummary)
  const queueCounts = selectPosQueueCounts(actions)

  async function flushQueue() {
    return syncPendingPosActions({
      onPaymentSettled: (_action, order) => {
        queryClient.setQueryData(posQueryKeys.order(order.id), order)
        void queryClient.invalidateQueries({ queryKey: ['pos', 'sessions'] })
      },
    })
  }

  useEffect(() => {
    if (!isOnline) {
      return
    }

    if (queueCounts.pendingCount === 0 && queueCounts.retryableFailedCount === 0) {
      return
    }

    void flushQueue()
  }, [isOnline, queryClient, queueCounts.pendingCount, queueCounts.retryableFailedCount])

  return {
    ...queueCounts,
    actions,
    flushQueue,
    isOnline,
    isSyncing,
    lastSyncSummary,
  }
}
