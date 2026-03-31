import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RevenueReportPage } from '../routes/RevenueReportPage'

const mocks = vi.hoisted(() => ({
  useRevenueReport: vi.fn(),
}))

vi.mock('../hooks/useRevenueReport', () => ({
  useRevenueReport: mocks.useRevenueReport,
}))

describe('RevenueReportPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('blocks export-only users from opening the revenue report page', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.export],
      },
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

    renderWithProviders(<RevenueReportPage />)

    expect(screen.getByText('Permission denied')).toBeInTheDocument()
    expect(screen.getByText('Bạn cần report.read để mở revenue report.')).toBeInTheDocument()
  })

  it('allows read-only users to open the page but keeps export-backed execution disabled', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read],
      },
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

    renderWithProviders(<RevenueReportPage />)

    expect(screen.getByRole('heading', { name: 'Revenue Report' })).toBeInTheDocument()
    expect(screen.getByText(/cần report\.export để queue report run mới/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run revenue report' })).toBeDisabled()
  })

  it('renders revenue preview data and export links for a read-plus-export user', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read, permissionConstants.report.export],
      },
    })
    mocks.useRevenueReport.mockReturnValue({
      activeFilters: {
        dataset: 'REGION_DAILY_SUMMARY',
        fromDate: '2026-03-01',
        limit: 20,
        regionId: 1,
        toDate: '2026-03-30',
      },
      exportJob: {
        completedAt: '2026-03-30T08:01:00.000Z',
        dataset: 'REGION_DAILY_SUMMARY',
        downloadUrl: null,
        errorMessage: null,
        exportJobId: 501,
        expiresAt: null,
        failedAt: null,
        filePath: null,
        format: 'CSV',
        preview: [],
        requestedAt: '2026-03-30T08:00:00.000Z',
        rowCount: 2,
        startedAt: '2026-03-30T08:00:05.000Z',
        status: 'COMPLETED',
      },
      exportJobError: null,
      isCreating: false,
      isLoading: false,
      preview: {
        dataset: 'REGION_DAILY_SUMMARY',
        exportJobId: 501,
        rowCount: 2,
        rows: [{ revenue: 1000000, reportDate: '2026-03-29' }],
        status: 'COMPLETED',
      },
      previewError: null,
      runReport: vi.fn(),
      summary: { dimensionCount: 1, jobStatus: 'COMPLETED', rowCount: 1, totalDiscount: null, totalOrders: null, totalRevenue: 1000000 },
    })

    renderWithProviders(<RevenueReportPage />)

    expect(screen.getByText('Revenue preview')).toBeInTheDocument()
    expect(screen.getByText('1000000')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Job detail' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Download' })).toBeInTheDocument()
  })
})
