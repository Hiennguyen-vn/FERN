import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createGoodsReceipt, getGoodsReceipt, goodsReceiptAction, listGoodsReceipts } from '../api/procurement.api'
import type { CreateGoodsReceiptPayload } from '../model/procurement.types'

export function useGoodsReceipts(params?: { purchaseOrderId?: number; outletId?: number; status?: string; limit?: number }) {
  return useQuery({
    queryKey: ['procurement', 'goods-receipts', 'list', params],
    queryFn: () => listGoodsReceipts(params),
  })
}

export function useGoodsReceipt(id: number | null) {
  return useQuery({
    enabled: id !== null,
    queryKey: ['procurement', 'goods-receipts', id],
    queryFn: () => getGoodsReceipt(id as number),
  })
}

export function useCreateGoodsReceipt() {
  return useMutation({
    mutationKey: ['procurement', 'goods-receipts', 'create'],
    mutationFn: (payload: CreateGoodsReceiptPayload) => createGoodsReceipt(payload),
  })
}

export function useGoodsReceiptAction() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['procurement', 'goods-receipts', 'action'],
    mutationFn: ({ action, id }: { action: 'receive' | 'post' | 'cancel'; id: number }) =>
      goodsReceiptAction(id, action),
    onSuccess: (data) => {
      void queryClient.invalidateQueries({
        queryKey: ['procurement', 'goods-receipts', data.id],
      })
    },
  })
}
