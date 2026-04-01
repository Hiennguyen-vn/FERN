import type { PageResponse } from '@core/types/api'

/**
 * Shape returned by GET /attendance-events (AttendanceEventListItemResponse).
 * Includes outlet/region/shift context used for display.
 */
export interface AttendanceEventListItem {
  id: number
  employeeId: number
  regionId: number
  outletId: number
  shiftAssignmentId: number
  shiftDate: string
  eventType: string
  eventTime: string
  sourceSystem: string | null
}

/**
 * Shape returned by POST /attendance-events (AttendanceEventResponse).
 * The single-record create response intentionally omits regionId, outletId
 * and shiftDate — those are only present on the list item projection.
 */
export interface AttendanceEventRecord {
  id: number
  employeeId: number
  shiftAssignmentId: number
  eventType: string
  eventTime: string
  sourceSystem: string | null
}

export interface AttendanceApproval {
  id: number
  shiftAssignmentId: number
  status: string
  comments: string | null
  approvedAt: string | null
  approvedByUserId: number | null
  attendanceStatus: string
  workHours: number | null
  overtimeHours: number | null
  businessDate: string | null
}

export interface AttendanceEventFilters {
  employeeId?: number
  fromDate?: string
  outletId?: number
  page: number
  regionId?: number
  shiftAssignmentId?: number
  size: number
  sort?: string
  toDate?: string
}

export interface RecordAttendanceEventPayload {
  employeeId: number
  regionId: number
  outletId: number
  shiftAssignmentId: number
  eventType: 'CLOCK_IN' | 'CLOCK_OUT' | 'BREAK_START' | 'BREAK_END'
  eventTime: string
  sourceSystem?: string
}

export type AttendanceEventPage = PageResponse<AttendanceEventListItem>
