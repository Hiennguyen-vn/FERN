import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { financeApi } from '../api/finance.api'

describe('finance.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('posts payroll submit requests to the submit endpoint', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 77, status: 'SUBMITTED' } } as any)

    await financeApi.submitPayrollRun(77)

    expect(postSpy).toHaveBeenCalledWith('/payroll-runs/77/submit', {})
  })
})
