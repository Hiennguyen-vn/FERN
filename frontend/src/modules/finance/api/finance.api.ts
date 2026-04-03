import { gatewayClient } from '@core/api/gatewayClient'
import type {
  FinancePaymentRequest,
  FinancePayrollFilters,
  FinancePayrollRun,
  FinanceSupplier,
  MarkPayrollPaidPayload,
  NumberingRule,
  PutNumberingRulePayload,
  PutSystemPolicyPayload,
  ReviewPayrollRunPayload,
  SystemPolicy,
} from '../model/finance.types'

/** Named functions (preferred); `financeApi` groups the same calls for query hooks. */
export async function listFinanceSuppliers() {
  const { data } = await gatewayClient.get<FinanceSupplier[]>('/suppliers')
  return data
}

export async function listFinancePaymentRequests(params?: {
  supplierId?: number
  outletId?: number
  status?: string
  limit?: number
}) {
  const { data } = await gatewayClient.get<FinancePaymentRequest[]>('/supplier-invoices', { params })
  return data
}

export async function getFinancePaymentRequest(invoiceId: number) {
  const { data } = await gatewayClient.get<FinancePaymentRequest>(`/supplier-invoices/${invoiceId}`)
  return data
}

export async function listFinancePayrollRuns(filters: FinancePayrollFilters = {}) {
  const { data } = await gatewayClient.get<FinancePayrollRun[]>('/payroll-runs', {
    params: filters.regionId ? { regionId: filters.regionId } : undefined,
  })
  return data
}

export async function getFinancePayrollRun(runId: number) {
  const { data } = await gatewayClient.get<FinancePayrollRun>(`/payroll-runs/${runId}`)
  return data
}

export async function submitFinancePayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
  const { data } = await gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/submit`, payload)
  return data
}

export async function approveFinancePayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
  const { data } = await gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/approve`, payload)
  return data
}

export async function rejectFinancePayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
  const { data } = await gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/reject`, payload)
  return data
}

export async function cancelFinancePayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
  const { data } = await gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/cancel`, payload)
  return data
}

export async function markFinancePayrollPaid(runId: number, payload: MarkPayrollPaidPayload) {
  const { data } = await gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/mark-paid`, payload)
  return data
}

export async function getFinanceNumberingRule(documentType: string) {
  const { data } = await gatewayClient.get<NumberingRule>(
    `/finance-config/numbering-rules/${encodeURIComponent(documentType)}`,
  )
  return data
}

export async function putFinanceNumberingRule(documentType: string, payload: PutNumberingRulePayload) {
  const { data } = await gatewayClient.put<NumberingRule>(
    `/finance-config/numbering-rules/${encodeURIComponent(documentType)}`,
    payload,
  )
  return data
}

export async function getFinanceSystemPolicy(policyKey: string) {
  const { data } = await gatewayClient.get<SystemPolicy>(
    `/finance-config/system-policies/${encodeURIComponent(policyKey)}`,
  )
  return data
}

export async function putFinanceSystemPolicy(policyKey: string, payload: PutSystemPolicyPayload) {
  const { data } = await gatewayClient.put<SystemPolicy>(
    `/finance-config/system-policies/${encodeURIComponent(policyKey)}`,
    payload,
  )
  return data
}

/** Stable object surface for React Query `queryFn` / `mutationFn` registration. */
export const financeApi = {
  listSuppliers: listFinanceSuppliers,
  listPaymentRequests: listFinancePaymentRequests,
  getPaymentRequest: getFinancePaymentRequest,
  listPayrollRuns: listFinancePayrollRuns,
  getPayrollRun: getFinancePayrollRun,
  submitPayrollRun: submitFinancePayrollRun,
  approvePayrollRun: approveFinancePayrollRun,
  rejectPayrollRun: rejectFinancePayrollRun,
  cancelPayrollRun: cancelFinancePayrollRun,
  markPayrollPaid: markFinancePayrollPaid,
  getNumberingRule: getFinanceNumberingRule,
  putNumberingRule: putFinanceNumberingRule,
  getSystemPolicy: getFinanceSystemPolicy,
  putSystemPolicy: putFinanceSystemPolicy,
}
