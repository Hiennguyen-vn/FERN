import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'
import {
  closePosSession,
  getPosSession,
  listPosSessions,
  openPosSession,
  reconcilePosSession,
} from '../api/pos.api'
import { posMutationKeys, posQueryKeys } from '../api/pos.queries'
import type { OpenPosSessionPayload, PosSessionFilters, ReconcilePosSessionPayload } from '../model/pos.types'
import { assertPosActionOnline } from '../offline/offlinePolicy.service'

export function usePosSessions(filters: PosSessionFilters | null, enabled = true) {
  return useQuery({
    enabled: filters !== null && enabled,
    queryKey: filters ? posQueryKeys.sessions(filters) : ['pos', 'sessions', 'disabled'],
    queryFn: () => listPosSessions(filters as PosSessionFilters),
  })
}

export function usePosSession(sessionId: number | null, enabled = true) {
  return useQuery({
    enabled: sessionId !== null && enabled,
    queryKey: sessionId !== null ? posQueryKeys.session(sessionId) : ['pos', 'session', 'disabled'],
    queryFn: () => getPosSession(sessionId as number),
  })
}

export function useOpenPosSession() {
  const queryClient = useQueryClient()
  const isOnline = useNetworkStatus()

  return useMutation({
    mutationKey: posMutationKeys.openSession,
    mutationFn: (payload: OpenPosSessionPayload) => {
      assertPosActionOnline('OPEN_SESSION', isOnline)
      return openPosSession(payload)
    },
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['pos', 'sessions'] })
      queryClient.setQueryData(posQueryKeys.session(result.session.id), result.session)
    },
  })
}

export function useClosePosSession() {
  const queryClient = useQueryClient()
  const isOnline = useNetworkStatus()

  return useMutation({
    mutationKey: posMutationKeys.closeSession,
    mutationFn: (sessionId: number) => {
      assertPosActionOnline('CLOSE_SESSION', isOnline)
      return closePosSession(sessionId)
    },
    onSuccess: (session) => {
      void queryClient.invalidateQueries({ queryKey: ['pos', 'sessions'] })
      queryClient.setQueryData(posQueryKeys.session(session.id), session)
    },
  })
}

export function useReconcilePosSession() {
  const queryClient = useQueryClient()
  const isOnline = useNetworkStatus()

  return useMutation({
    mutationKey: posMutationKeys.reconcileSession,
    mutationFn: ({ payload, sessionId }: { payload: ReconcilePosSessionPayload; sessionId: number }) => {
      assertPosActionOnline('RECONCILE_SESSION', isOnline)
      return reconcilePosSession(sessionId, payload)
    },
    onSuccess: (session) => {
      void queryClient.invalidateQueries({ queryKey: ['pos', 'sessions'] })
      queryClient.setQueryData(posQueryKeys.session(session.id), session)
    },
  })
}
