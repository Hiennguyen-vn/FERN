import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { AttendanceSummaryPage } from '../routes/AttendanceSummaryPage'

const mocks = vi.hoisted(() => ({
  useHrAttendanceApprovals: vi.fn(),
  useHrAttendanceEvents: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  useHrAttendanceApprovals: mocks.useHrAttendanceApprovals,
  useHrAttendanceEvents: mocks.useHrAttendanceEvents,
}))

describe('AttendanceSummaryPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.hr.attendanceReview],
      },
    })
    mocks.useHrAttendanceEvents.mockReturnValue({
      data: { items: [], page: 0, size: 100, hasMore: false },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrAttendanceApprovals.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders empty states for exceptions and events', async () => {
    renderWithProviders(<AttendanceSummaryPage />)

    expect(await screen.findByText('No attendance exceptions')).toBeInTheDocument()
    expect(screen.getByText('No attendance events')).toBeInTheDocument()
  })

  it('renders page-level error when both attendance queries fail', async () => {
    mocks.useHrAttendanceEvents.mockReturnValue({
      data: undefined,
      error: new Error('Events failed'),
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrAttendanceApprovals.mockReturnValue({
      data: undefined,
      error: new Error('Approvals failed'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<AttendanceSummaryPage />)

    expect(await screen.findByRole('heading', { name: 'Không thể tải attendance summary' })).toBeInTheDocument()
    expect(screen.getByText('Events failed')).toBeInTheDocument()
  })
})
