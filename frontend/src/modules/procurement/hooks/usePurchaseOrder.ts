import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createPurchaseOrder, getPurchaseOrder, listPurchaseOrders, purchaseOrderAction, updatePurchaseOrder } from '../api/procurement.api'
import type { CreatePurchaseOrderPayload, UpdatePurchaseOrderPayload } from '../model/procurement.types'

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
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['procurement', 'purchase-orders', 'create'],
    mutationFn: (payload: CreatePurchaseOrderPayload) => createPurchaseOrder(payload),
    onSuccess: (purchaseOrder) => {
      void queryClient.invalidateQueries({ queryKey: ['procurement', 'purchase-orders', 'list'] })
      queryClient.setQueryData(['procurement', 'purchase-orders', purchaseOrder.id], purchaseOrder)
    },
  })
}

export function useUpdatePurchaseOrder() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['procurement', 'purchase-orders', 'update'],
    mutationFn: ({ id, payload }: { id: number; payload: UpdatePurchaseOrderPayload }) =>
      updatePurchaseOrder(id, payload),
    onSuccess: (data) => {
      void queryClient.invalidateQueries({
        queryKey: ['procurement', 'purchase-orders', data.id],
      })
      void queryClient.invalidateQueries({
        queryKey: ['procurement', 'purchase-orders', 'list'],
      })
    },
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
      void queryClient.invalidateQueries({
        queryKey: ['procurement', 'purchase-orders', 'list'],
      })
    },
  })
}
