import { useQueries } from '@tanstack/react-query'
import { listPosSessions, listSessionOrders } from '../api/pos.api'
import { computeShiftStats } from '../components/PosSessionShiftSummary'

/**
 * For a given outletId, fetches the most recent sessions (today or last known)
 * and their orders, then returns aggregated shift stats.
 */
export function useOutletTodayStats(outletIds: number[], enabled = true) {
  // Step 1: fetch sessions for each outlet (limit=1 to get most recent OPEN or latest)
  const sessionQueries = useQueries({
    queries: outletIds.map((outletId) => ({
      enabled: enabled && outletId > 0,
      queryKey: ['pos', 'outlet-sessions', outletId],
      queryFn: () => listPosSessions({ outletId, status: '' }),
      staleTime: 30_000,
    })),
  })

  // Collect all the top sessions (most recent per outlet)
  const topSessions = sessionQueries
    .flatMap((q) => q.data ?? [])
    .reduce<Record<number, { sessionId: number; outletId: number; status: string; currencyCode: string }>>((acc, session) => {
      const outletId = session.outletId
      // Keep the session with the highest id per outlet (most recent)
      if (!acc[outletId] || session.id > acc[outletId].sessionId) {
        acc[outletId] = {
          sessionId: session.id,
          outletId,
          status: session.status,
          currencyCode: session.currencyCode,
        }
      }
      return acc
    }, {})

  const sessionList = Object.values(topSessions)

  // Step 2: fetch orders for each top session
  const orderQueries = useQueries({
    queries: sessionList.map(({ sessionId }) => ({
      enabled: enabled && sessionId > 0,
      queryKey: ['pos', 'session-orders', sessionId],
      queryFn: () => listSessionOrders(sessionId),
      staleTime: 30_000,
    })),
  })

  // Build per-outlet stats
  const outletStats = sessionList.map((session, index) => {
    const orders = orderQueries[index]?.data ?? []
    const stats = computeShiftStats(orders)
    return {
      outletId: session.outletId,
      sessionId: session.sessionId,
      sessionStatus: session.status,
      currencyCode: session.currencyCode,
      isLoading: orderQueries[index]?.isLoading ?? true,
      ...stats,
    }
  })

  const isLoading = sessionQueries.some((q) => q.isLoading) || orderQueries.some((q) => q.isLoading)

  return { outletStats, isLoading }
}
