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

  it('renders report surfaces and recent export activity', () => {
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
          rowCount: 12,
          startedAt: null,
          status: 'QUEUED',
        },
      ],
      refresh: vi.fn(),
      summaryCards: [
        { label: 'Recent exports', value: 1 },
        { label: 'Pending exports', value: 1 },
      ],
    })

    renderWithProviders(<ReportsDashboardPage />)

    expect(screen.getByRole('heading', { name: 'Reports' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Open' }).length).toBeGreaterThan(0)
    expect(screen.getByText('Revenue report')).toBeInTheDocument()
    expect(screen.getByText('Recent export activity')).toBeInTheDocument()
    expect(screen.getByText('#99')).toBeInTheDocument()
  })

  it('shows permission denied without report permissions', () => {
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useReportDashboard.mockReturnValue({
      error: null,
      isLoading: false,
      jobs: [],
      refresh: vi.fn(),
      summaryCards: [],
    })

    renderWithProviders(<ReportsDashboardPage />)

    expect(screen.getByText('Permission denied')).toBeInTheDocument()
  })
})
