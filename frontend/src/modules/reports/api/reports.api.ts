import { gatewayClient } from '@core/api/gatewayClient'
import type { PageResponse } from '@core/types/api'
import type {
  InventoryReportFilters,
  ReportInventoryTransactionPage,
  ReportStockBalancePage,
} from '../model/inventoryReport.types'
import type {
  ReportPayrollRunDetail,
  ReportPayrollRunListItem,
  ReportPayrollSummary,
  ReportPayrollSummaryFilters,
} from '../model/payrollReport.types'
import type { CreateExportPayload, ExportJob, ExportJobListFilters, ExportPreview } from '../model/reportExport.types'
import { normalizeExportPayload } from '../services/reportFilter.service'

export async function createExportJob(payload: CreateExportPayload) {
  const { data } = await gatewayClient.post<ExportJob>('/reports/exports', normalizeExportPayload(payload))
  return data
}

export async function getExportJob(jobId: number) {
  const { data } = await gatewayClient.get<ExportJob>(`/reports/exports/${jobId}`)
  return data
}

export async function listExportJobs(filters: ExportJobListFilters = {}) {
  const { data } = await gatewayClient.get<PageResponse<ExportJob>>('/reports/exports', {
    params: filters,
  })
  return data
}

export async function getExportPreview(jobId: number) {
  const { data } = await gatewayClient.get<ExportPreview>(`/reports/exports/${jobId}/preview`)
  return data
}

export async function getReportStockBalances(filters: InventoryReportFilters) {
  const { data } = await gatewayClient.get<ReportStockBalancePage>('/reports/inventory/stock-balances', {
    params: {
      ingredientId: filters.ingredientId,
      outletId: filters.outletId,
      page: filters.page,
      size: filters.size,
    },
  })
  return data
}

export async function getReportInventoryTransactions(filters: InventoryReportFilters) {
  const { data } = await gatewayClient.get<ReportInventoryTransactionPage>('/reports/inventory/inventory-transactions', {
    params: {
      from: filters.from,
      ingredientId: filters.ingredientId,
      outletId: filters.outletId,
      page: filters.page,
      size: filters.size,
      to: filters.to,
      txnType: filters.txnType,
    },
  })
  return data
}

export async function getPayrollSummary(filters: ReportPayrollSummaryFilters) {
  const { data } = await gatewayClient.get<ReportPayrollSummary>('/reports/payroll/summary', {
    params: filters,
  })
  return data
}

export async function getPayrollRunReport(runId: number) {
  const { data } = await gatewayClient.get<ReportPayrollRunDetail>(`/reports/payroll/runs/${runId}`)
  return data
}

export async function listPayrollRuns(regionId?: number) {
  const { data } = await gatewayClient.get<ReportPayrollRunListItem[]>('/payroll-runs', {
    params: regionId ? { regionId } : undefined,
  })
  return data
}
