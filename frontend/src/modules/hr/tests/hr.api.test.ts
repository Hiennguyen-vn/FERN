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

  /**
   * Backend contract: GET /attendance-events returns AttendanceEventListItemResponse.
   * This list projection includes regionId, outletId, and shiftDate — fields NOT
   * present on AttendanceEventResponse (the POST create response).
   *
   * Reference: HrResponses.AttendanceEventListItemResponse
   *   Long id, Long employeeId, Long regionId, Long outletId,
   *   Long shiftAssignmentId, LocalDate shiftDate, String eventType,
   *   Instant eventTime, String sourceSystem
   */
  it('sends attendance event filters as query params and expects list projection shape', async () => {
    const mockListItem = {
      id: 1,
      employeeId: 42,
      regionId: 10,      // present on list — absent on create response
      outletId: 5,       // present on list — absent on create response
      shiftAssignmentId: 99,
      shiftDate: '2026-04-01', // present on list — absent on create response
      eventType: 'CLOCK_IN',
      eventTime: '2026-04-01T08:00:00Z',
      sourceSystem: 'WEB',
    }

    const getSpy = vi.spyOn(gatewayClient, 'get').mockResolvedValue({
      data: { items: [mockListItem], hasMore: false, page: 0, size: 20 },
    } as any)

    const result = await hrApi.listAttendanceEvents({
      outletId: 5,
      regionId: 10,
      page: 0,
      size: 20,
      sort: 'desc',
    })

    expect(getSpy).toHaveBeenCalledWith('/attendance-events', {
      params: { outletId: 5, regionId: 10, page: 0, size: 20, sort: 'desc' },
    })

    // List items must carry the extended projection fields
    expect(result.items[0]).toMatchObject({
      regionId: 10,
      outletId: 5,
      shiftDate: '2026-04-01',
    })
  })

  /**
   * Backend contract: GET /attendance-approvals uses region/outlet query params
   * (not a path parameter).
   *
   * Reference: HrReadController.listAttendanceApprovals
   *   GET /attendance-approvals?outletId=&regionId=
   */
  it('sends attendance approval filters as query params', async () => {
    const getSpy = vi.spyOn(gatewayClient, 'get').mockResolvedValue({ data: [] } as any)

    await hrApi.listAttendanceApprovals({ outletId: 5, regionId: 10 })

    expect(getSpy).toHaveBeenCalledWith('/attendance-approvals', {
      params: { outletId: 5, regionId: 10 },
    })
  })
})
