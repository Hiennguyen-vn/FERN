import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { createExportJob } from '../api/reports.api'

describe('reports.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('flattens legacy nested export filters before calling the backend', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { exportJobId: 1 } } as any)

    await createExportJob({
      dataset: 'PAYROLL_SUMMARY',
      format: 'CSV',
      filters: {
        regionId: 2,
        fromDate: '2026-03-01',
        toDate: '2026-03-31',
        limit: 1000,
      },
    })

    expect(postSpy).toHaveBeenCalledWith('/reports/exports', {
      dataset: 'PAYROLL_SUMMARY',
      format: 'CSV',
      regionId: 2,
      fromDate: '2026-03-01',
      toDate: '2026-03-31',
      limit: 1000,
      filters: undefined,
      outletId: undefined,
      payrollRunId: undefined,
    })
  })

  it('preserves top-level export fields over legacy nested filters', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { exportJobId: 2 } } as any)

    await createExportJob({
      dataset: 'SALES_FACT',
      format: 'CSV',
      regionId: 3,
      outletId: 101,
      filters: {
        regionId: 2,
        outletId: 999,
      },
    })

    expect(postSpy).toHaveBeenCalledWith('/reports/exports', {
      dataset: 'SALES_FACT',
      format: 'CSV',
      regionId: 3,
      outletId: 101,
      filters: undefined,
      fromDate: undefined,
      toDate: undefined,
      payrollRunId: undefined,
      limit: undefined,
    })
  })
})
