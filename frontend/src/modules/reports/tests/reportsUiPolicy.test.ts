import { permissionConstants } from '@core/permissions/permission.constants'
import { createTestPrincipal } from '@shared/test-utils/scopeTestHelpers'
import { describe, expect, it } from 'vitest'
import type { ExportJob } from '../model/reportExport.types'
import {
  canCreateExport,
  canDownloadExport,
  canInspectExportJobs,
  canOpenExportJob,
  canOpenReportDashboard,
  canPreviewExport,
  canReadInventoryReport,
  canReadPayrollReport,
  canReadPayrollReportDetail,
  canReadRevenueReport,
  canViewExportJobs,
  getCreatableExportDatasets,
  isExportDownloadAvailable,
  isExportPreviewAvailable,
} from '../services/reportsUiPolicy.service'

const baseJob: ExportJob = {
  exportJobId: 1,
  status: 'QUEUED',
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
  it('keeps export-only users out of read-report pages while still opening the export center', () => {
    const principal = createTestPrincipal({
      permissions: [permissionConstants.report.export],
    })

    expect(canOpenReportDashboard(principal)).toBe(true)
    expect(canViewExportJobs(principal)).toBe(true)
    expect(canInspectExportJobs(principal)).toBe(false)
    expect(canReadRevenueReport(principal)).toBe(false)
    expect(canReadInventoryReport(principal)).toBe(false)
    expect(canReadPayrollReport(principal)).toBe(false)
    expect(canCreateExport(principal, 'SALES_FACT')).toBe(true)
    expect(canCreateExport(principal, 'PAYROLL_SUMMARY')).toBe(false)
    expect(getCreatableExportDatasets(principal)).toContain('SALES_FACT')
    expect(canOpenExportJob(principal, { ...baseJob, dataset: 'SALES_FACT' })).toBe(false)
  })

  it('lets generic read users inspect generic export records without granting export creation', () => {
    const principal = createTestPrincipal({
      permissions: [permissionConstants.report.read],
    })

    expect(canReadRevenueReport(principal)).toBe(true)
    expect(canReadInventoryReport(principal)).toBe(true)
    expect(canReadPayrollReport(principal)).toBe(false)
    expect(canInspectExportJobs(principal)).toBe(true)
    expect(canCreateExport(principal)).toBe(false)
    expect(canOpenExportJob(principal, { ...baseJob, dataset: 'SALES_FACT' })).toBe(true)
    expect(canOpenExportJob(principal, { ...baseJob, dataset: 'PAYROLL_SUMMARY' })).toBe(false)
  })

  it('maps payroll datasets to payroll permissions and preserves payroll detail masking separately', () => {
    const principal = createTestPrincipal({
      permissions: [
        permissionConstants.report.payrollRead,
        permissionConstants.report.payrollExport,
        permissionConstants.finance.payrollDetailRead,
      ],
    })

    expect(canReadPayrollReport(principal)).toBe(true)
    expect(canCreateExport(principal, 'PAYROLL_RUN')).toBe(true)
    expect(canCreateExport(principal, 'REGION_DAILY_SUMMARY')).toBe(false)
    expect(canOpenExportJob(principal, { ...baseJob, dataset: 'PAYROLL_RUN' })).toBe(true)
    expect(canReadPayrollReportDetail(principal)).toBe(true)
  })

  it('requires both readability and status before preview/download actions are enabled', () => {
    const readablePrincipal = createTestPrincipal({
      permissions: [permissionConstants.report.read],
    })
    const exportOnlyPrincipal = createTestPrincipal({
      permissions: [permissionConstants.report.export],
    })
    const runningJob = { ...baseJob, status: 'RUNNING' }
    const completedJob = { ...baseJob, status: 'COMPLETED' }

    expect(isExportPreviewAvailable(runningJob)).toBe(true)
    expect(isExportPreviewAvailable(completedJob)).toBe(true)
    expect(isExportDownloadAvailable(completedJob)).toBe(true)
    expect(canPreviewExport(readablePrincipal, runningJob)).toBe(true)
    expect(canDownloadExport(readablePrincipal, completedJob)).toBe(true)
    expect(canPreviewExport(exportOnlyPrincipal, runningJob)).toBe(false)
    expect(canDownloadExport(exportOnlyPrincipal, completedJob)).toBe(false)
  })
})
