import { gatewayClient } from '@core/api/gatewayClient'
import type { PageResponse } from '@core/types/api'
import type {
  CreatePayrollPeriodPayload,
  CreatePayrollRunPayload,
  CreateShiftAssignmentPayload,
  CreateShiftSchedulePayload,
  HrAssignment,
  HrAttendanceApproval,
  HrAttendanceEventPage,
  HrAttendanceFilters,
  HrContract,
  HrEmployee,
  PayrollPeriod,
  PayrollRun,
  ShiftAssignment,
  ShiftSchedule,
} from '../model/hr.types'

export const hrApi = {
  listEmployees(params?: { search?: string; status?: string; page?: number; size?: number }) {
    return gatewayClient.get<PageResponse<HrEmployee>>('/employees', { params }).then((response) => response.data)
  },

  getEmployee(employeeId: number) {
    return gatewayClient.get<HrEmployee>(`/employees/${employeeId}`).then((response) => response.data)
  },

  listContracts(params?: { employeeId?: number; regionId?: number; search?: string; status?: string; page?: number; size?: number }) {
    return gatewayClient.get<PageResponse<HrContract>>('/employee-contracts', { params }).then((response) => response.data)
  },

  listEmployeeContracts(employeeId: number) {
    return gatewayClient.get<HrContract[]>(`/employees/${employeeId}/contracts`).then((response) => response.data)
  },

  async getEmployeeContract(employeeId: number, contractId: number) {
    const contracts = await hrApi.listEmployeeContracts(employeeId)
    return contracts.find((contract) => contract.id === contractId) ?? null
  },

  listEmployeeAssignments(employeeId: number) {
    return gatewayClient.get<HrAssignment[]>(`/employees/${employeeId}/assignments`).then((response) => response.data)
  },

  listAttendanceEvents(filters: HrAttendanceFilters) {
    return gatewayClient
      .get<HrAttendanceEventPage>('/attendance-events', { params: filters })
      .then((response) => response.data)
  },

  listAttendanceApprovals(params: { outletId?: number; regionId?: number }) {
    return gatewayClient
      .get<HrAttendanceApproval[]>('/attendance-approvals', { params })
      .then((response) => response.data)
  },

  listPayrollPeriods(regionId?: number) {
    return gatewayClient
      .get<PayrollPeriod[]>('/payroll-periods', { params: regionId ? { regionId } : undefined })
      .then((response) => response.data)
  },

  createPayrollPeriod(payload: CreatePayrollPeriodPayload) {
    return gatewayClient.post<PayrollPeriod>('/payroll-periods', payload).then((response) => response.data)
  },

  listPayrollRuns(regionId?: number) {
    return gatewayClient
      .get<PayrollRun[]>('/payroll-runs', { params: regionId ? { regionId } : undefined })
      .then((response) => response.data)
  },

  getPayrollRun(runId: number) {
    return gatewayClient.get<PayrollRun>(`/payroll-runs/${runId}`).then((response) => response.data)
  },

  createPayrollRun(payload: CreatePayrollRunPayload) {
    return gatewayClient.post<PayrollRun>('/payroll-runs', payload).then((response) => response.data)
  },

  // ── Shift Schedules ──────────────────────────────────────
  listShiftSchedules(params?: { outletId?: number; fromDate?: string; toDate?: string }) {
    return gatewayClient
      .get<ShiftSchedule[]>('/shift-schedules', { params })
      .then((response) => response.data)
  },

  getShiftSchedule(id: number) {
    return gatewayClient.get<ShiftSchedule>(`/shift-schedules/${id}`).then((response) => response.data)
  },

  createShiftSchedule(payload: CreateShiftSchedulePayload) {
    return gatewayClient.post<ShiftSchedule>('/shift-schedules', payload).then((response) => response.data)
  },

  // ── Shift Assignments ────────────────────────────────────
  listShiftAssignments(shiftScheduleId: number) {
    return gatewayClient
      .get<ShiftAssignment[]>('/shift-assignments', { params: { shiftScheduleId } })
      .then((response) => response.data)
  },

  getShiftAssignment(id: number) {
    return gatewayClient.get<ShiftAssignment>(`/shift-assignments/${id}`).then((response) => response.data)
  },

  createShiftAssignment(payload: CreateShiftAssignmentPayload) {
    return gatewayClient.post<ShiftAssignment>('/shift-assignments', payload).then((response) => response.data)
  },
}
