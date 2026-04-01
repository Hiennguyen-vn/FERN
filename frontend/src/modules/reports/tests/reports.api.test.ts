import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { createExportJob } from '../api/reports.api'

describe('reports.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends a region-scoped export payload with format defaulted to CSV', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { exportJobId: 1 } } as any)

    await createExportJob({
      dataset: 'PAYROLL_SUMMARY',
      regionId: 2,
      fromDate: '2026-03-01',
      toDate: '2026-03-31',
    })

    expect(postSpy).toHaveBeenCalledWith('/reports/exports', {
      dataset: 'PAYROLL_SUMMARY',
      format: 'CSV',
      regionId: 2,
      fromDate: '2026-03-01',
      toDate: '2026-03-31',
    })
  })

  it('passes explicit format and outlet filter through unchanged', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { exportJobId: 2 } } as any)

    await createExportJob({
      dataset: 'SALES_FACT',
      format: 'CSV',
      regionId: 3,
      outletId: 101,
    })

    expect(postSpy).toHaveBeenCalledWith('/reports/exports', {
      dataset: 'SALES_FACT',
      format: 'CSV',
      regionId: 3,
      outletId: 101,
    })
  })
})
