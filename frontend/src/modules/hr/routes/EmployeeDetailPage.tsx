import { useEffect, useMemo } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  DataTable,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrAssignments, useHrContracts, useHrEmployee } from '../hooks/useHr'
import type { HrAssignment, HrContract } from '../model/hr.types'
import { contractUiPolicy } from '../services/contractUiPolicy.service'
import { employeeUiPolicy } from '../services/employeeUiPolicy.service'
import { getHrErrorMessage } from '../services/hrError.service'
import {
  buildEmployeeLabel,
  formatCurrencyAmount,
  formatDateLabel,
  formatDateRange,
} from '../services/hrReadModel.service'
import {
  saveRecentHrContract,
  saveRecentHrEmployee,
} from '../services/recentHrLookups.service'

export function EmployeeDetailPage() {
  const navigate = useNavigate()
  const { employeeId: employeeIdParam } = useParams<{ employeeId: string }>()
  const employeeId = Number(employeeIdParam)
  const principal = usePrincipal()
  const canReadEmployee = employeeUiPolicy.canOpenEmployeeDetail(principal)
  const canReadContracts = employeeUiPolicy.canViewContracts(principal)
  const canReadAssignments = employeeUiPolicy.canViewAssignments(principal)
  const canReadSensitiveContractFields = contractUiPolicy.canViewSensitiveFields(principal)

  const employeeQuery = useHrEmployee(employeeId, {
    enabled: canReadEmployee && Number.isFinite(employeeId),
  })
  const contractsQuery = useHrContracts(employeeId, {
    enabled: canReadEmployee && canReadContracts && Number.isFinite(employeeId),
  })
  const assignmentsQuery = useHrAssignments(employeeId, {
    enabled: canReadEmployee && canReadAssignments && Number.isFinite(employeeId),
  })

  useEffect(() => {
    if (employeeQuery.data) {
      saveRecentHrEmployee(employeeQuery.data)
    }
  }, [employeeQuery.data])

  useEffect(() => {
    if (!contractsQuery.data || !employeeQuery.data) {
      return
    }

    contractsQuery.data.forEach((contract) => {
      saveRecentHrContract(contract, {
        employeeCode: employeeQuery.data?.employeeCode,
        employeeName: employeeQuery.data?.fullName,
      })
    })
  }, [contractsQuery.data, employeeQuery.data])

  usePageTitle(employeeQuery.data ? `${employeeQuery.data.fullName} — HR` : 'Chi tiết nhân viên — HR')

  const contractColumns = useMemo<Array<DataTableColumn<HrContract>>>(
    () => [
      {
        key: 'contract',
        header: 'Contract',
        render: (contract) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>#{contract.id}</strong>
            <span className="muted-text">{contract.employmentType}</span>
          </div>
        ),
      },
      {
        key: 'status',
        header: 'Status',
        render: (contract) => <StatusBadge status={contract.contractStatus} />,
      },
      {
        key: 'salary',
        header: 'Base salary',
        render: (contract) =>
          canReadSensitiveContractFields ? formatCurrencyAmount(contract.baseSalary) : '••••••',
      },
      {
        key: 'dates',
        header: 'Effective dates',
        render: (contract) => formatDateRange(contract.startDate, contract.endDate),
      },
    ],
    [canReadSensitiveContractFields],
  )

  const assignmentColumns = useMemo<Array<DataTableColumn<HrAssignment>>>(
    () => [
      {
        key: 'assignment',
        header: 'Assignment',
        render: (assignment) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{assignment.positionTitle}</strong>
            <span className="muted-text">
              Region #{assignment.regionId} · Outlet #{assignment.outletId}
            </span>
          </div>
        ),
      },
      {
        key: 'primary',
        header: 'Primary',
        render: (assignment) => (assignment.primaryAssignment ? 'Yes' : 'No'),
      },
      {
        key: 'status',
        header: 'Status',
        render: (assignment) => <StatusBadge status={assignment.status} />,
      },
      {
        key: 'dates',
        header: 'Effective dates',
        render: (assignment) => formatDateRange(assignment.startDate, assignment.endDate),
      },
    ],
    [],
  )

  if (!canReadEmployee) {
    return (
      <DashboardLayout title="Chi tiết nhân viên" description="Inspect hồ sơ nhân viên và dữ liệu HR liên quan">
        <PermissionDeniedInline message="Bạn cần quyền hr.employee.read để xem chi tiết nhân viên." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(employeeId) || employeeId <= 0) {
    return (
      <DashboardLayout
        title="Chi tiết nhân viên"
        description="Inspect hồ sơ nhân viên và dữ liệu HR liên quan"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/employees">Quay lại danh sách</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa employeeId hợp lệ." title="Thiếu employeeId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (employeeQuery.isLoading) {
    return (
      <DashboardLayout title="Chi tiết nhân viên" description="Inspect hồ sơ nhân viên và dữ liệu HR liên quan">
        <EmptyState description="Đang tải hồ sơ nhân viên, hợp đồng và assignments..." title="Đang tải chi tiết nhân viên" />
      </DashboardLayout>
    )
  }

  if (employeeQuery.error) {
    return (
      <DashboardLayout
        title="Chi tiết nhân viên"
        description="Inspect hồ sơ nhân viên và dữ liệu HR liên quan"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/employees">Quay lại danh sách</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Tải lại"
          message={getHrErrorMessage(employeeQuery.error, 'Không thể tải hồ sơ nhân viên.')}
          onAction={() => void employeeQuery.refetch()}
          title="Không thể tải nhân viên"
        />
      </DashboardLayout>
    )
  }

  if (!employeeQuery.data) {
    return (
      <DashboardLayout
        title="Chi tiết nhân viên"
        description="Inspect hồ sơ nhân viên và dữ liệu HR liên quan"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/employees">Quay lại danh sách</Link>
          </Button>
        }
      >
        <EmptyState description="Nhân viên này không tồn tại hoặc không còn trong phạm vi hiện tại." title="Không tìm thấy nhân viên" />
      </DashboardLayout>
    )
  }

  const employee = employeeQuery.data

  return (
    <DashboardLayout
      title="Chi tiết nhân viên"
      description="Read-first inspection cho hồ sơ nhân viên, hợp đồng và assignments."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/hr/employees">Quay lại danh sách</Link>
        </Button>
      }
    >
      <ReadonlyBanner message="HR detail hiện publish ở chế độ read-first. Các thao tác edit/write chưa được mở ở bước này." />

      <EntityHeader
        actions={
          canReadContracts ? (
            <Button onClick={() => navigate(`/hr/contracts?employeeId=${employee.id}`)} size="sm" variant="secondary">
              Open contracts
            </Button>
          ) : null
        }
        eyebrow="HR / Employee"
        metadata={
          <>
            <span>{employee.employeeCode}</span>
            <span>Status: {employee.status}</span>
            <span>Hired: {formatDateLabel(employee.hiredAt)}</span>
            <span>User account: {employee.userAccountId ? `#${employee.userAccountId}` : 'Unlinked'}</span>
          </>
        }
        status={<StatusBadge status={employee.status} />}
        title={buildEmployeeLabel(employee)}
      />

      <FormSection description="Hồ sơ read-only phục vụ people operations và payroll preparation." title="Employee overview">
        <div className="meta-grid">
          <span>Gender: {employee.gender ?? 'Unknown'}</span>
          <span>Date of birth: {formatDateLabel(employee.dob)}</span>
          <span>Email: {employee.email ?? 'No email'}</span>
          <span>Phone: {employee.phone ?? 'No phone'}</span>
        </div>
      </FormSection>

      <FormSection description="Hợp đồng của nhân viên trong phạm vi hiện tại." title="Contracts">
        {canReadContracts ? (
          <DataTable
            columns={contractColumns}
            emptyDescription="Nhân viên này chưa có hợp đồng nào trong phạm vi hiện tại."
            emptyTitle="No contracts"
            error={contractsQuery.error ? getHrErrorMessage(contractsQuery.error, 'Không thể tải contracts.') : null}
            errorTitle="Không thể tải contracts"
            loading={contractsQuery.isLoading}
            loadingDescription="Đang tải contracts của nhân viên..."
            loadingTitle="Loading contracts"
            onRetry={() => void contractsQuery.refetch()}
            onRowClick={(contract) => navigate(`/hr/contracts/${contract.id}?employeeId=${employee.id}`)}
            rowKey={(contract) => contract.id}
            rows={contractsQuery.data ?? []}
          />
        ) : (
          <PermissionDeniedInline message="Bạn cần quyền hr.contract.read để xem danh sách hợp đồng của nhân viên." />
        )}
      </FormSection>

      <FormSection description="Assignment history để hiểu phạm vi làm việc và vị trí hiện tại." title="Assignments">
        {canReadAssignments ? (
          <DataTable
            columns={assignmentColumns}
            emptyDescription="Nhân viên này chưa có assignment nào trong phạm vi hiện tại."
            emptyTitle="No assignments"
            error={assignmentsQuery.error ? getHrErrorMessage(assignmentsQuery.error, 'Không thể tải assignments.') : null}
            errorTitle="Không thể tải assignments"
            loading={assignmentsQuery.isLoading}
            loadingDescription="Đang tải assignment history..."
            loadingTitle="Loading assignments"
            onRetry={() => void assignmentsQuery.refetch()}
            rowKey={(assignment) => assignment.id}
            rows={assignmentsQuery.data ?? []}
          />
        ) : (
          <PermissionDeniedInline message="Bạn cần quyền hr.shift.read để xem assignment history của nhân viên." />
        )}
      </FormSection>
    </DashboardLayout>
  )
}
