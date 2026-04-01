import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createPurchaseOrder, getPurchaseOrder, listPurchaseOrders, purchaseOrderAction } from '../api/procurement.api'
import type { CreatePurchaseOrderPayload } from '../model/procurement.types'

export function usePurchaseOrders(params?: { outletId?: number; supplierId?: number; status?: string; limit?: number }) {
  return useQuery({
    queryKey: ['procurement', 'purchase-orders', 'list', params],
    queryFn: () => listPurchaseOrders(params),
  })
}

export function usePurchaseOrder(id: number | null) {
  return useQuery({
    enabled: id !== null,
    queryKey: ['procurement', 'purchase-orders', id],
    queryFn: () => getPurchaseOrder(id as number),
  })
}

export function useCreatePurchaseOrder() {
  return useMutation({
    mutationKey: ['procurement', 'purchase-orders', 'create'],
    mutationFn: (payload: CreatePurchaseOrderPayload) => createPurchaseOrder(payload),
  })
}

export function usePurchaseOrderAction() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['procurement', 'purchase-orders', 'action'],
    mutationFn: ({ action, id }: { action: 'submit' | 'approve' | 'issue' | 'cancel'; id: number }) =>
      purchaseOrderAction(id, action),
    onSuccess: (data) => {
      void queryClient.invalidateQueries({
        queryKey: ['procurement', 'purchase-orders', data.id],
      })
    },
  })
}
