import { permissionConstants } from '@core/permissions/permission.constants'
import { describe, expect, it } from 'vitest'
import {
  canDownloadExport,
  canExportPayrollReports,
  canExportReports,
  canPreviewExport,
  canReadPayrollReportDetail,
  canReadPayrollReports,
  canReadReports,
} from '../services/reportsUiPolicy.service'

const baseJob = {
  exportJobId: 1,
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
}

describe('reportsUiPolicy', () => {
  it('allows preview for running and completed jobs', () => {
    expect(canPreviewExport({ ...baseJob, status: 'RUNNING' })).toBe(true)
    expect(canPreviewExport({ ...baseJob, status: 'COMPLETED' })).toBe(true)
    expect(canPreviewExport({ ...baseJob, status: 'FAILED' })).toBe(false)
  })

  it('allows download only when completed', () => {
    expect(canDownloadExport({ ...baseJob, status: 'COMPLETED' })).toBe(true)
    expect(canDownloadExport({ ...baseJob, status: 'QUEUED' })).toBe(false)
  })

  it('evaluates report permissions consistently', () => {
    const principal = {
      permissions: [
        permissionConstants.report.read,
        permissionConstants.report.payrollExport,
        permissionConstants.finance.payrollDetailRead,
      ],
    }

    expect(canReadReports(principal as never)).toBe(true)
    expect(canExportReports(principal as never)).toBe(false)
    expect(canReadPayrollReports(principal as never)).toBe(true)
    expect(canExportPayrollReports(principal as never)).toBe(true)
    expect(canReadPayrollReportDetail(principal as never)).toBe(true)
  })
})
