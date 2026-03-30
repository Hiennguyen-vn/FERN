import { useQuery } from '@tanstack/react-query'
import { getAttendanceEvents } from '../api/workforce.api'
import type { AttendanceEventFilters } from '../model/workforce.types'

export function useAttendanceEvents(filters: AttendanceEventFilters | null) {
  return useQuery({
    enabled: filters !== null,
    queryKey: ['workforce', 'attendance-events', filters],
    queryFn: () => getAttendanceEvents(filters as AttendanceEventFilters),
  })
}
