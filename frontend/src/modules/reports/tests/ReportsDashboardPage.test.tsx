import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { ReportsDashboardPage } from '../routes/ReportsDashboardPage'

const mocks = vi.hoisted(() => ({
  useReportDashboard: vi.fn(),
}))

vi.mock('../hooks/useReportDashboard', () => ({
  useReportDashboard: mocks.useReportDashboard,
}))

describe('ReportsDashboardPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('shows only readable report surfaces for a generic read user', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read],
      },
    })
    mocks.useReportDashboard.mockReturnValue({
      error: null,
      isLoading: false,
      jobs: [
        {
          completedAt: null,
          dataset: 'REGION_DAILY_SUMMARY',
          downloadUrl: null,
          errorMessage: null,
          exportJobId: 99,
          expiresAt: null,
          failedAt: null,
          filePath: null,
          format: 'CSV',
          preview: [],
          requestedAt: '2026-03-30T08:00:00.000Z',
          restrictedJobCount: 0,
          rowCount: 12,
          startedAt: null,
          status: 'QUEUED',
        },
      ],
      refresh: vi.fn(),
      restrictedJobCount: 0,
      restrictedJobIds: [],
      summaryCards: [
        { label: 'Recent exports', value: 1 },
        { label: 'Pending exports', value: 1 },
      ],
    })

    renderWithProviders(<ReportsDashboardPage />)

    expect(screen.getByRole('heading', { name: 'Reports' })).toBeInTheDocument()
    expect(screen.getByText('Revenue report')).toBeInTheDocument()
    expect(screen.getByText('Inventory report')).toBeInTheDocument()
    expect(screen.queryByText('Payroll report')).not.toBeInTheDocument()
    expect(screen.getByText('Export jobs')).toBeInTheDocument()
    expect(screen.getByText('Export activity')).toBeInTheDocument()
    expect(screen.getByText('#99')).toBeInTheDocument()
  })

  it('keeps export-only users in create-only dashboard mode', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.export],
      },
    })
    mocks.useReportDashboard.mockReturnValue({
      error: null,
      isLoading: false,
      jobs: [],
      refresh: vi.fn(),
      restrictedJobCount: 0,
      restrictedJobIds: [],
      summaryCards: [],
    })

    renderWithProviders(<ReportsDashboardPage />)

    expect(screen.getByText(/create-only mode/i)).toBeInTheDocument()
    expect(screen.getByText('Export jobs')).toBeInTheDocument()
    expect(screen.queryByText('Revenue report')).not.toBeInTheDocument()
    expect(screen.queryByText('Export activity')).not.toBeInTheDocument()
  })

  it('shows permission denied without report permissions', () => {
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useReportDashboard.mockReturnValue({
      error: null,
      isLoading: false,
      jobs: [],
      refresh: vi.fn(),
      restrictedJobCount: 0,
      restrictedJobIds: [],
      summaryCards: [],
    })

    renderWithProviders(<ReportsDashboardPage />)

    expect(screen.getByText('Permission denied')).toBeInTheDocument()
  })
})
