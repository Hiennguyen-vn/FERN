import { useMutation, useQuery } from '@tanstack/react-query'
import { generateIdempotencyKey } from '@core/api/idempotency'
import { createSupplierPayment, listSupplierPayments } from '../api/procurement.api'
import type { CreateSupplierPaymentPayload } from '../model/procurement.types'

export function useSupplierPayments() {
  return useQuery({
    queryKey: ['procurement', 'supplier-payments'],
    queryFn: () => listSupplierPayments(),
  })
}

export function useCreateSupplierPayment() {
  return useMutation({
    mutationKey: ['procurement', 'supplier-payments', 'create'],
    mutationFn: (payload: CreateSupplierPaymentPayload) =>
      createSupplierPayment(payload, generateIdempotencyKey()),
  })
}
