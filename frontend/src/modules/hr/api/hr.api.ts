import { gatewayClient } from '@core/api/gatewayClient'
import type {
  CreatePayrollPeriodPayload,
  CreatePayrollRunPayload,
  HrAssignment,
  HrAttendanceApproval,
  HrAttendanceEventPage,
  HrAttendanceFilters,
  HrContract,
  HrEmployee,
  PayrollPeriod,
  PayrollRun,
} from '../model/hr.types'

export const hrApi = {
  getEmployee(employeeId: number) {
    return gatewayClient.get<HrEmployee>(`/employees/${employeeId}`).then((response) => response.data)
  },

  listEmployeeContracts(employeeId: number) {
    return gatewayClient.get<HrContract[]>(`/employees/${employeeId}/contracts`).then((response) => response.data)
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
}
