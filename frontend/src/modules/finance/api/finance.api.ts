import { gatewayClient } from '@core/api/gatewayClient'
import type {
  FinancePaymentRequest,
  FinancePayrollFilters,
  FinancePayrollRun,
  FinanceSupplier,
  MarkPayrollPaidPayload,
  ReviewPayrollRunPayload,
} from '../model/finance.types'

export const financeApi = {
  listSuppliers() {
    return gatewayClient.get<FinanceSupplier[]>('/suppliers').then((response) => response.data)
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
}
