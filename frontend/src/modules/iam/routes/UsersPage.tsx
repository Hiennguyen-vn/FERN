import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  DataTable,
  Input,
  PermissionDeniedInline,
  Select,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { IamUserStatusBadge } from '../components/IamStatusBadge'
import { useIamUsers } from '../hooks/useIam'
import type { IamUser, IamUserStatus } from '../model/iam.types'
import { getIamErrorMessage } from '../services/iamError.service'
import { iamUiPolicy } from '../services/iamUiPolicy.service'

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Locked', value: 'LOCKED' },
  { label: 'Suspended', value: 'SUSPENDED' },
]

const PAGE_SIZE = 50

export function UsersPage() {
  usePageTitle('IAM Users')
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

  const columns = useMemo<Array<DataTableColumn<IamUser>>>(
    () => [
      { key: 'id', header: 'User ID', render: (user) => `#${user.id}` },
      {
        key: 'identity',
        header: 'Identity',
        render: (user) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{user.username}</strong>
            <span className="muted-text">{user.fullName ?? 'No full name'}</span>
          </div>
        ),
      },
      {
        key: 'contact',
        header: 'Contact',
        render: (user) => user.email ?? user.phone ?? 'No contact info',
      },
      {
        key: 'roles',
        header: 'Roles',
        render: (user) => (user.roleCodes.length > 0 ? user.roleCodes.join(', ') : 'No roles'),
      },
      {
        key: 'status',
        header: 'Status',
        render: (user) => <IamUserStatusBadge status={user.status} />,
      },
    ],
    [],
  )

  if (!canReadUsers) {
    return (
      <DashboardLayout title="IAM Users" description="Browse user accounts trong IAM">
        <PermissionDeniedInline message="Bạn cần quyền iam.user.read để xem user directory." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="IAM Users"
      description="Published IAM browse cho user directory, assignment review và effective access."
    >
      <div className="field-grid">
        <Input
          label="Search users"
          onChange={(event) => { setSearchText(event.target.value); setPage(0) }}
          placeholder="Username, full name, email, phone..."
          value={searchText}
        />
        <Select
          label="Status filter"
          onChange={(event) => { setStatusFilter(event.target.value as IamUserStatus | 'ALL'); setPage(0) }}
          options={statusOptions}
          value={statusFilter}
        />
      </div>

      <DataTable
        canNext={canNext}
        canPrevious={canPrev}
        columns={columns}
        currentPage={page}
        emptyDescription="Không có user nào khớp bộ lọc hiện tại."
        emptyTitle="No matching users"
        error={usersQuery.error ? getIamErrorMessage(usersQuery.error, 'Không thể tải danh sách user.') : null}
        loading={usersQuery.isLoading}
        loadingDescription="Đang tải IAM users..."
        loadingTitle="Đang tải users"
        onNext={() => setPage((p) => p + 1)}
        onPrevious={() => setPage((p) => Math.max(0, p - 1))}
        onRetry={() => void usersQuery.refetch()}
        onRowClick={(user) => navigate(`/iam/users/${user.id}`)}
        rowKey={(user) => user.id}
        rows={usersQuery.data?.items ?? []}
      />
    </DashboardLayout>
  )
}
