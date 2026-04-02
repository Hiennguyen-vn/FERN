import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  AssignmentCreatePage,
  AttendanceSummaryPage,
  ContractDetailPage,
  ContractCreatePage,
  ContractsPage,
  EmployeeCreatePage,
  EmployeeDetailPage,
  EmployeesPage,
  PayrollDraftReviewPage,
  PayrollPreparationPage,
  ShiftSchedulingPage,
} from '../routes/hrRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useCreateEmployee: vi.fn(),
  useCreateContract: vi.fn(),
  useCreateEmployeeAssignment: vi.fn(),
  useHrAttendanceApprovals: vi.fn(),
  useHrAttendanceEvents: vi.fn(),
  useHrAssignments: vi.fn(),
  useHrContractBrowse: vi.fn(),
  useHrEmployees: vi.fn(),
  useHrContracts: vi.fn(),
  useHrEmployee: vi.fn(),
  usePayrollPeriods: vi.fn(),
  usePayrollRun: vi.fn(),
  usePayrollRuns: vi.fn(),
  useCreatePayrollPeriod: vi.fn(),
  useCreatePayrollRun: vi.fn(),
  useSubmitPayrollRun: vi.fn(),
}))

vi.mock('../hooks/useHr', () => ({
  useHrAttendanceApprovals: mocks.useHrAttendanceApprovals,
  useHrAttendanceEvents: mocks.useHrAttendanceEvents,
  useHrAssignments: mocks.useHrAssignments,
  useHrContractBrowse: mocks.useHrContractBrowse,
  useHrEmployees: mocks.useHrEmployees,
  useHrContracts: mocks.useHrContracts,
  useHrEmployee: mocks.useHrEmployee,
  usePayrollPeriods: mocks.usePayrollPeriods,
  usePayrollRun: mocks.usePayrollRun,
  usePayrollRuns: mocks.usePayrollRuns,
  useCreateEmployee: mocks.useCreateEmployee,
  useCreateContract: mocks.useCreateContract,
  useCreateEmployeeAssignment: mocks.useCreateEmployeeAssignment,
  useCreatePayrollPeriod: mocks.useCreatePayrollPeriod,
  useCreatePayrollRun: mocks.useCreatePayrollRun,
  useSubmitPayrollRun: mocks.useSubmitPayrollRun,
}))

function HrRoutesHarness() {
  return (
    <Routes>
        <Route path="/hr" element={<LazyRouteBoundary moduleName="HR" label="Loading HR workspace" />}>
        <Route path="employees" element={<EmployeesPage />} />
        <Route path="employees/new" element={<EmployeeCreatePage />} />
        <Route path="employees/:employeeId" element={<EmployeeDetailPage />} />
        <Route path="contracts" element={<ContractsPage />} />
        <Route path="contracts/new" element={<ContractCreatePage />} />
        <Route path="contracts/:contractId" element={<ContractDetailPage />} />
        <Route path="assignments/new" element={<AssignmentCreatePage />} />
        <Route path="attendance-summary" element={<AttendanceSummaryPage />} />
        <Route path="payroll-preparation" element={<PayrollPreparationPage />} />
        <Route path="payroll-draft-review/:runId" element={<PayrollDraftReviewPage />} />
        <Route path="shift-scheduling" element={<ShiftSchedulingPage />} />
      </Route>
    </Routes>
  )
}

