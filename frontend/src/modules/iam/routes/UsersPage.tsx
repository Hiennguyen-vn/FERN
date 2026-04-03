import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  DataTable,
  PermissionDeniedInline,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { IamUserStatusBadge } from '../components/IamStatusBadge'
import { useIamUsers } from '../hooks/useIam'
import type { IamUser, IamUserStatus } from '../model/iam.types'
import { getIamErrorMessage } from '../services/iamError.service'
import { iamUiPolicy } from '../services/iamUiPolicy.service'

const statusOptions: Array<{ label: string; value: IamUserStatus | 'ALL' }> = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Locked', value: 'LOCKED' },
  { label: 'Suspended', value: 'SUSPENDED' },
]

const PAGE_SIZE = 50

function formatActor(roleCodes: string[]) {
  if (roleCodes.some((role) => role.includes('admin'))) {
    return 'Admin'
  }
  if (roleCodes.some((role) => role.includes('manager'))) {
    return 'Manager'
  }
  if (roleCodes.length > 0) {
    return roleCodes[0].replace(/_/g, ' ')
  }
  return 'Unassigned'
}

function formatScope(user: IamUser) {
  if (user.scopeRoots.system) {
    return 'Global system'
  }
  if (user.scopeRoots.regions.length > 0) {
    return `Region ${user.scopeRoots.regions.join(', ')}`
  }
  if (user.scopeRoots.outlets.length > 0) {
    return `Outlet ${user.scopeRoots.outlets.join(', ')}`
  }
  return 'No scope assigned'
}

export function UsersPage() {
  usePageTitle('User Management')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canReadUsers = iamUiPolicy.canReadUsers(principal)
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState<IamUserStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(0)
  const usersQuery = useIamUsers(
    {
      search: searchText.trim() || undefined,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      page,
      size: PAGE_SIZE,
    },
    { enabled: canReadUsers },
  )

  const hasMore = usersQuery.data?.hasMore ?? false
  const canPrev = page > 0
  const canNext = hasMore
  const visibleUsers = usersQuery.data?.items ?? []
  const totalVisible = visibleUsers.length
  const activeUsers = visibleUsers.filter((user) => user.status === 'ACTIVE').length
  const lockedUsers = visibleUsers.filter((user) => user.status === 'LOCKED').length
  const suspendedUsers = visibleUsers.filter((user) => user.status === 'SUSPENDED').length

  const columns = useMemo<Array<DataTableColumn<IamUser>>>(
    () => [
      {
        key: 'username',
        header: 'Username',
        render: (user) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{user.username}</strong>
            <span className="muted-text">#{user.id}</span>
          </div>
        ),
      },
      {
        key: 'fullName',
        header: 'Full Name',
        render: (user) => user.fullName ?? 'No full name',
      },
      {
        key: 'actor',
        header: 'Actor',
        render: (user) => <span className="meta-chip">{formatActor(user.roleCodes)}</span>,
      },
      {
        key: 'scope',
        header: 'Effective Scope',
        render: (user) => <span className="muted-text">{formatScope(user)}</span>,
      },
      {
        key: 'status',
        header: 'Status',
        render: (user) => <IamUserStatusBadge status={user.status} />,
      },
      {
        key: 'contact',
        header: 'Contact',
        render: (user) => user.email ?? user.phone ?? 'No contact info',
      },
    ],
    [],
  )

  if (!canReadUsers) {
    return (
      <DashboardLayout description="Enterprise access control and visibility across published identities." eyebrow="IAM / Identity Control" title="User Management">
        <PermissionDeniedInline message="You need `iam.user.read` to open the user directory." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/iam/assignments">
              <AppIcon name="rule_settings" size="sm" />
              Review assignments
            </Link>
          </Button>
        </div>
      }
      description="Enterprise access control and visibility across published identities."
      eyebrow="IAM / Identity Control"
      title="User Management"
    >
      <section className="workspace-stats-grid" aria-label="User management summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon name="group" size="sm" />
            </span>
            <span className="workspace-stat-badge success">Visible</span>
          </div>
          <div>
            <p className="workspace-stat-label">Users on current page</p>
            <strong className="workspace-stat-value">{totalVisible}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon name="verified_user" size="sm" />
            </span>
            <span className="workspace-stat-badge success">Stable</span>
          </div>
          <div>
            <p className="workspace-stat-label">Active accounts</p>
            <strong className="workspace-stat-value">{activeUsers}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon name="lock" size="sm" />
            </span>
            <span className="workspace-stat-badge warning">Action Needed</span>
          </div>
          <div>
            <p className="workspace-stat-label">Locked accounts</p>
            <strong className="workspace-stat-value">{lockedUsers}</strong>
          </div>
        </article>
        <article className="workspace-stat-card danger">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon name="block" size="sm" />
            </span>
            <span className="workspace-stat-badge danger">Restricted</span>
          </div>
          <div>
            <p className="workspace-stat-label">Suspended accounts</p>
            <strong className="workspace-stat-value">{suspendedUsers}</strong>
          </div>
        </article>
      </section>

      <section className="workspace-filter-bar" aria-label="User filters">
        <label className="workspace-inline-search" htmlFor="iam-user-search">
          <AppIcon name="search" size="sm" />
          <input
            className="workspace-inline-input"
            id="iam-user-search"
            onChange={(event) => { setSearchText(event.target.value); setPage(0) }}
            placeholder="Search by username, full name, email, or phone..."
            value={searchText}
          />
        </label>
        <div className="workspace-inline-actions">
          <select
            aria-label="Account status"
            className="workspace-inline-select"
            onChange={(event) => { setStatusFilter(event.target.value as IamUserStatus | 'ALL'); setPage(0) }}
            value={statusFilter}
          >
            {statusOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <div className="workspace-inline-pill">
            <AppIcon name="visibility" size="sm" />
            Page {page + 1}
          </div>
        </div>
      </section>

      <DataTable
        canNext={canNext}
        canPrevious={canPrev}
        columns={columns}
        currentPage={page}
        emptyDescription="No users match the current filters."
        emptyTitle="No matching users"
        error={usersQuery.error ? getIamErrorMessage(usersQuery.error, 'Unable to load users.') : null}
        loading={usersQuery.isLoading}
        loadingDescription="Loading published IAM users for the current filter set..."
        loadingTitle="Loading users"
        onNext={() => setPage((p) => p + 1)}
        onPrevious={() => setPage((p) => Math.max(0, p - 1))}
        onRetry={() => void usersQuery.refetch()}
        onRowClick={(user) => navigate(`/iam/users/${user.id}`)}
        rowKey={(user) => user.id}
        rows={visibleUsers}
      />
    </DashboardLayout>
  )
}
