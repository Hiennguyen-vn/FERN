import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { financeApi } from '../api/finance.api'
import type {
  FinancePayrollFilters,
  MarkPayrollPaidPayload,
  PutNumberingRulePayload,
  PutSystemPolicyPayload,
  ReviewPayrollRunPayload,
} from '../model/finance.types'
import {
  clearRecentFinancePaymentRequests,
  loadRecentFinancePaymentRequests,
  saveRecentFinancePaymentRequest,
} from '../services/recentPaymentRequests.service'

interface QueryOptions {
  enabled?: boolean
}

const KEYS = {
  suppliers: ['finance', 'suppliers'] as const,
  paymentRequests: (params: { supplierId?: number; outletId?: number; status?: string; limit?: number }) =>
    ['finance', 'payment-requests', 'list', params] as const,
  paymentRequest: (invoiceId: number) => ['finance', 'payment-requests', invoiceId] as const,
  payrollRuns: (filters: FinancePayrollFilters) => ['finance', 'payroll-runs', filters.regionId ?? 'all-regions'] as const,
  payrollRun: (runId: number) => ['finance', 'payroll-runs', runId] as const,
}

export function useFinanceSuppliers(options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.suppliers,
    queryFn: financeApi.listSuppliers,
    enabled: options.enabled ?? true,
  })
}

export function useFinanceSupplier(supplierId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.suppliers,
    queryFn: financeApi.listSuppliers,
    select: (suppliers) => suppliers.find((supplier) => supplier.id === supplierId) ?? null,
    enabled: (options.enabled ?? true) && supplierId > 0,
  })
}

export function usePaymentRequest(invoiceId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.paymentRequest(invoiceId),
    queryFn: () => financeApi.getPaymentRequest(invoiceId),
    enabled: (options.enabled ?? true) && invoiceId > 0,
  })
}

export function usePaymentRequests(
  params: { supplierId?: number; outletId?: number; status?: string; limit?: number },
  options: QueryOptions = {},
) {
  return useQuery({
    queryKey: KEYS.paymentRequests(params),
    queryFn: () => financeApi.listPaymentRequests(params),
    enabled: options.enabled ?? true,
  })
}

export function useRecentPaymentRequests() {
  const [items, setItems] = useState(() => loadRecentFinancePaymentRequests())

  return useMemo(
    () => ({
      items,
      clear() {
        clearRecentFinancePaymentRequests()
        setItems([])
      },
      refresh() {
        setItems(loadRecentFinancePaymentRequests())
      },
      save: saveRecentFinancePaymentRequest,
    }),
    [items],
  )
}

export function usePayrollApprovalQueue(filters: FinancePayrollFilters = {}, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.payrollRuns(filters),
    queryFn: () => financeApi.listPayrollRuns(filters),
    enabled: options.enabled ?? true,
  })
}

export function usePayrollRun(runId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.payrollRun(runId),
    queryFn: () => financeApi.getPayrollRun(runId),
    enabled: (options.enabled ?? true) && runId > 0,
  })
}

function invalidatePayrollRunQueries(queryClient: ReturnType<typeof useQueryClient>, runId: number) {
  void queryClient.invalidateQueries({ queryKey: ['finance', 'payroll-runs'] })
  void queryClient.invalidateQueries({ queryKey: KEYS.payrollRun(runId) })
  void queryClient.invalidateQueries({ queryKey: ['hr', 'payroll-runs'] })
  void queryClient.invalidateQueries({ queryKey: ['hr', 'payroll-runs', runId] })
  void queryClient.invalidateQueries({ queryKey: ['reports', 'payroll'] })
}

export function useApprovePayrollRun() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['finance', 'payroll-runs', 'approve'],
    mutationFn: ({ payload, runId }: { payload?: ReviewPayrollRunPayload; runId: number }) =>
      financeApi.approvePayrollRun(runId, payload),
    onSuccess: (run) => invalidatePayrollRunQueries(queryClient, run.id),
  })
}

export function useRejectPayrollRun() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['finance', 'payroll-runs', 'reject'],
    mutationFn: ({ payload, runId }: { payload?: ReviewPayrollRunPayload; runId: number }) =>
      financeApi.rejectPayrollRun(runId, payload),
    onSuccess: (run) => invalidatePayrollRunQueries(queryClient, run.id),
  })
}

export function useCancelPayrollRun() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['finance', 'payroll-runs', 'cancel'],
    mutationFn: ({ payload, runId }: { payload?: ReviewPayrollRunPayload; runId: number }) =>
      financeApi.cancelPayrollRun(runId, payload),
    onSuccess: (run) => invalidatePayrollRunQueries(queryClient, run.id),
  })
}

export function useMarkPayrollPaid() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['finance', 'payroll-runs', 'mark-paid'],
    mutationFn: ({ payload, runId }: { payload: MarkPayrollPaidPayload; runId: number }) =>
      financeApi.markPayrollPaid(runId, payload),
    onSuccess: (run) => invalidatePayrollRunQueries(queryClient, run.id),
  })
}

// ── Finance Config ────────────────────────────────────────────────────────────
export function useNumberingRule(documentType: string, options: QueryOptions = {}) {
  return useQuery({
    queryKey: ['finance', 'config', 'numbering-rules', documentType],
    queryFn: () => financeApi.getNumberingRule(documentType),
    enabled: (options.enabled ?? true) && !!documentType,
  })
}

export function usePutNumberingRule(documentType: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: PutNumberingRulePayload) => financeApi.putNumberingRule(documentType, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['finance', 'config', 'numbering-rules'] })
    },
  })
}

export function useSystemPolicy(policyKey: string, options: QueryOptions = {}) {
  return useQuery({
    queryKey: ['finance', 'config', 'system-policies', policyKey],
    queryFn: () => financeApi.getSystemPolicy(policyKey),
    enabled: (options.enabled ?? true) && !!policyKey,
  })
}

export function usePutSystemPolicy(policyKey: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: PutSystemPolicyPayload) => financeApi.putSystemPolicy(policyKey, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['finance', 'config', 'system-policies'] })
    },
  })
}
