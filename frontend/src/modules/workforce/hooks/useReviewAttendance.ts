import { useMutation, useQueryClient } from '@tanstack/react-query'
import { reviewAttendance } from '../api/workforce.api'

export function useReviewAttendance() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['workforce', 'attendance-approvals', 'review'],
    mutationFn: ({
      action,
      comments,
      shiftAssignmentId,
    }: {
      action: 'approve' | 'reject'
      comments?: string
      shiftAssignmentId: number
    }) => reviewAttendance(shiftAssignmentId, action, comments),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['workforce', 'attendance-approvals'] })
      void queryClient.invalidateQueries({
        queryKey: ['workforce', 'attendance-approvals', variables.shiftAssignmentId],
      })
    },
  })
}
