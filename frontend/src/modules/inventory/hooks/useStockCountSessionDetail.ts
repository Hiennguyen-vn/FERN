import { useQuery } from '@tanstack/react-query'
import { getStockCountSession } from '../api/inventory.api'

export function useStockCountSessionDetail(sessionId: number | null) {
  return useQuery({
    enabled: sessionId !== null && sessionId > 0,
    queryKey: ['inventory', 'stock-count-session', sessionId],
    queryFn: () => getStockCountSession(sessionId as number),
  })
}
