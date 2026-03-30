import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage } from '@shared/test-utils/scopeTestHelpers'
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
    vi.useRealTimers()
    reportsApi.createExportJob.mockReset()
    reportsApi.getExportJob.mockReset()
    reportsApi.getExportPreview.mockReset()
    downloadService.startBrowserDownload.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('creates an export job and shows it in recent jobs', async () => {
    const user = userEvent.setup()
    const exportJobState = createExportJob({
      exportJobId: 901,
      status: 'QUEUED',
    })

    reportsApi.createExportJob.mockResolvedValue(clone(exportJobState))
    reportsApi.getExportJob.mockImplementation(async () => clone(exportJobState))

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
    expect(await screen.findByText('Export #901')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Details' })).toBeInTheDocument()
  })

  it('polls export detail until the job completes', async () => {
    const runningJob = createExportJob({
      exportJobId: 902,
      status: 'RUNNING',
      startedAt: '2026-03-30T11:00:05.000Z',
    })
    const completedJob = createExportJob({
      exportJobId: 902,
      status: 'COMPLETED',
      startedAt: '2026-03-30T11:00:05.000Z',
      completedAt: '2026-03-30T11:01:00.000Z',
      rowCount: 42,
      downloadUrl: '/reports/exports/902/download',
      preview: [{ orderNumber: 'SO-1' }],
    })
    let requestCount = 0

    reportsApi.getExportJob.mockImplementation(async () => {
      requestCount += 1
      return clone(requestCount === 1 ? runningJob : completedJob)
    })

    renderWithProviders(
      <Routes>
        <Route path="/reports/export-jobs/:jobId" element={<ExportJobDetailPage />} />
      </Routes>,
      { route: '/reports/export-jobs/902' },
    )

    expect(await screen.findByText('Job is being generated. Preview may already be available.')).toBeInTheDocument()
    await waitFor(() => {
      expect(reportsApi.getExportJob).toHaveBeenCalledTimes(2)
    })

    expect(await screen.findByText('Job finished successfully and can be previewed or downloaded.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Preview' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Download' })).toBeInTheDocument()
  })

  it('renders export preview rows from the preview endpoint', async () => {
    reportsApi.getExportPreview.mockResolvedValue({
      exportJobId: 903,
      status: 'COMPLETED',
      dataset: 'SALES_FACT',
      rowCount: 1,
      rows: [{ orderNumber: 'SO-903', totalAmount: 150000 }],
    })

    renderWithProviders(
      <Routes>
        <Route path="/reports/export-jobs/:jobId/preview" element={<ExportPreviewPage />} />
      </Routes>,
      { route: '/reports/export-jobs/903/preview' },
    )

    expect(await screen.findByText('SO-903')).toBeInTheDocument()
    expect(screen.getByText('150000')).toBeInTheDocument()
  })

  it('starts export download using the current API base path', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/reports/export-jobs/:jobId/download" element={<ExportDownloadPage />} />
      </Routes>,
      { route: '/reports/export-jobs/904/download' },
    )

    await waitFor(() => {
      expect(downloadService.startBrowserDownload).toHaveBeenCalledWith('/reports/exports/904/download')
    })
  })
})
