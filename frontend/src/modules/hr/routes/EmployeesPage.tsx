import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  DataTable,
  Input,
  PermissionDeniedInline,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrEmployees } from '../hooks/useHr'
import type { HrEmployee } from '../model/hr.types'
import { employeeUiPolicy } from '../services/employeeUiPolicy.service'
import { getHrErrorMessage } from '../services/hrError.service'
import { canWriteEmployees } from '../services/hrPermission.service'
import { formatDateLabel } from '../services/hrReadModel.service'

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'INACTIVE', value: 'INACTIVE' },
  { label: 'ON_LEAVE', value: 'ON_LEAVE' },
  { label: 'TERMINATED', value: 'TERMINATED' },
]

const PAGE_SIZE = 50

export function EmployeesPage() {
  usePageTitle('HR Employees')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canReadEmployees = employeeUiPolicy.canOpenEmployeesPage(principal)
  const canCreateEmployees = canWriteEmployees(principal)
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [page, setPage] = useState(0)
  const employeesQuery = useHrEmployees(
    {
      search: searchText.trim() || undefined,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      page,
      size: PAGE_SIZE,
    },
    { enabled: canReadEmployees },
  )

  const hasMore = employeesQuery.data?.hasMore ?? false
  const canPrev = page > 0
  const canNext = hasMore

  const columns = useMemo<Array<DataTableColumn<HrEmployee>>>(
    () => [
      { key: 'employeeId', header: 'Employee ID', render: (employee) => `#${employee.id}` },
      {
        key: 'identity',
        header: 'Identity',
        render: (employee) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{employee.employeeCode}</strong>
            <span className="muted-text">{employee.fullName}</span>
          </div>
        ),
      },
      {
        key: 'contact',
        header: 'Contact',
        render: (employee) => employee.email ?? employee.phone ?? 'No contact info',
      },
      {
        key: 'status',
        header: 'Status',
        render: (employee) => <StatusBadge status={employee.status} />,
      },
      {
        key: 'hiredAt',
        header: 'Hired',
        render: (employee) => formatDateLabel(employee.hiredAt),
      },
    ],
    [],
  )

  if (!canReadEmployees) {
    return (
      <DashboardLayout title="HR Employees" description="Browse cho hồ sơ nhân viên">
        <PermissionDeniedInline message="Bạn cần quyền hr.employee.read để xem employee directory." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Nhân viên"
      description="Employee browse workspace cho HR master data và contract drill-down."
      actions={
        canCreateEmployees ? (
          <Button asChild size="sm">
            <Link to="/hr/employees/new">Create employee</Link>
          </Button>
        ) : null
      }
    >
      <div className="field-grid">
        <Input
          label="Search employees"
          onChange={(event) => { setSearchText(event.target.value); setPage(0) }}
          placeholder="Mã, tên, email, số điện thoại..."
          value={searchText}
        />
        <Select
          label="Status filter"
          onChange={(event) => { setStatusFilter(event.target.value); setPage(0) }}
          options={statusOptions}
          value={statusFilter}
        />
      </div>

      <DataTable
        canNext={canNext}
        canPrevious={canPrev}
        columns={columns}
        currentPage={page}
        emptyDescription="Không có employee nào khớp bộ lọc hiện tại."
        emptyTitle="No matching employees"
        error={employeesQuery.error ? getHrErrorMessage(employeesQuery.error, 'Không thể tải danh sách employee.') : null}
        loading={employeesQuery.isLoading}
        loadingDescription="Đang tải employee directory..."
        loadingTitle="Đang tải employees"
        onNext={() => setPage((p) => p + 1)}
        onPrevious={() => setPage((p) => Math.max(0, p - 1))}
        onRetry={() => void employeesQuery.refetch()}
        onRowClick={(employee) => navigate(`/hr/employees/${employee.id}`)}
        rowKey={(employee) => employee.id}
        rows={employeesQuery.data?.items ?? []}
      />
    </DashboardLayout>
  )
}
