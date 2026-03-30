import { afterEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '@core/api/httpClient'
import { iamApi } from '../api/iam.api'

describe('iam.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uses published IAM read endpoints', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: {} } as any)

    await iamApi.getUser(1)
    await iamApi.getRoles()
    await iamApi.getPermissions()
    await iamApi.getUserPermissionOverrides(1)
    await iamApi.getEffectiveAccess(1)

    expect(getSpy).toHaveBeenNthCalledWith(1, '/users/1')
    expect(getSpy).toHaveBeenNthCalledWith(2, '/roles')
    expect(getSpy).toHaveBeenNthCalledWith(3, '/permissions')
    expect(getSpy).toHaveBeenNthCalledWith(4, '/users/1/permission-overrides')
    expect(getSpy).toHaveBeenNthCalledWith(5, '/users/1/effective-access')
  })
})
