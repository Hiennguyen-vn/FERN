import { useQuery } from '@tanstack/react-query'
import { getAttendanceApproval, getAttendanceApprovals } from '../api/workforce.api'

export function useAttendanceApprovals(params: { outletId?: number; regionId?: number } | null) {
  return useQuery({
    enabled: params !== null,
    queryKey: ['workforce', 'attendance-approvals', params],
    queryFn: () => getAttendanceApprovals(params as { outletId?: number; regionId?: number }),
  })
}

export function useAttendanceApproval(shiftAssignmentId: number | null) {
  return useQuery({
    enabled: shiftAssignmentId !== null,
    queryKey: ['workforce', 'attendance-approvals', shiftAssignmentId],
    queryFn: () => getAttendanceApproval(shiftAssignmentId as number),
  })
}
