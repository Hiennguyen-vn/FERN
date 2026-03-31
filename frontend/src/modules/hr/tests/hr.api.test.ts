import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { hrApi } from '../api/hr.api'

describe('hr.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('reads employee contracts from the employee-scoped endpoint and can resolve a single contract id', async () => {
    const getSpy = vi.spyOn(gatewayClient, 'get').mockResolvedValue({
      data: [
        { id: 10, employeeId: 7, contractStatus: 'ACTIVE' },
        { id: 11, employeeId: 7, contractStatus: 'ENDED' },
      ],
    } as any)

    const contract = await hrApi.getEmployeeContract(7, 10)

    expect(getSpy).toHaveBeenCalledWith('/employees/7/contracts')
    expect(contract).toEqual({ id: 10, employeeId: 7, contractStatus: 'ACTIVE' })
  })
})
