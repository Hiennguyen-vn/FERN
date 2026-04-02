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

export const financeApi = {
  listSuppliers() {
    return gatewayClient.get<FinanceSupplier[]>('/suppliers').then((response) => response.data)
  },

  listPaymentRequests(params?: { supplierId?: number; outletId?: number; status?: string; limit?: number }) {
    return gatewayClient.get<FinancePaymentRequest[]>('/supplier-invoices', { params }).then((response) => response.data)
  },

  getPaymentRequest(invoiceId: number) {
    return gatewayClient.get<FinancePaymentRequest>(`/supplier-invoices/${invoiceId}`).then((response) => response.data)
  },

  listPayrollRuns(filters: FinancePayrollFilters = {}) {
    return gatewayClient
      .get<FinancePayrollRun[]>('/payroll-runs', {
        params: filters.regionId ? { regionId: filters.regionId } : undefined,
      })
      .then((response) => response.data)
  },

  getPayrollRun(runId: number) {
    return gatewayClient.get<FinancePayrollRun>(`/payroll-runs/${runId}`).then((response) => response.data)
  },

  submitPayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
    return gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/submit`, payload).then((response) => response.data)
  },

  approvePayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
    return gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/approve`, payload).then((response) => response.data)
  },

  rejectPayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
    return gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/reject`, payload).then((response) => response.data)
  },

  cancelPayrollRun(runId: number, payload: ReviewPayrollRunPayload = {}) {
    return gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/cancel`, payload).then((response) => response.data)
  },

  markPayrollPaid(runId: number, payload: MarkPayrollPaidPayload) {
    return gatewayClient.post<FinancePayrollRun>(`/payroll-runs/${runId}/mark-paid`, payload).then((response) => response.data)
  },

  // ── Finance Config ─────────────────────────────────────────────────────────
  getNumberingRule(documentType: string) {
    return gatewayClient.get<NumberingRule>(`/finance-config/numbering-rules/${encodeURIComponent(documentType)}`).then((r) => r.data)
  },

  putNumberingRule(documentType: string, payload: PutNumberingRulePayload) {
    return gatewayClient.put<NumberingRule>(`/finance-config/numbering-rules/${encodeURIComponent(documentType)}`, payload).then((r) => r.data)
  },

  getSystemPolicy(policyKey: string) {
    return gatewayClient.get<SystemPolicy>(`/finance-config/system-policies/${encodeURIComponent(policyKey)}`).then((r) => r.data)
  },

  putSystemPolicy(policyKey: string, payload: PutSystemPolicyPayload) {
    return gatewayClient.put<SystemPolicy>(`/finance-config/system-policies/${encodeURIComponent(policyKey)}`, payload).then((r) => r.data)
  },
}
