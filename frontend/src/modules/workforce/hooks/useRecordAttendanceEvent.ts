import { useMutation, useQueryClient } from '@tanstack/react-query'
import { recordAttendanceEvent } from '../api/workforce.api'

export function useRecordAttendanceEvent() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['workforce', 'attendance-events', 'record'],
    mutationFn: recordAttendanceEvent,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['workforce', 'attendance-events'] })
    },
  })
}
