import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createSupplierInvoice,
  getSupplierInvoice,
  listSupplierInvoices,
  supplierInvoiceAction,
} from '../api/procurement.api'
import type { CreateSupplierInvoicePayload } from '../model/procurement.types'

export function useSupplierInvoices(params?: { supplierId?: number; outletId?: number; status?: string; limit?: number }) {
  return useQuery({
    queryKey: ['procurement', 'supplier-invoices', 'list', params],
    queryFn: () => listSupplierInvoices(params),
  })
}

export function useSupplierInvoice(id: number | null) {
  return useQuery({
    enabled: id !== null,
    queryKey: ['procurement', 'supplier-invoices', id],
    queryFn: () => getSupplierInvoice(id as number),
  })
}

export function useCreateSupplierInvoice() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['procurement', 'supplier-invoices', 'create'],
    mutationFn: (payload: CreateSupplierInvoicePayload) => createSupplierInvoice(payload),
    onSuccess: (invoice) => {
      void queryClient.invalidateQueries({ queryKey: ['procurement', 'supplier-invoices', 'list'] })
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payment-requests'] })
      queryClient.setQueryData(['procurement', 'supplier-invoices', invoice.id], invoice)
    },
  })
}

export function useSupplierInvoiceAction() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['procurement', 'supplier-invoices', 'action'],
    mutationFn: ({ action, id }: { action: 'approve' | 'dispute'; id: number }) =>
      supplierInvoiceAction(id, action),
    onSuccess: (data) => {
      void queryClient.invalidateQueries({
        queryKey: ['procurement', 'supplier-invoices', data.id],
      })
      void queryClient.invalidateQueries({
        queryKey: ['procurement', 'supplier-invoices', 'list'],
      })
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payment-requests'] })
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payment-requests', data.id] })
    },
  })
}