describe('HR route group', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.hr.employeeRead,
          permissionConstants.hr.employeeWrite,
          permissionConstants.hr.contractRead,
          permissionConstants.hr.contractDetailRead,
          permissionConstants.hr.contractWrite,
          permissionConstants.hr.shiftRead,
          permissionConstants.hr.shiftWrite,
          permissionConstants.hr.attendanceReview,
          permissionConstants.finance.payrollRead,
          permissionConstants.finance.payrollPrepare,
          permissionConstants.finance.payrollDetailRead,
        ],
      },
    })

    mocks.useHrEmployees.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            employeeCode: 'EMP-001',
            fullName: 'Nguyen Van A',
            dob: '1994-01-01',
            gender: 'MALE',
            email: 'a@fern.local',
            phone: '0901',
            status: 'ACTIVE',
            hiredAt: '2024-01-01',
            userAccountId: 7,
          },
        ],
        page: 0,
        size: 50,
        hasMore: false,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrEmployee.mockReturnValue({
      data: {
        id: 1,
        employeeCode: 'EMP-001',
        fullName: 'Nguyen Van A',
        dob: '1994-01-01',
        gender: 'MALE',
        email: 'a@fern.local',
        phone: '0901',
        status: 'ACTIVE',
        hiredAt: '2024-01-01',
        userAccountId: 7,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrContractBrowse.mockReturnValue({
      data: {
        items: [
          {
            id: 10,
            employeeId: 1,
            employmentType: 'FULL_TIME',
            salaryType: 'MONTHLY',
            baseSalary: 18000000,
            regionId: 1,
            taxCode: 'TX-01',
            contractStatus: 'ACTIVE',
            startDate: '2024-01-01',
            endDate: null,
          },
        ],
        page: 0,
        size: 50,
        hasMore: false,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrContracts.mockReturnValue({
      data: [
        {
          id: 10,
          employeeId: 1,
          employmentType: 'FULL_TIME',
          salaryType: 'MONTHLY',
          baseSalary: 18000000,
          regionId: 1,
          taxCode: 'TX-01',
          contractStatus: 'ACTIVE',
          startDate: '2024-01-01',
          endDate: null,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrAssignments.mockReturnValue({
      data: [
        {
          id: 5,
          employeeId: 1,
          regionId: 1,
          outletId: 101,
          positionTitle: 'Store Manager',
          startDate: '2024-01-01',
          endDate: null,
          primaryAssignment: true,
          status: 'ACTIVE',
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrAttendanceEvents.mockReturnValue({
      data: { items: [], page: 0, size: 100, hasMore: false },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useHrAttendanceApprovals.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.usePayrollPeriods.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.usePayrollRuns.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.usePayrollRun.mockReturnValue({
      data: {
        id: 88,
        payrollPeriodId: 11,
        runCode: 'PR-202603',
        runDate: '2026-03-29',
        status: 'DRAFT',
        totalAmount: 50000000,
        paymentRef: null,
        note: 'Draft',
        submittedAt: null,
        approvedAt: null,
        paidAt: null,
        employees: [],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useCreatePayrollPeriod.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
    mocks.useCreatePayrollRun.mockReturnValue({ mutateAsync: vi.fn(), isPending: false })
    mocks.useSubmitPayrollRun.mockReturnValue({ mutateAsync: vi.fn(), isPending: false, error: null })
    mocks.useCreateEmployee.mockReturnValue({ mutateAsync: vi.fn(), isPending: false, error: null })
    mocks.useCreateContract.mockReturnValue({ mutateAsync: vi.fn(), isPending: false, error: null })
    mocks.useCreateEmployeeAssignment.mockReturnValue({ mutateAsync: vi.fn(), isPending: false, error: null })
  })

  it.each([
    ['/hr/employees', 'Employee Master'],
    ['/hr/employees/new', 'Create Employee'],
    ['/hr/employees/1', 'Employee Profile'],
    ['/hr/contracts?employeeId=1', 'Hợp đồng'],
    ['/hr/contracts/new', 'Create Contract'],
    ['/hr/contracts/10?employeeId=1', 'Chi tiết hợp đồng'],
    ['/hr/assignments/new', 'Create Assignment'],
    ['/hr/attendance-summary', 'Attendance Summary'],
    ['/hr/payroll-preparation', 'Payroll Preparation'],
    ['/hr/payroll-draft-review/88', 'Payroll Draft Review'],
    ['/hr/shift-scheduling', 'Shift Scheduling'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<HrRoutesHarness />, { route })

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
