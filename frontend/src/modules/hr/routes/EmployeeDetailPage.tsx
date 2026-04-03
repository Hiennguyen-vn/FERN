import { useEffect, useMemo } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  MaskedField,
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

function getPrimaryAssignment(assignments: HrAssignment[]) {
  return assignments.find((assignment) => assignment.primaryAssignment) ?? assignments[0] ?? null
}

function getPrimaryContract(contracts: HrContract[]) {
  return (
    contracts.find((contract) => contract.contractStatus.toUpperCase() === 'ACTIVE') ??
    contracts.find((contract) => contract.contractStatus.toUpperCase() === 'SIGNED') ??
    contracts[0] ??
    null
  )
}

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

  const contracts = contractsQuery.data ?? []
  const assignments = assignmentsQuery.data ?? []
  const primaryAssignment = getPrimaryAssignment(assignments)
  const primaryContract = getPrimaryContract(contracts)
  const profileCompleteness = [employeeQuery.data?.email, employeeQuery.data?.phone, employeeQuery.data?.userAccountId].filter(Boolean).length

  usePageTitle(employeeQuery.data ? `${employeeQuery.data.fullName} Employee Profile` : 'Employee Profile')

  const contractColumns = useMemo<Array<DataTableColumn<HrContract>>>(
    () => [
      {
        key: 'contract',
        header: 'Contract',
        render: (contract) => (
          <div className="cell-stack">
            <strong>#{contract.id}</strong>
            <span className="cell-subtitle">{contract.employmentType}</span>
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
        header: 'Base Salary',
        render: (contract) =>
          canReadSensitiveContractFields ? formatCurrencyAmount(contract.baseSalary) : 'Masked by policy',
      },
      {
        key: 'dates',
        header: 'Effective Window',
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
          <div className="cell-stack">
            <strong>{assignment.positionTitle}</strong>
            <span className="cell-subtitle">
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
        header: 'Effective Window',
        render: (assignment) => formatDateRange(assignment.startDate, assignment.endDate),
      },
    ],
    [],
  )

  if (!canReadEmployee) {
    return (
      <DashboardLayout
        description="Inspect employee profile, assignments, and HR contract coverage."
        eyebrow="HR Management"
        title="Employee Profile"
      >
        <PermissionDeniedInline message="You need hr.employee.read to inspect employee profiles." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(employeeId) || employeeId <= 0) {
    return (
      <DashboardLayout
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/employees">Back to employees</Link>
          </Button>
        }
        description="Inspect employee profile, assignments, and HR contract coverage."
        eyebrow="HR Management"
        title="Employee Profile"
      >
        <EmptyState description="The current URL does not include a valid employee ID." title="Missing employee ID" />
      </DashboardLayout>
    )
  }

  if (employeeQuery.isLoading) {
    return (
      <DashboardLayout
        description="Inspect employee profile, assignments, and HR contract coverage."
        eyebrow="HR Management"
        title="Employee Profile"
      >
        <Card title="Loading employee profile">
          <p className="muted-text">Loading employee profile, contracts, and assignment history...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (employeeQuery.error) {
    return (
      <DashboardLayout
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/employees">Back to employees</Link>
          </Button>
        }
        description="Inspect employee profile, assignments, and HR contract coverage."
        eyebrow="HR Management"
        title="Employee Profile"
      >
        <ErrorState
          actionLabel="Retry"
          message={getHrErrorMessage(employeeQuery.error, 'Unable to load the employee profile.')}
          onAction={() => void employeeQuery.refetch()}
          title="Unable to load employee profile"
        />
      </DashboardLayout>
    )
  }

  if (!employeeQuery.data) {
    return (
      <DashboardLayout
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/employees">Back to employees</Link>
          </Button>
        }
        description="Inspect employee profile, assignments, and HR contract coverage."
        eyebrow="HR Management"
        title="Employee Profile"
      >
        <EmptyState description="This employee could not be found in the current HR scope." title="Employee not found" />
      </DashboardLayout>
    )
  }

  const employee = employeeQuery.data

  return (
    <DashboardLayout
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/employees">Back to employees</Link>
          </Button>
          {canReadContracts ? (
            <Button onClick={() => navigate(`/hr/contracts?employeeId=${employee.id}`)} size="sm" variant="secondary">
              Open contracts
            </Button>
          ) : null}
        </div>
      }
      description="Inspect employee profile, assignments, and HR contract coverage."
      eyebrow="HR Management"
      title="Employee Profile"
    >
      <ReadonlyBanner
        label="Read-first workspace"
        message="Employee detail is currently published for inspection, contract review, and assignment drill-down. Editing remains outside this pass."
        title="HR profile is currently in read-first mode"
        tone="warning"
      />

      <section className="detail-hero-grid">
        <article className="surface-panel detail-hero-card">
          <div className="detail-hero-body">
            <div className="detail-media-frame">
              <AppIcon name="person" size="lg" />
            </div>
            <div className="detail-summary-copy">
              <div className="entity-header-title">
                <h2>{employee.fullName}</h2>
                <StatusBadge status={employee.status} />
              </div>
              <p className="muted-text">
                Employee ID: <strong>{employee.employeeCode}</strong>
              </p>
              <div className="key-value-list">
                <div className="key-value-row">
                  <span>Email</span>
                  <strong>{employee.email ?? 'No email'}</strong>
                </div>
                <div className="key-value-row">
                  <span>Phone</span>
                  <strong>{employee.phone ?? 'No phone'}</strong>
                </div>
                <div className="key-value-row">
                  <span>Joined</span>
                  <strong>{formatDateLabel(employee.hiredAt)}</strong>
                </div>
              </div>
            </div>
          </div>
        </article>

        <aside className="detail-side-stack">
          <Card className="detail-side-card" title="Assignment & Status">
            <div className="detail-side-list">
              <div className="detail-side-row">
                <span>Region</span>
                <strong>{primaryAssignment ? `Region #${primaryAssignment.regionId}` : 'Unassigned'}</strong>
              </div>
              <div className="detail-side-row">
                <span>Outlet</span>
                <strong>{primaryAssignment ? `Outlet #${primaryAssignment.outletId}` : 'Unassigned'}</strong>
              </div>
              <div className="detail-side-row">
                <span>Current role</span>
                <strong>{primaryAssignment?.positionTitle ?? 'No assignment yet'}</strong>
              </div>
              <div className="detail-side-row">
                <span>User account</span>
                <strong>{employee.userAccountId ? `Linked #${employee.userAccountId}` : 'Unlinked'}</strong>
              </div>
            </div>
          </Card>
        </aside>
      </section>

      <section className="surface-grid">
        <div className="surface-grid-main">
          <Card title="Contract Snapshot">
            {primaryContract ? (
              <>
                <div className="key-value-list">
                  <div className="key-value-row">
                    <span>Reference</span>
                    <strong>#{primaryContract.id}</strong>
                  </div>
                  <div className="key-value-row">
                    <span>Employment type</span>
                    <strong>{primaryContract.employmentType}</strong>
                  </div>
                  <div className="key-value-row">
                    <span>Effective window</span>
                    <strong>{formatDateRange(primaryContract.startDate, primaryContract.endDate)}</strong>
                  </div>
                </div>
                <div className="workspace-stats-grid section-spacing-top">
                  <MaskedField
                    helperText={canReadSensitiveContractFields ? 'Visible in read-only mode' : 'Masked by policy'}
                    label="Base salary"
                    mode={canReadSensitiveContractFields ? 'readonly-visible' : 'masked'}
                    value={formatCurrencyAmount(primaryContract.baseSalary)}
                  />
                  <MaskedField
                    helperText={canReadSensitiveContractFields ? 'Visible in read-only mode' : 'Masked by policy'}
                    label="Tax code"
                    mode={canReadSensitiveContractFields ? 'readonly-visible' : 'masked'}
                    value={primaryContract.taxCode ?? '—'}
                  />
                </div>
              </>
            ) : (
              <EmptyState description="No contract is available for this employee in the current scope." title="No contract snapshot" />
            )}
          </Card>

          <Card title="Contracts">
            {canReadContracts ? (
              <DataTable
                columns={contractColumns}
                emptyDescription="No contracts are available for this employee in the current scope."
                emptyTitle="No contracts"
                error={contractsQuery.error ? getHrErrorMessage(contractsQuery.error, 'Unable to load contracts.') : null}
                errorTitle="Unable to load contracts"
                loading={contractsQuery.isLoading}
                loadingDescription="Loading employee contracts..."
                loadingTitle="Loading contracts"
                onRetry={() => void contractsQuery.refetch()}
                onRowClick={(contract) => navigate(`/hr/contracts/${contract.id}?employeeId=${employee.id}`)}
                rowKey={(contract) => contract.id}
                rows={contracts}
              />
            ) : (
              <PermissionDeniedInline message="You need hr.contract.read to inspect employee contracts." />
            )}
          </Card>
        </div>

        <aside className="surface-grid-side">
          <section className="workspace-stats-grid">
            <article className="workspace-stat-card">
              <div className="workspace-stat-topline">
                <span className="workspace-stat-icon">
                  <AppIcon filled name="badge" />
                </span>
              </div>
              <div className="compact-stack">
                <span className="workspace-stat-label">Employee record</span>
                <strong className="workspace-stat-value">{buildEmployeeLabel(employee)}</strong>
              </div>
            </article>
            <article className="workspace-stat-card">
              <div className="workspace-stat-topline">
                <span className="workspace-stat-icon">
                  <AppIcon filled name="description" />
                </span>
              </div>
              <div className="compact-stack">
                <span className="workspace-stat-label">Contracts</span>
                <strong className="workspace-stat-value">{contracts.length}</strong>
              </div>
            </article>
            <article className="workspace-stat-card">
              <div className="workspace-stat-topline">
                <span className="workspace-stat-icon">
                  <AppIcon filled name="schedule" />
                </span>
              </div>
              <div className="compact-stack">
                <span className="workspace-stat-label">Assignments</span>
                <strong className="workspace-stat-value">{assignments.length}</strong>
              </div>
            </article>
            <article className="workspace-stat-card warning">
              <div className="workspace-stat-topline">
                <span className="workspace-stat-icon">
                  <AppIcon filled name="shield" />
                </span>
              </div>
              <div className="compact-stack">
                <span className="workspace-stat-label">Profile completeness</span>
                <strong className="workspace-stat-value">{profileCompleteness}/3</strong>
              </div>
            </article>
          </section>

          <Card title="Assignments">
            {canReadAssignments ? (
              <DataTable
                columns={assignmentColumns}
                emptyDescription="No assignments are available for this employee in the current scope."
                emptyTitle="No assignments"
                error={assignmentsQuery.error ? getHrErrorMessage(assignmentsQuery.error, 'Unable to load assignments.') : null}
                errorTitle="Unable to load assignments"
                loading={assignmentsQuery.isLoading}
                loadingDescription="Loading employee assignment history..."
                loadingTitle="Loading assignments"
                onRetry={() => void assignmentsQuery.refetch()}
                rowKey={(assignment) => assignment.id}
                rows={assignments}
              />
            ) : (
              <PermissionDeniedInline message="You need hr.shift.read to inspect employee assignments." />
            )}
          </Card>
        </aside>
      </section>
    </DashboardLayout>
  )
}
