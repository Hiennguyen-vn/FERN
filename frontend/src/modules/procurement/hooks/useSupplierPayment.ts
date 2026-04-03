import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { generateIdempotencyKey } from '@core/api/idempotency'
import { createSupplierPayment, listSupplierPayments } from '../api/procurement.api'
import type { CreateSupplierPaymentPayload } from '../model/procurement.types'

export function useSupplierPayments(params?: { supplierId?: number; limit?: number }) {
  return useQuery({
    queryKey: ['procurement', 'supplier-payments', params],
    queryFn: () => listSupplierPayments(params),
  })
}

export function useCreateSupplierPayment() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['procurement', 'supplier-payments', 'create'],
    mutationFn: (payload: CreateSupplierPaymentPayload) =>
      createSupplierPayment(payload, generateIdempotencyKey()),
    onSuccess: (payment) => {
      void queryClient.invalidateQueries({ queryKey: ['procurement', 'supplier-payments'] })
      void queryClient.invalidateQueries({ queryKey: ['procurement', 'supplier-invoices'] })
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payment-requests'] })
      for (const allocation of payment.invoiceAllocations) {
        void queryClient.invalidateQueries({ queryKey: ['procurement', 'supplier-invoices', allocation.supplierInvoiceId] })
        void queryClient.invalidateQueries({ queryKey: ['finance', 'payment-requests', allocation.supplierInvoiceId] })
      }
    },
  })
}
