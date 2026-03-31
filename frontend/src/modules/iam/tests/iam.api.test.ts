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

  it('calls correct paths for IAM admin write operations', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)
    const patchSpy = vi.spyOn(httpClient, 'patch').mockResolvedValue({ data: {} } as any)
    const putSpy = vi.spyOn(httpClient, 'put').mockResolvedValue({ data: {} } as any)

    await iamApi.createUser({ username: 'alice' } as any)
    await iamApi.updateUser(7, { fullName: 'Alice' } as any)
    await iamApi.assignRoles(7, { roleCodes: ['admin'] })
    await iamApi.assignScopes(7, { regions: [1], outlets: [101] })
    await iamApi.replacePermissionOverrides(7, { overrides: [] })

    expect(postSpy).toHaveBeenCalledWith('/users', { username: 'alice' })
    expect(patchSpy).toHaveBeenCalledWith('/users/7', { fullName: 'Alice' })
    expect(postSpy).toHaveBeenCalledWith('/users/7/roles', { roleCodes: ['admin'] })
    expect(postSpy).toHaveBeenCalledWith('/users/7/scopes', { regions: [1], outlets: [101] })
    expect(putSpy).toHaveBeenCalledWith('/users/7/permission-overrides', { overrides: [] })
  })
})
