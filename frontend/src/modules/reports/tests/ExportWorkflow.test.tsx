import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@core/api/apiError'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import {
  clearTestStorage,
  resetTestStores,
  setAuthenticatedSession,
} from '@shared/test-utils/scopeTestHelpers'
import { addRecentExportJobId } from '../services/exportHistory.service'
import { ExportDownloadPage } from '../routes/ExportDownloadPage'
import { ExportJobDetailPage } from '../routes/ExportJobDetailPage'
import { ExportJobsPage } from '../routes/ExportJobsPage'
import { ExportPreviewPage } from '../routes/ExportPreviewPage'
import type { ExportJob } from '../model/reportExport.types'

const reportsApi = vi.hoisted(() => ({
  createExportJob: vi.fn(),
  getExportJob: vi.fn(),
  getExportPreview: vi.fn(),
}))

const downloadService = vi.hoisted(() => ({
  startBrowserDownload: vi.fn(),
}))

vi.mock('../api/reports.api', () => reportsApi)
vi.mock('../services/download.service', () => downloadService)
vi.mock('../services/exportPolling.service', () => ({
  getExportPollingInterval: (job?: { status?: string } | null) =>
    !job || ['COMPLETED', 'FAILED'].includes(String(job.status ?? '').toUpperCase()) ? false : 10,
  isTerminalExportStatus: (status: string) => ['COMPLETED', 'FAILED'].includes(status.toUpperCase()),
}))

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function createExportJob(overrides: Partial<ExportJob> = {}): ExportJob {
  return {
    exportJobId: overrides.exportJobId ?? 901,
    status: overrides.status ?? 'QUEUED',
    dataset: overrides.dataset ?? 'SALES_FACT',
    format: overrides.format ?? 'CSV',
    requestedAt: overrides.requestedAt ?? '2026-03-30T11:00:00.000Z',
    startedAt: overrides.startedAt ?? null,
    completedAt: overrides.completedAt ?? null,
    failedAt: overrides.failedAt ?? null,
    rowCount: overrides.rowCount ?? null,
    downloadUrl: overrides.downloadUrl ?? null,
    expiresAt: overrides.expiresAt ?? null,
    errorMessage: overrides.errorMessage ?? null,
    filePath: overrides.filePath ?? null,
    preview: overrides.preview ?? [],
  }
}

describe('export workflows', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    vi.useRealTimers()
    reportsApi.createExportJob.mockReset()
    reportsApi.getExportJob.mockReset()
    reportsApi.getExportPreview.mockReset()
    downloadService.startBrowserDownload.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('lets export-only users queue exports in create-only mode without opening recent-job inspection', async () => {
    const user = userEvent.setup()
    const exportJobState = createExportJob({
      exportJobId: 901,
      status: 'QUEUED',
    })

    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.export],
      },
    })
    reportsApi.createExportJob.mockResolvedValue(clone(exportJobState))

    renderWithProviders(<ExportJobsPage />)

    await user.type(screen.getByLabelText('Region ID'), '1')
    await user.type(screen.getByLabelText('Outlet ID'), '101')
    await user.type(screen.getByLabelText('From date'), '2026-03-01')
    await user.type(screen.getByLabelText('To date'), '2026-03-30')
    await user.click(screen.getByRole('button', { name: 'Queue export' }))

    await waitFor(() => {
      expect(reportsApi.createExportJob).toHaveBeenCalledWith(
        expect.objectContaining({
          dataset: 'SALES_FACT',
          format: 'CSV',
          regionId: 1,
          outletId: 101,
        }),
      )
    })

    expect(screen.getByText(/^Create-only mode:/i)).toBeInTheDocument()
    expect(screen.getByText('Recent export inspection requires read access')).toBeInTheDocument()
    expect(reportsApi.getExportJob).not.toHaveBeenCalled()
    expect(screen.queryByRole('link', { name: 'Details' })).not.toBeInTheDocument()
  })

  it('shows readable recent jobs and hides restricted ones for partial-read users', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read],
      },
    })
    addRecentExportJobId(901)
    addRecentExportJobId(902)

    reportsApi.getExportJob.mockImplementation(async (jobId: number) => {
      if (jobId === 901) {
        return clone(createExportJob({ exportJobId: 901, dataset: 'SALES_FACT', status: 'COMPLETED' }))
      }

      throw new ApiError(403, { message: 'Forbidden' })
    })

    renderWithProviders(<ExportJobsPage />)

    expect(await screen.findByText('Export #901')).toBeInTheDocument()
    expect(screen.getByText(/1 recent export job is hidden/i)).toBeInTheDocument()
    expect(screen.queryByText('Không thể tải recent export jobs')).not.toBeInTheDocument()
  })

  it('renders a permission-denied state when export job detail is forbidden', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.export],
      },
    })
    reportsApi.getExportJob.mockRejectedValue(new ApiError(403, { message: 'Forbidden' }))

    renderWithProviders(
      <Routes>
        <Route path="/reports/export-jobs/:jobId" element={<ExportJobDetailPage />} />
      </Routes>,
      { route: '/reports/export-jobs/902' },
    )

    expect(await screen.findByText('Bạn không có quyền truy cập export job này.')).toBeInTheDocument()
  })

  it('renders a permission-denied state when export preview is forbidden', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.export],
      },
    })
    reportsApi.getExportPreview.mockRejectedValue(new ApiError(403, { message: 'Forbidden' }))

    renderWithProviders(
      <Routes>
        <Route path="/reports/export-jobs/:jobId/preview" element={<ExportPreviewPage />} />
      </Routes>,
      { route: '/reports/export-jobs/903/preview' },
    )

    expect(await screen.findByText('Bạn không có quyền truy cập export preview này.')).toBeInTheDocument()
  })

  it('does not start download when export download access is forbidden', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.export],
      },
    })
    reportsApi.getExportJob.mockRejectedValue(new ApiError(403, { message: 'Forbidden' }))

    renderWithProviders(
      <Routes>
        <Route path="/reports/export-jobs/:jobId/download" element={<ExportDownloadPage />} />
      </Routes>,
      { route: '/reports/export-jobs/904/download' },
    )

    expect(await screen.findByText('Bạn không có quyền truy cập export download này.')).toBeInTheDocument()
    expect(downloadService.startBrowserDownload).not.toHaveBeenCalled()
  })

  it('preflights export download with job detail before starting the browser download', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read],
      },
    })
    reportsApi.getExportJob.mockResolvedValue(
      createExportJob({
        exportJobId: 905,
        status: 'COMPLETED',
        dataset: 'SALES_FACT',
        completedAt: '2026-03-30T11:01:00.000Z',
      }),
    )

    renderWithProviders(
      <Routes>
        <Route path="/reports/export-jobs/:jobId/download" element={<ExportDownloadPage />} />
      </Routes>,
      { route: '/reports/export-jobs/905/download' },
    )

    await waitFor(() => {
      expect(reportsApi.getExportJob).toHaveBeenCalledWith(905)
      expect(downloadService.startBrowserDownload).toHaveBeenCalledWith('/reports/exports/905/download')
    })
  })
})
