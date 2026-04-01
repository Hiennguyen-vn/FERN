import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { AttendanceDetailPage } from '../routes/AttendanceDetailPage'
import { AttendanceReviewPage } from '../routes/AttendanceReviewPage'
import type { AttendanceApproval } from '../model/workforce.types'

const workforceApi = vi.hoisted(() => ({
  getAttendanceApproval: vi.fn(),
  getAttendanceApprovals: vi.fn(),
  getAttendanceEvents: vi.fn(),
  recordAttendanceEvent: vi.fn(),
  reviewAttendance: vi.fn(),
}))

vi.mock('../api/workforce.api', () => workforceApi)

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function createApproval(overrides: Partial<AttendanceApproval> = {}): AttendanceApproval {
  return {
    id: overrides.id ?? 701,
    shiftAssignmentId: overrides.shiftAssignmentId ?? 8801,
    status: overrides.status ?? 'PENDING',
    comments: overrides.comments ?? null,
    approvedAt: overrides.approvedAt ?? null,
    approvedByUserId: overrides.approvedByUserId ?? null,
    attendanceStatus: overrides.attendanceStatus ?? 'PRESENT',
    workHours: overrides.workHours ?? 8,
    overtimeHours: overrides.overtimeHours ?? 0,
    businessDate: overrides.businessDate ?? '2026-03-30',
  }
}

describe('attendance approval workflows', () => {
  let approvalState: AttendanceApproval

  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: ['hr.attendance.write', 'hr.attendance.review'] } })
    approvalState = createApproval()

    workforceApi.getAttendanceApprovals.mockImplementation(async () => clone([approvalState]))
    workforceApi.getAttendanceApproval.mockImplementation(async () => clone(approvalState))
    workforceApi.reviewAttendance.mockImplementation(async (shiftAssignmentId, action, comments) => {
      approvalState = {
        ...approvalState,
        shiftAssignmentId,
        status: action === 'approve' ? 'APPROVED' : 'REJECTED',
        comments: comments ?? null,
        approvedAt: '2026-03-30T17:00:00.000Z',
        approvedByUserId: 99,
      }

      return clone(approvalState)
    })
  })

  it('opens approval detail from the review list and approves it', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <Routes>
        <Route path="/workforce/attendance-approvals" element={<AttendanceReviewPage />} />
        <Route path="/workforce/attendance-approvals/:shiftAssignmentId" element={<AttendanceDetailPage />} />
      </Routes>,
      { route: '/workforce/attendance-approvals' },
    )

    expect(await screen.findByText('Approval #701')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Open detail' }))

    expect(await screen.findByRole('heading', { name: 'Attendance Detail 8801' })).toBeInTheDocument()
    await user.type(await screen.findByLabelText('Comments'), 'Looks good')
    await user.click(screen.getByRole('button', { name: 'Approve' }))

    await waitFor(() => {
      expect(workforceApi.reviewAttendance).toHaveBeenCalledWith(8801, 'approve', 'Looks good')
    })
    expect(await screen.findByText('APPROVED')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Looks good')).toBeInTheDocument()
    expect(screen.getByText('Attendance approval này đã ở trạng thái terminal và hiện ở chế độ chỉ đọc.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('rejects an attendance approval from the detail page', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <Routes>
        <Route path="/workforce/attendance-approvals/:shiftAssignmentId" element={<AttendanceDetailPage />} />
      </Routes>,
      { route: '/workforce/attendance-approvals/8801' },
    )

    expect(await screen.findByRole('heading', { name: 'Attendance Detail 8801' })).toBeInTheDocument()
    await user.type(await screen.findByLabelText('Comments'), 'Missing supporting evidence')
    await user.click(screen.getByRole('button', { name: 'Reject' }))

    await waitFor(() => {
      expect(workforceApi.reviewAttendance).toHaveBeenCalledWith(8801, 'reject', 'Missing supporting evidence')
    })
    expect(await screen.findByText('REJECTED')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Missing supporting evidence')).toBeInTheDocument()
    expect(screen.getByText('Attendance approval này đã ở trạng thái terminal và hiện ở chế độ chỉ đọc.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })
})
