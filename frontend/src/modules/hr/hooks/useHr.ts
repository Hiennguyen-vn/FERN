import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { hrApi } from '../api/hr.api'
import type { CreatePayrollPeriodPayload, CreatePayrollRunPayload, HrAttendanceFilters } from '../model/hr.types'

interface QueryOptions {
  enabled?: boolean
}

const KEYS = {
  employee: (employeeId: number) => ['hr', 'employees', employeeId] as const,
  employeeContracts: (employeeId: number) => ['hr', 'employees', employeeId, 'contracts'] as const,
  employeeAssignments: (employeeId: number) => ['hr', 'employees', employeeId, 'assignments'] as const,
  attendanceEvents: (filters: HrAttendanceFilters) => ['hr', 'attendance-events', filters] as const,
  attendanceApprovals: (params: { outletId?: number; regionId?: number }) => ['hr', 'attendance-approvals', params] as const,
  payrollPeriods: (regionId?: number) => ['hr', 'payroll-periods', regionId ?? 'all'] as const,
  payrollRuns: (regionId?: number) => ['hr', 'payroll-runs', regionId ?? 'all'] as const,
  payrollRun: (runId: number) => ['hr', 'payroll-runs', runId] as const,
}

export function useHrEmployee(employeeId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.employee(employeeId),
    queryFn: () => hrApi.getEmployee(employeeId),
    enabled: (options.enabled ?? true) && employeeId > 0,
  })
}

export function useHrContracts(employeeId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.employeeContracts(employeeId),
    queryFn: () => hrApi.listEmployeeContracts(employeeId),
    enabled: (options.enabled ?? true) && employeeId > 0,
  })
}

export function useHrAssignments(employeeId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.employeeAssignments(employeeId),
    queryFn: () => hrApi.listEmployeeAssignments(employeeId),
    enabled: (options.enabled ?? true) && employeeId > 0,
  })
}

export function useHrAttendanceEvents(filters: HrAttendanceFilters, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.attendanceEvents(filters),
    queryFn: () => hrApi.listAttendanceEvents(filters),
    enabled: options.enabled ?? true,
  })
}

export function useHrAttendanceApprovals(
  params: { outletId?: number; regionId?: number },
  options: QueryOptions = {},
) {
  return useQuery({
    queryKey: KEYS.attendanceApprovals(params),
    queryFn: () => hrApi.listAttendanceApprovals(params),
    enabled: options.enabled ?? true,
  })
}

export function usePayrollPeriods(regionId?: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.payrollPeriods(regionId),
    queryFn: () => hrApi.listPayrollPeriods(regionId),
    enabled: options.enabled ?? true,
  })
}

export function usePayrollRuns(regionId?: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.payrollRuns(regionId),
    queryFn: () => hrApi.listPayrollRuns(regionId),
    enabled: options.enabled ?? true,
  })
}

export function usePayrollRun(runId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.payrollRun(runId),
    queryFn: () => hrApi.getPayrollRun(runId),
    enabled: (options.enabled ?? true) && runId > 0,
  })
}

export function useCreatePayrollPeriod() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['hr', 'payroll-periods', 'create'],
    mutationFn: (payload: CreatePayrollPeriodPayload) => hrApi.createPayrollPeriod(payload),
    onSuccess: (period) => {
      void queryClient.invalidateQueries({ queryKey: ['hr', 'payroll-periods'] })
      void queryClient.invalidateQueries({ queryKey: ['hr', 'payroll-runs', period.regionId] })
    },
  })
}

export function useCreatePayrollRun() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['hr', 'payroll-runs', 'create'],
    mutationFn: (payload: CreatePayrollRunPayload) => hrApi.createPayrollRun(payload),
    onSuccess: (run) => {
      void queryClient.invalidateQueries({ queryKey: ['hr', 'payroll-runs'] })
      void queryClient.invalidateQueries({ queryKey: KEYS.payrollRun(run.id) })
    },
  })
}
