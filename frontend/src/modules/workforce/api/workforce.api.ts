import { gatewayClient } from '@core/api/gatewayClient'
import type {
  AttendanceApproval,
  AttendanceEventFilters,
  AttendanceEventPage,
  RecordAttendanceEventPayload,
} from '../model/workforce.types'

export async function getAttendanceEvents(filters: AttendanceEventFilters) {
  const { data } = await gatewayClient.get<AttendanceEventPage>('/attendance-events', {
    params: filters,
  })
  return data
}

export async function recordAttendanceEvent(payload: RecordAttendanceEventPayload) {
  const { data } = await gatewayClient.post('/attendance-events', payload)
  return data
}

export async function getAttendanceApprovals(params: { outletId?: number; regionId?: number }) {
  const { data } = await gatewayClient.get<AttendanceApproval[]>('/attendance-approvals', {
    params,
  })
  return data
}

export async function getAttendanceApproval(shiftAssignmentId: number) {
  const { data } = await gatewayClient.get<AttendanceApproval>(`/attendance-approvals/${shiftAssignmentId}`)
  return data
}

export async function reviewAttendance(
  shiftAssignmentId: number,
  action: 'approve' | 'reject',
  comments?: string,
) {
  const { data } = await gatewayClient.post<AttendanceApproval>(
    `/attendance-approvals/${shiftAssignmentId}/${action}`,
    comments ? { comments } : {},
  )
  return data
}
