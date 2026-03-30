import type { PageResponse } from '@core/types/api'

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

export interface AttendanceApproval {
  id: number
  shiftAssignmentId: number
  status: string
  comments: string | null
  approvedAt: string | null
  approvedByUserId: number | null
  attendanceStatus: string
  workHours: string | null
  overtimeHours: string | null
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
