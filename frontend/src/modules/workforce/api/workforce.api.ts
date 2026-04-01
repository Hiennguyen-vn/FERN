import { gatewayClient } from '@core/api/gatewayClient'
import type {
  AttendanceApproval,
  AttendanceEventFilters,
  AttendanceEventPage,
  AttendanceEventRecord,
  RecordAttendanceEventPayload,
} from '../model/workforce.types'

export async function getAttendanceEvents(filters: AttendanceEventFilters) {
  const { data } = await gatewayClient.get<AttendanceEventPage>('/attendance-events', {
    params: filters,
  })
  return data
}

export async function recordAttendanceEvent(payload: RecordAttendanceEventPayload) {
  // Idempotency key derived from the natural business key of an attendance event:
  // same employee + shift + type + day always produces the same key, preventing
  // duplicate clock-in/out even when the request is retried after a network failure.
  const eventDay = payload.eventTime.slice(0, 10) // YYYY-MM-DD
  const idempotencyKey = `attendance:${payload.employeeId}:${payload.shiftAssignmentId}:${payload.eventType}:${eventDay}`

  const { data } = await gatewayClient.post<AttendanceEventRecord>('/attendance-events', payload, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
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
