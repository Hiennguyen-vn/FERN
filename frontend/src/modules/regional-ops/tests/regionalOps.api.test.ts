import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@core/api/gatewayClient', () => ({
  gatewayClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}))

import { gatewayClient } from '@core/api/gatewayClient'
import { regionalOpsApi } from '../api/regionalOps.api'

describe('regionalOps.api', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('getOutlet calls GET /outlets/:id and returns data', async () => {
    const outlet = { id: 101, name: 'Outlet A' } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: outlet } as any)

    const result = await regionalOpsApi.getOutlet(101)
    expect(gatewayClient.get).toHaveBeenCalledWith('/outlets/101')
    expect(result).toEqual(outlet)
  })

  it('getRegion calls GET /regions/:id and returns data', async () => {
    const region = { id: 7, name: 'North' } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: region } as any)

    const result = await regionalOpsApi.getRegion(7)
    expect(gatewayClient.get).toHaveBeenCalledWith('/regions/7')
    expect(result).toEqual(region)
  })
})
