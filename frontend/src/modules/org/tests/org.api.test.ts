import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { orgApi } from '../api/org.api'

describe('org.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends region create and update payloads using backend-supported field names', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 1 } } as any)
    const patchSpy = vi.spyOn(gatewayClient, 'patch').mockResolvedValue({ data: { id: 1 } } as any)

    await orgApi.createRegion({
      code: 'R-SOUTH',
      parentRegionId: 1,
      currencyCode: 'VND',
      name: 'South',
      taxCode: 'TAX-01',
      timezoneName: 'Asia/Ho_Chi_Minh',
    })
    await orgApi.updateRegion(1, {
      parentRegionId: 2,
      currencyCode: 'USD',
      name: 'South 2',
      taxCode: 'TAX-02',
      timezoneName: 'Asia/Singapore',
    })

    expect(postSpy).toHaveBeenCalledWith('/regions', {
      code: 'R-SOUTH',
      parentRegionId: 1,
      currencyCode: 'VND',
      name: 'South',
      taxCode: 'TAX-01',
      timezoneName: 'Asia/Ho_Chi_Minh',
    })
    expect(patchSpy).toHaveBeenCalledWith('/regions/1', {
      parentRegionId: 2,
      currencyCode: 'USD',
      name: 'South 2',
      taxCode: 'TAX-02',
      timezoneName: 'Asia/Singapore',
    })
  })
})
