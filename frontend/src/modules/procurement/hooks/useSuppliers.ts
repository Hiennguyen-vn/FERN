import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { activateSupplier, createSupplier, listSuppliers, updateSupplier } from '../api/procurement.api'
import type { SupplierUpsertPayload } from '../model/procurement.types'

interface QueryOptions {
  enabled?: boolean
}

export function useSuppliers(options: QueryOptions = {}) {
  return useQuery({
    enabled: options.enabled ?? true,
    queryKey: ['procurement', 'suppliers'],
    queryFn: listSuppliers,
  })
}

export function useCreateSupplier() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: SupplierUpsertPayload) => createSupplier(payload),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['procurement', 'suppliers'] })
      void qc.invalidateQueries({ queryKey: ['finance', 'suppliers'] })
    },
  })
}

export function useUpdateSupplier() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: SupplierUpsertPayload }) =>
      updateSupplier(id, payload),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['procurement', 'suppliers'] })
      void qc.invalidateQueries({ queryKey: ['finance', 'suppliers'] })
    },
  })
}

export function useActivateSupplier() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => activateSupplier(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['procurement', 'suppliers'] })
      void qc.invalidateQueries({ queryKey: ['finance', 'suppliers'] })
    },
  })
}
