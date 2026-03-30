import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  ExportJobsPage,
  InventoryReportPage,
  PayrollReportPage,
  ReportsDashboardPage,
  RevenueReportPage,
} from '../routes/reportsRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useCreateExportJob: vi.fn(),
  useExportJobs: vi.fn(),
  useInventoryReport: vi.fn(),
  usePayrollReport: vi.fn(),
  useReportDashboard: vi.fn(),
  useRevenueReport: vi.fn(),
}))

vi.mock('../hooks/useCreateExportJob', () => ({
  useCreateExportJob: mocks.useCreateExportJob,
}))

vi.mock('../hooks/useExportJobs', () => ({
  useExportJobs: mocks.useExportJobs,
}))

vi.mock('../hooks/useReportDashboard', () => ({
  useReportDashboard: mocks.useReportDashboard,
}))

vi.mock('../hooks/useRevenueReport', () => ({
  useRevenueReport: mocks.useRevenueReport,
}))

vi.mock('../hooks/useInventoryReport', () => ({
  useInventoryReport: mocks.useInventoryReport,
}))

vi.mock('../hooks/usePayrollReport', () => ({
  usePayrollReport: mocks.usePayrollReport,
}))

function ReportsRoutesHarness() {
  return (
    <Routes>
      <Route path="/reports" element={<LazyRouteBoundary moduleName="Reports" label="Loading reports" />}>
        <Route index element={<ReportsDashboardPage />} />
        <Route path="revenue" element={<RevenueReportPage />} />
        <Route path="inventory" element={<InventoryReportPage />} />
        <Route path="payroll" element={<PayrollReportPage />} />
        <Route path="export-jobs" element={<ExportJobsPage />} />
      </Route>
    </Routes>
  )
}

describe('Reports route group', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.report.read,
          permissionConstants.report.export,
          permissionConstants.report.payrollRead,
          permissionConstants.report.payrollExport,
          permissionConstants.finance.payrollDetailRead,
        ],
      },
    })

    mocks.useCreateExportJob.mockReturnValue({ isPending: false, mutateAsync: vi.fn() })
    mocks.useExportJobs.mockReturnValue({ error: null, isLoading: false, jobs: [], refresh: vi.fn() })
    mocks.useReportDashboard.mockReturnValue({
      error: null,
      isLoading: false,
      jobs: [],
      refresh: vi.fn(),
      summaryCards: [
        { label: 'Recent exports', value: 0 },
        { label: 'Pending exports', value: 0 },
      ],
    })
    mocks.useRevenueReport.mockReturnValue({
      activeFilters: null,
      exportJob: null,
      exportJobError: null,
      isCreating: false,
      isLoading: false,
      preview: null,
      previewError: null,
      runReport: vi.fn(),
      summary: { dimensionCount: 0, jobStatus: null, rowCount: 0, totalDiscount: null, totalOrders: null, totalRevenue: null },
    })
    mocks.useInventoryReport.mockReturnValue({
      balanceQuery: { data: { hasMore: false, items: [], page: 0, size: 25 }, error: null, isLoading: false, refetch: vi.fn() },
      summary: { availableQuantity: 0, balanceRows: 0, ingredients: 0, transactionRows: 0, txnQuantityDelta: 0 },
      transactionQuery: { data: { hasMore: false, items: [], page: 0, size: 25 }, error: null, isLoading: false, refetch: vi.fn() },
    })
    mocks.usePayrollReport.mockReturnValue({
      runDetailQuery: { data: null, error: null, isLoading: false, refetch: vi.fn() },
      runsQuery: { data: [], error: null, isLoading: false, refetch: vi.fn() },
      summaryQuery: { data: { regionId: 1, fromDate: '2026-03-01', toDate: '2026-03-31', totalGrossPay: 1, totalNetPay: 1, totalTax: 1, totalExpense: 1, runCount: 1 }, error: null, isLoading: false, refetch: vi.fn() },
    })
  })

  it.each([
    ['/reports', 'Reports'],
    ['/reports/revenue', 'Revenue Report'],
    ['/reports/inventory', 'Inventory Report'],
    ['/reports/payroll', 'Payroll Report'],
    ['/reports/export-jobs', 'Export Jobs'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<ReportsRoutesHarness />, { route })

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
