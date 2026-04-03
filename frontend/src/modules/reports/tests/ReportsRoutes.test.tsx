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
  OutletRevenueReportPage,
  PayrollReportPage,
  ReportsDashboardPage,
  RevenueReportPage,
} from '../routes/reportsRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useCreateExportJob: vi.fn(),
  useExportJobs: vi.fn(),
  useInventoryReport: vi.fn(),
  useOutletRevenueTodayStats: vi.fn(),
  usePayrollReport: vi.fn(),
  useReportDashboard: vi.fn(),
  useRegionalOutlets: vi.fn(),
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

vi.mock('../hooks/useOutletRevenueTodayStats', () => ({
  useOutletRevenueTodayStats: mocks.useOutletRevenueTodayStats,
}))

vi.mock('../../regional-ops/hooks/useRegionalOps', () => ({
  useRegionalOutlets: mocks.useRegionalOutlets,
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
        <Route path="outlet-revenue" element={<OutletRevenueReportPage />} />
      </Route>
    </Routes>
  )
}

describe('Reports route group', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()

    mocks.useCreateExportJob.mockReturnValue({ isPending: false, mutateAsync: vi.fn() })
    mocks.useExportJobs.mockReturnValue({
      error: null,
      isLoading: false,
      jobs: [],
      refresh: vi.fn(),
      restrictedJobCount: 0,
      restrictedJobIds: [],
    })
    mocks.useReportDashboard.mockReturnValue({
      error: null,
      isLoading: false,
      jobs: [],
      refresh: vi.fn(),
      restrictedJobCount: 0,
      restrictedJobIds: [],
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
    mocks.useRegionalOutlets.mockReturnValue({
      rows: [{ id: 101, name: 'Outlet 101', code: 'OUT-101', regionId: 1 }],
    })
    mocks.useOutletRevenueTodayStats.mockReturnValue({
      isLoading: false,
      outletStats: [{
        outletId: 101,
        sessionStatus: 'OPEN',
        currencyCode: 'VND',
        totalRevenue: 1200000,
        cashCollected: 600000,
        nonCashCollected: 600000,
        completed: 42,
        cancelled: 1,
        open: 3,
        isLoading: false,
      }],
    })
  })

  it('allows a read-only user to open revenue and export jobs but not payroll', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read],
      },
    })

    renderWithProviders(<ReportsRoutesHarness />, { route: '/reports/revenue' })
    expect(await screen.findByRole('heading', { name: 'Revenue Report' })).toBeInTheDocument()
    expect(screen.queryByText('Permission denied')).not.toBeInTheDocument()
  })

  it('keeps export-only users out of read-report routes while allowing the export center', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.export],
      },
    })

    const exportJobsRender = renderWithProviders(<ReportsRoutesHarness />, { route: '/reports/export-jobs' })
    expect(await screen.findByRole('heading', { name: 'Export Jobs' })).toBeInTheDocument()
    expect(screen.getByText(/^Create-only mode:/i)).toBeInTheDocument()
    exportJobsRender.unmount()

    renderWithProviders(<ReportsRoutesHarness />, { route: '/reports/revenue' })
    expect(await screen.findByRole('heading', { name: 'Revenue Report' })).toBeInTheDocument()
    expect(screen.getByText('Permission denied')).toBeInTheDocument()
  })

  it('allows payroll read-plus-export users to open the payroll route', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.payrollRead, permissionConstants.report.payrollExport],
      },
    })

    renderWithProviders(<ReportsRoutesHarness />, { route: '/reports/payroll' })

    expect(await screen.findByRole('heading', { name: 'Payroll Report' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Queue payroll export' })).toBeEnabled()
  })

  it('renders the outlet revenue dashboard surface for report readers', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read],
        scopeRoots: { system: false, regions: [1], outlets: [101] },
      },
    })

    renderWithProviders(<ReportsRoutesHarness />, { route: '/reports/outlet-revenue' })

    expect(await screen.findByRole('heading', { name: 'Outlet Revenue Summary' })).toBeInTheDocument()
    expect(screen.getByText('Summary-first regional revenue view aligned with the main reports dashboard family.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Export CSV/i })).toBeEnabled()
  })

  it('blocks users with no report access from export jobs', async () => {
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(<ReportsRoutesHarness />, { route: '/reports/export-jobs' })

    expect(await screen.findByRole('heading', { name: 'Export Jobs' })).toBeInTheDocument()
    expect(screen.getByText('Permission denied')).toBeInTheDocument()
  })
})
