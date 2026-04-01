import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { hrApi } from '../api/hr.api'
import type { CreateShiftAssignmentPayload, CreateShiftSchedulePayload } from '../model/hr.types'

const SHIFT_SCHEDULES_KEY = 'shift-schedules'
const SHIFT_ASSIGNMENTS_KEY = 'shift-assignments'

export function useShiftSchedules(params?: { outletId?: number; fromDate?: string; toDate?: string }) {
  return useQuery({
    queryKey: [SHIFT_SCHEDULES_KEY, params],
    queryFn: () => hrApi.listShiftSchedules(params),
    enabled: params !== undefined,
  })
}

export function useShiftAssignments(shiftScheduleId: number | null) {
  return useQuery({
    enabled: shiftScheduleId !== null,
    queryKey: [SHIFT_ASSIGNMENTS_KEY, shiftScheduleId],
    queryFn: () => hrApi.listShiftAssignments(shiftScheduleId!),
  })
}

export function useCreateShiftSchedule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateShiftSchedulePayload) => hrApi.createShiftSchedule(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [SHIFT_SCHEDULES_KEY] })
    },
  })
}

export function useCreateShiftAssignment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateShiftAssignmentPayload) => hrApi.createShiftAssignment(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [SHIFT_ASSIGNMENTS_KEY] })
    },
  })
}
