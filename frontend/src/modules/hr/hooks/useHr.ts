import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { financeApi } from '../../finance/api/finance.api'
import type { ReviewPayrollRunPayload } from '../../finance/model/finance.types'
import { hrApi } from '../api/hr.api'
import type {
  CreateAssignmentPayload,
  CreateContractPayload,
  CreateEmployeePayload,
  CreatePayrollPeriodPayload,
  CreatePayrollRunPayload,
  HrAttendanceFilters,
} from '../model/hr.types'

interface QueryOptions {
  enabled?: boolean
}

const KEYS = {
  employees: (params: { search?: string; status?: string; page?: number; size?: number }) => ['hr', 'employees', params] as const,
  employee: (employeeId: number) => ['hr', 'employees', employeeId] as const,
  contracts: (params: { employeeId?: number; regionId?: number; search?: string; status?: string; page?: number; size?: number }) =>
    ['hr', 'contracts', params] as const,
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

export function useHrEmployees(params: { search?: string; status?: string; page?: number; size?: number }, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.employees(params),
    queryFn: () => hrApi.listEmployees(params),
    enabled: options.enabled ?? true,
  })
}

export function useHrContracts(employeeId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.employeeContracts(employeeId),
    queryFn: () => hrApi.listEmployeeContracts(employeeId),
    enabled: (options.enabled ?? true) && employeeId > 0,
  })
}

export function useHrContractBrowse(
  params: { employeeId?: number; regionId?: number; search?: string; status?: string; page?: number; size?: number },
  options: QueryOptions = {},
) {
  return useQuery({
    queryKey: KEYS.contracts(params),
    queryFn: () => hrApi.listContracts(params),
    enabled: options.enabled ?? true,
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
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payrollPeriods'] })
    },
  })
}

export function useCreateEmployee() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['hr', 'employees', 'create'],
    mutationFn: (payload: CreateEmployeePayload) => hrApi.createEmployee(payload),
    onSuccess: (employee) => {
      void queryClient.invalidateQueries({ queryKey: ['hr', 'employees'] })
      void queryClient.invalidateQueries({ queryKey: KEYS.employee(employee.id) })
    },
  })
}

export function useCreateContract() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['hr', 'contracts', 'create'],
    mutationFn: (payload: CreateContractPayload) => hrApi.createContract(payload),
    onSuccess: (contract) => {
      void queryClient.invalidateQueries({ queryKey: ['hr', 'contracts'] })
      void queryClient.invalidateQueries({ queryKey: KEYS.employeeContracts(contract.employeeId) })
    },
  })
}

export function useCreateEmployeeAssignment() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['hr', 'assignments', 'create'],
    mutationFn: (payload: CreateAssignmentPayload) => hrApi.createEmployeeAssignment(payload),
    onSuccess: (assignment) => {
      void queryClient.invalidateQueries({ queryKey: KEYS.employeeAssignments(assignment.employeeId) })
      void queryClient.invalidateQueries({ queryKey: KEYS.employee(assignment.employeeId) })
      void queryClient.invalidateQueries({ queryKey: ['shift-schedules'] })
      void queryClient.invalidateQueries({ queryKey: ['shift-assignments'] })
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
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payroll-runs'] })
      void queryClient.invalidateQueries({ queryKey: ['reports', 'payroll'] })
    },
  })
}

function invalidatePayrollRunQueries(queryClient: ReturnType<typeof useQueryClient>, runId: number) {
  void queryClient.invalidateQueries({ queryKey: ['hr', 'payroll-runs'] })
  void queryClient.invalidateQueries({ queryKey: KEYS.payrollRun(runId) })
  void queryClient.invalidateQueries({ queryKey: ['finance', 'payroll-runs'] })
  void queryClient.invalidateQueries({ queryKey: ['finance', 'payroll-runs', runId] })
  void queryClient.invalidateQueries({ queryKey: ['reports', 'payroll'] })
}

export function useSubmitPayrollRun() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: ['hr', 'payroll-runs', 'submit'],
    mutationFn: ({ payload, runId }: { payload?: ReviewPayrollRunPayload; runId: number }) =>
      financeApi.submitPayrollRun(runId, payload),
    onSuccess: (run) => invalidatePayrollRunQueries(queryClient, run.id),
  })
}
