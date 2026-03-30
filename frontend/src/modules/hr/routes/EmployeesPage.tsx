import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrEmployee } from '../hooks/useHr'
import type { HrEmployee } from '../model/hr.types'
import { employeeUiPolicy } from '../services/employeeUiPolicy.service'
import { getHrErrorMessage } from '../services/hrError.service'
import { formatDateLabel, matchesSearch } from '../services/hrReadModel.service'
import {
  clearRecentHrEmployees,
  loadRecentHrEmployees,
  saveRecentHrEmployee,
} from '../services/recentHrLookups.service'

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'INACTIVE', value: 'INACTIVE' },
  { label: 'ON_LEAVE', value: 'ON_LEAVE' },
  { label: 'TERMINATED', value: 'TERMINATED' },
]

function matchesEmployeeSearch(employee: HrEmployee, search: string) {
  return matchesSearch(
    [employee.id, employee.employeeCode, employee.fullName, employee.email, employee.phone, employee.status],
    search,
  )
}

export function EmployeesPage() {
  usePageTitle('HR Employees')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canReadEmployees = employeeUiPolicy.canOpenEmployeesPage(principal)
  const [lookupInput, setLookupInput] = useState('')
  const [searchedEmployeeId, setSearchedEmployeeId] = useState<number | null>(null)
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [recentEmployees, setRecentEmployees] = useState<HrEmployee[]>(() => loadRecentHrEmployees())

  const employeeQuery = useHrEmployee(searchedEmployeeId ?? 0, {
    enabled: canReadEmployees && Boolean(searchedEmployeeId),
  })

  useEffect(() => {
    if (!employeeQuery.data) {
      return
    }

    saveRecentHrEmployee(employeeQuery.data)
    setRecentEmployees(loadRecentHrEmployees())
  }, [employeeQuery.data])

  const filteredRecentEmployees = useMemo(() => {
    return recentEmployees.filter((employee) => {
      const matchesStatus = statusFilter === 'ALL' || employee.status === statusFilter
      return matchesStatus && matchesEmployeeSearch(employee, searchText)
    })
  }, [recentEmployees, searchText, statusFilter])

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

  function submitLookup() {
    const parsed = Number(lookupInput)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập employee ID hợp lệ để lookup.')
      return
    }

    setValidationError(null)
    setSearchedEmployeeId(parsed)
  }

  if (!canReadEmployees) {
    return (
      <DashboardLayout title="HR Employees" description="Lookup-first browse cho hồ sơ nhân viên">
        <PermissionDeniedInline message="Bạn cần quyền hr.employee.read để tra cứu và inspect hồ sơ nhân viên." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Nhân viên"
      description="Lookup-first employee workspace vì backend hiện chỉ publish employee detail endpoint."
    >
      <ReadonlyBanner message="HR Employees đang publish ở chế độ read-first. Lookup theo employee ID để inspect hồ sơ, hợp đồng và assignment." />

      <Card title="Lookup employee">
        <div className="field-grid">
          <Input
            label="Employee ID"
            onChange={(event) => setLookupInput(event.target.value)}
            placeholder="VD: 1001"
            type="number"
            value={lookupInput}
          />
          <Input
            label="Tìm trong recent employees"
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Mã, tên, email, số điện thoại..."
            value={searchText}
          />
          <Select
            label="Status filter"
            onChange={(event) => setStatusFilter(event.target.value)}
            options={statusOptions}
            value={statusFilter}
          />
        </div>
        <div className="form-actions align-start">
          <Button onClick={submitLookup} size="sm">
            Lookup employee
          </Button>
          {recentEmployees.length > 0 ? (
            <Button
              onClick={() => {
                clearRecentHrEmployees()
                setRecentEmployees([])
              }}
              size="sm"
              variant="ghost"
            >
              Clear recent
            </Button>
          ) : null}
        </div>
        {validationError ? <p className="error-text">{validationError}</p> : null}
      </Card>

      {searchedEmployeeId ? (
        employeeQuery.isLoading ? (
          <Card title="Đang lookup employee">
            <p className="muted-text">Đang tải hồ sơ nhân viên #{searchedEmployeeId}...</p>
          </Card>
        ) : employeeQuery.error ? (
          <ErrorState
            actionLabel="Retry lookup"
            message={getHrErrorMessage(employeeQuery.error, `Không thể tải employee #${searchedEmployeeId}.`)}
            onAction={() => void employeeQuery.refetch()}
            title="Lookup failed"
          />
        ) : employeeQuery.data ? (
          <Card title="Latest lookup">
            <div className="meta-grid">
              <span>{employeeQuery.data.employeeCode}</span>
              <span>{employeeQuery.data.fullName}</span>
              <span>Status: {employeeQuery.data.status}</span>
            </div>
            <div className="form-actions align-start">
              <Button onClick={() => navigate(`/hr/employees/${employeeQuery.data!.id}`)} size="sm" variant="secondary">
                Open employee detail
              </Button>
            </div>
          </Card>
        ) : null
      ) : null}

      {recentEmployees.length === 0 ? (
        <EmptyState
          description="Chưa có employee nào được inspect gần đây. Dùng employee ID để lookup và build recent list."
          title="No recent employees yet"
        />
      ) : (
        <DataTable
          columns={columns}
          emptyDescription="Không có recent employee nào khớp bộ lọc hiện tại."
          emptyTitle="No matching recent employees"
          onRowClick={(employee) => navigate(`/hr/employees/${employee.id}`)}
          rowKey={(employee) => employee.id}
          rows={filteredRecentEmployees}
        />
      )}
    </DashboardLayout>
  )
}
