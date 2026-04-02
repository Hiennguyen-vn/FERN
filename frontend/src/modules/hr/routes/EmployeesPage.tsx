import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Badge,
  Button,
  DataTable,
  PermissionDeniedInline,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrEmployees } from '../hooks/useHr'
import type { HrEmployee } from '../model/hr.types'
import { employeeUiPolicy } from '../services/employeeUiPolicy.service'
import { canWriteEmployees } from '../services/hrPermission.service'
import { formatDateLabel } from '../services/hrReadModel.service'

const statusOptions: SelectOption[] = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'INACTIVE', value: 'INACTIVE' },
  { label: 'ON_LEAVE', value: 'ON_LEAVE' },
  { label: 'TERMINATED', value: 'TERMINATED' },
]

const PAGE_SIZE = 50

function getRecordHealth(employee: HrEmployee) {
  if (employee.email && employee.phone) {
    return { label: 'Healthy', tone: 'success' as const }
  }

  if (employee.email || employee.phone) {
    return { label: 'Warning', tone: 'warning' as const }
  }

  return { label: 'Missing records', tone: 'danger' as const }
}

export function EmployeesPage() {
  usePageTitle('Employee Master')

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

  const rows = employeesQuery.data?.items ?? []
  const hasMore = employeesQuery.data?.hasMore ?? false
  const canPrev = page > 0
  const canNext = hasMore
  const activeEmployees = rows.filter((employee) => employee.status === 'ACTIVE').length
  const onLeaveEmployees = rows.filter((employee) => employee.status === 'ON_LEAVE').length
  const missingRecords = rows.filter((employee) => getRecordHealth(employee).tone === 'danger').length

  const columns = useMemo<Array<DataTableColumn<HrEmployee>>>(
    () => [
      {
        key: 'employeeCode',
        header: 'Emp Code',
        render: (employee) => <span className="cell-kicker">{employee.employeeCode}</span>,
      },
      {
        key: 'identity',
        header: 'Employee Name',
        render: (employee) => (
          <div className="table-identity">
            <span className="table-avatar">
              <AppIcon filled name="badge" size="sm" />
            </span>
            <div className="cell-stack">
              <strong>{employee.fullName}</strong>
              <span className="cell-subtitle">{employee.email ?? employee.phone ?? 'No contact information'}</span>
            </div>
          </div>
        ),
      },
      {
        key: 'directory',
        header: 'Directory Profile',
        render: (employee) => (
          <div className="cell-stack">
            <strong>{employee.userAccountId ? `User #${employee.userAccountId}` : 'Unlinked profile'}</strong>
            <span className="cell-subtitle">{employee.phone ?? employee.email ?? 'No direct channel'}</span>
          </div>
        ),
      },
      {
        key: 'status',
        header: 'Employment Status',
        render: (employee) => <StatusBadge status={employee.status} />,
      },
      {
        key: 'health',
        header: 'Record Health',
        render: (employee) => {
          const health = getRecordHealth(employee)
          return (
            <span className="health-line">
              <span className={`health-dot ${health.tone}`} />
              {health.label}
            </span>
          )
        },
      },
      {
        key: 'hiredAt',
        header: 'Joined',
        render: (employee) => formatDateLabel(employee.hiredAt),
      },
    ],
    [],
  )

  if (!canReadEmployees) {
    return (
      <DashboardLayout
        description="Workforce directory for employee records, contract health, and drill-down to people operations."
        eyebrow="HR Management"
        title="Employee Master"
      >
        <PermissionDeniedInline message="You need hr.employee.read to open the employee master workspace." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        canCreateEmployees ? (
          <Button asChild size="sm">
            <Link to="/hr/employees/new">New staff entry</Link>
          </Button>
        ) : null
      }
      description="Workforce directory for employee records, contract health, and drill-down to people operations."
      eyebrow="HR Management"
      title="Employee Master"
    >
      <section className="workspace-stats-grid">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="groups" />
            </span>
            <span className="workspace-stat-badge success">Current slice</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">Loaded workforce</span>
            <strong className="workspace-stat-value">{rows.length.toLocaleString('en-US')}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="verified" />
            </span>
            <span className="workspace-stat-badge success">Healthy</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">Active staff</span>
            <strong className="workspace-stat-value">{activeEmployees.toLocaleString('en-US')}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="schedule" />
            </span>
            <span className="workspace-stat-badge warning">Monitor</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">On leave</span>
            <strong className="workspace-stat-value">{onLeaveEmployees.toLocaleString('en-US')}</strong>
          </div>
        </article>
        <article className="workspace-stat-card danger">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="warning" />
            </span>
            <span className="workspace-stat-badge danger">Action</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">Missing records</span>
            <strong className="workspace-stat-value">{missingRecords.toLocaleString('en-US')}</strong>
          </div>
        </article>
      </section>

      <section className="workspace-filter-bar" aria-label="Employee master filters">
        <div className="workspace-inline-search">
          <AppIcon name="search" size="sm" />
          <input
            className="workspace-inline-input"
            onChange={(event) => {
              setSearchText(event.target.value)
              setPage(0)
            }}
            placeholder="Search by employee name, code, or phone number..."
            value={searchText}
          />
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Employment status</span>
          <select
            className="workspace-inline-select"
            onChange={(event) => {
              setStatusFilter(event.target.value)
              setPage(0)
            }}
            value={statusFilter}
          >
            {statusOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Actions</span>
          <span className="workspace-inline-pill">
            <AppIcon name="download" size="sm" />
            Export disabled
          </span>
        </div>
      </section>

      <section className="surface-panel">
        <div className="page-header">
          <div>
            <h2 className="card-title">Employee Directory</h2>
            <p className="muted-text">Showing the current page of HR employee records and operational health signals.</p>
          </div>
          <Badge>Page {page + 1}</Badge>
        </div>

        <DataTable
          canNext={canNext}
          canPrevious={canPrev}
          columns={columns}
          currentPage={page}
          emptyDescription="No employees match the current search and status filters."
          emptyTitle="No matching employees"
          error={employeesQuery.error ? (employeesQuery.error instanceof Error ? employeesQuery.error.message : 'Unable to load employees.') : null}
          loading={employeesQuery.isLoading}
          loadingDescription="Loading the employee master slice..."
          loadingTitle="Loading employee directory"
          onNext={() => setPage((current) => current + 1)}
          onPrevious={() => setPage((current) => Math.max(0, current - 1))}
          onRetry={() => void employeesQuery.refetch()}
          onRowClick={(employee) => navigate(`/hr/employees/${employee.id}`)}
          rowKey={(employee) => employee.id}
          rows={rows}
        />
      </section>
    </DashboardLayout>
  )
}
