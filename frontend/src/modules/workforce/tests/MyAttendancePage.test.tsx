import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { MyAttendancePage } from '../routes/MyAttendancePage'

const workforceApi = vi.hoisted(() => ({
  getAttendanceEvents: vi.fn(),
  recordAttendanceEvent: vi.fn(),
}))

vi.mock('../api/workforce.api', () => workforceApi)

describe('MyAttendancePage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: ['hr.attendance.write'],
        scopeRoots: { system: false, regions: [1], outlets: [101] },
      },
    })

    workforceApi.getAttendanceEvents.mockResolvedValue({
      items: [
        {
          id: 1,
          employeeId: 12,
          regionId: 1,
          outletId: 101,
          shiftAssignmentId: 701,
          shiftDate: '2026-04-03',
          eventType: 'CLOCK_IN',
          eventTime: '2026-04-03T08:00:00.000Z',
          sourceSystem: 'FERN_FRONTEND',
        },
      ],
      hasMore: false,
      page: 0,
      size: 20,
    })
    workforceApi.recordAttendanceEvent.mockResolvedValue({
      id: 2,
      employeeId: 12,
      shiftAssignmentId: 701,
      eventType: 'CLOCK_IN',
      eventTime: '2026-04-03T08:00:00.000Z',
      sourceSystem: 'FERN_FRONTEND',
    })
  })

  it('renders the scoped attendance workspace with the Stitch-aligned hero state', async () => {
    renderWithProviders(<MyAttendancePage />, { route: '/workforce/my-attendance' })

    expect(await screen.findByRole('heading', { name: 'My Attendance' })).toBeInTheDocument()
    expect(screen.getByText('Active Attendance')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Record attendance event' })).toBeInTheDocument()
    expect(screen.getByText('Record attendance event below')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Record event' })).toBeInTheDocument()
    expect(await screen.findByText('#12')).toBeInTheDocument()
  })
})
