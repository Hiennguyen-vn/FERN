import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'
import {
  cancelSaleOrder,
  completeSaleOrder,
  createSaleOrder,
  getSaleOrder,
  updateSaleOrder,
} from '../api/pos.api'
import { posMutationKeys, posQueryKeys } from '../api/pos.queries'
import type { AddSalePaymentPayload, CreateSaleOrderPayload, UpdateSaleOrderPayload } from '../model/pos.types'
import { assertPosActionOnline } from '../offline/offlinePolicy.service'
import { submitSalePaymentWithResilience } from '../offline/paymentResilience.service'

export function usePosOrder(orderId: number | null, enabled = true) {
  return useQuery({
    enabled: orderId !== null && enabled,
    queryKey: orderId !== null ? posQueryKeys.order(orderId) : ['pos', 'order', 'disabled'],
    queryFn: () => getSaleOrder(orderId as number),
  })
}

export function useCreatePosOrder() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: posMutationKeys.createOrder,
    mutationFn: (payload: CreateSaleOrderPayload) => createSaleOrder(payload),
    onSuccess: (order) => {
      queryClient.setQueryData(posQueryKeys.order(order.id), order)
      void queryClient.invalidateQueries({ queryKey: ['pos', 'sessions'] })
    },
  })
}

export function useUpdatePosOrder() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: posMutationKeys.updateOrder,
    mutationFn: ({ orderId, payload }: { orderId: number; payload: UpdateSaleOrderPayload }) =>
      updateSaleOrder(orderId, payload),
    onSuccess: (order) => {
      queryClient.setQueryData(posQueryKeys.order(order.id), order)
    },
  })
}

export function useAddSalePayment() {
  const queryClient = useQueryClient()
  const isOnline = useNetworkStatus()

  return useMutation({
    mutationKey: posMutationKeys.addPayment,
    mutationFn: ({ orderId, payload }: { orderId: number; payload: AddSalePaymentPayload }) =>
      submitSalePaymentWithResilience({
        isOnline,
        orderId,
        payload,
      }),
    onSuccess: (result) => {
      if (result.kind === 'success') {
        queryClient.setQueryData(posQueryKeys.order(result.order.id), result.order)
      }
    },
  })
}

export function useCompletePosOrder() {
  const queryClient = useQueryClient()
  const isOnline = useNetworkStatus()

  return useMutation({
    mutationKey: posMutationKeys.completeOrder,
    mutationFn: (orderId: number) => {
      assertPosActionOnline('COMPLETE_ORDER', isOnline)
      return completeSaleOrder(orderId)
    },
    onSuccess: (order) => {
      queryClient.setQueryData(posQueryKeys.order(order.id), order)
      void queryClient.invalidateQueries({ queryKey: ['pos', 'sessions'] })
    },
  })
}

export function useCancelPosOrder() {
  const queryClient = useQueryClient()
  const isOnline = useNetworkStatus()

  return useMutation({
    mutationKey: posMutationKeys.cancelOrder,
    mutationFn: (orderId: number) => {
      assertPosActionOnline('CANCEL_ORDER', isOnline)
      return cancelSaleOrder(orderId)
    },
    onSuccess: (order) => {
      queryClient.setQueryData(posQueryKeys.order(order.id), order)
      void queryClient.invalidateQueries({ queryKey: ['pos', 'sessions'] })
    },
  })
}
