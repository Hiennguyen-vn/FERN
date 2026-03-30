import { describe, expect, it } from 'vitest'
import { getExportPollingInterval, isTerminalExportStatus } from '../services/exportPolling.service'

describe('exportPolling.service', () => {
  it('detects terminal statuses', () => {
    expect(isTerminalExportStatus('COMPLETED')).toBe(true)
    expect(isTerminalExportStatus('FAILED')).toBe(true)
    expect(isTerminalExportStatus('RUNNING')).toBe(false)
  })

  it('polls only while export is not terminal', () => {
    expect(
      getExportPollingInterval({
        exportJobId: 1,
        status: 'RUNNING',
        dataset: 'SALES_FACT',
        format: 'CSV',
        requestedAt: new Date().toISOString(),
        startedAt: null,
        completedAt: null,
        failedAt: null,
        rowCount: null,
        downloadUrl: null,
        expiresAt: null,
        errorMessage: null,
        filePath: null,
        preview: [],
      }),
    ).toBe(3000)
    expect(
      getExportPollingInterval({
        exportJobId: 1,
        status: 'COMPLETED',
        dataset: 'SALES_FACT',
        format: 'CSV',
        requestedAt: new Date().toISOString(),
        startedAt: null,
        completedAt: null,
        failedAt: null,
        rowCount: null,
        downloadUrl: null,
        expiresAt: null,
        errorMessage: null,
        filePath: null,
        preview: [],
      }),
    ).toBe(false)
  })
})
