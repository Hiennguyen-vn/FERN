import { afterEach, describe, expect, it, vi } from 'vitest'
import { gatewayClient } from '@core/api/gatewayClient'
import { recordAttendanceEvent } from '../api/workforce.api'

describe('workforce.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends an idempotency key when recording attendance events', async () => {
    const postSpy = vi.spyOn(gatewayClient, 'post').mockResolvedValue({ data: { id: 1 } } as any)

    await recordAttendanceEvent({
      employeeId: 1,
      regionId: 2,
      outletId: 1,
      shiftAssignmentId: 10,
      eventType: 'CLOCK_IN',
      eventTime: '2026-04-01T01:00:00Z',
      sourceSystem: 'WEB',
    })

    expect(postSpy).toHaveBeenCalledWith('/attendance-events', {
      employeeId: 1,
      regionId: 2,
      outletId: 1,
      shiftAssignmentId: 10,
      eventType: 'CLOCK_IN',
      eventTime: '2026-04-01T01:00:00Z',
      sourceSystem: 'WEB',
    }, {
      headers: expect.objectContaining({
        'Idempotency-Key': expect.any(String),
      }),
    })
  })
})
