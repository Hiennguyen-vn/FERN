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
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useIamUser } from '../hooks/useIam'
import type { IamUser, IamUserStatus } from '../model/iam.types'
import { IamUserStatusBadge } from '../components/IamStatusBadge'
import { getIamErrorMessage } from '../services/iamError.service'
import { iamUiPolicy } from '../services/iamUiPolicy.service'
import { clearRecentIamUsers, loadRecentIamUsers, saveRecentIamUser } from '../services/recentUsers.service'

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Locked', value: 'LOCKED' },
  { label: 'Suspended', value: 'SUSPENDED' },
]

function matchesUserSearch(user: IamUser, query: string) {
  const normalized = query.trim().toLowerCase()
  if (!normalized) {
    return true
  }

  return [user.username, user.fullName, user.email, user.phone, user.id]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(normalized))
}

export function UsersPage() {
  usePageTitle('IAM Users')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canReadUsers = iamUiPolicy.canReadUsers(principal)
  const [lookupInput, setLookupInput] = useState(principal?.userId ? String(principal.userId) : '')
  const [searchedUserId, setSearchedUserId] = useState<number | null>(null)
  const [searchText, setSearchText] = useState('')
  const [statusFilter, setStatusFilter] = useState<IamUserStatus | 'ALL'>('ALL')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [recentUsers, setRecentUsers] = useState<IamUser[]>(() => loadRecentIamUsers())

  const userQuery = useIamUser(searchedUserId ?? 0, { enabled: canReadUsers && Boolean(searchedUserId) })

  useEffect(() => {
    if (!userQuery.data) {
      return
    }

    saveRecentIamUser(userQuery.data)
    setRecentUsers(loadRecentIamUsers())
  }, [userQuery.data])

  const filteredRecentUsers = useMemo(() => {
    return recentUsers.filter((user) => {
      const statusMatches = statusFilter === 'ALL' || user.status === statusFilter
      return statusMatches && matchesUserSearch(user, searchText)
    })
  }, [recentUsers, searchText, statusFilter])

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

  const submitLookup = () => {
    const parsed = Number(lookupInput)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập user ID hợp lệ để lookup.')
      return
    }

    setValidationError(null)
    setSearchedUserId(parsed)
  }

  if (!canReadUsers) {
    return (
      <DashboardLayout title="IAM Users" description="Lookup và inspect user accounts trong IAM">
        <PermissionDeniedInline message="Bạn cần quyền iam.user.read để tra cứu và inspect user accounts." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout title="IAM Users" description="Lookup-first user console vì backend hiện không publish public user list endpoint.">
      <ReadonlyBanner message="IAM đang publish ở chế độ read-first / assignment-first. Trang này hỗ trợ lookup theo user ID và recent inspected users." />

      <Card title="Lookup user">
        <div className="field-grid">
          <Input
            label="User ID"
            onChange={(event) => setLookupInput(event.target.value)}
            placeholder="VD: 1"
            type="number"
            value={lookupInput}
          />
          <Input
            label="Tìm trong recent users"
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Username, full name, email..."
            value={searchText}
          />
          <Select
            label="Status filter"
            onChange={(event) => setStatusFilter(event.target.value as IamUserStatus | 'ALL')}
            options={statusOptions}
            value={statusFilter}
          />
        </div>
        <div className="form-actions align-start">
          <Button onClick={submitLookup} size="sm">
            Lookup user
          </Button>
          {principal?.userId ? (
            <Button
              onClick={() => {
                setLookupInput(String(principal.userId))
                setSearchedUserId(principal.userId)
                setValidationError(null)
              }}
              size="sm"
              variant="secondary"
            >
              Load current user
            </Button>
          ) : null}
          {recentUsers.length > 0 ? (
            <Button
              onClick={() => {
                clearRecentIamUsers()
                setRecentUsers([])
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

      {searchedUserId ? (
        userQuery.isLoading ? (
          <Card title="Đang lookup user">
            <p className="muted-text">Đang tải user #{searchedUserId}...</p>
          </Card>
        ) : userQuery.error ? (
          <ErrorState
            actionLabel="Retry lookup"
            message={getIamErrorMessage(userQuery.error, `Không thể tải user #${searchedUserId}.`)}
            onAction={() => void userQuery.refetch()}
            title="Lookup failed"
          />
        ) : userQuery.data ? (
          <Card title="Latest lookup">
            <div className="meta-grid">
              <span>User: {userQuery.data.username}</span>
              <span>Status: {userQuery.data.status}</span>
              <span>Roles: {userQuery.data.roleCodes.length}</span>
            </div>
            <div className="form-actions align-start">
              <Button asChild size="sm" variant="secondary">
                <button onClick={() => navigate(`/iam/users/${userQuery.data!.id}`)} type="button">
                  Open user detail
                </button>
              </Button>
              <Button asChild size="sm" variant="ghost">
                <button onClick={() => navigate(`/iam/effective-access/${userQuery.data!.id}`)} type="button">
                  Open effective access
                </button>
              </Button>
            </div>
          </Card>
        ) : null
      ) : null}

      {recentUsers.length === 0 ? (
        <EmptyState
          description="Chưa có user nào được inspect gần đây. Dùng user ID để lookup và build recent list."
          title="No recent users yet"
        />
      ) : (
        <DataTable
          columns={columns}
          emptyDescription="Không có recent users nào khớp bộ lọc hiện tại."
          emptyTitle="No matching recent users"
          onRowClick={(user) => navigate(`/iam/users/${user.id}`)}
          rowKey={(user) => user.id}
          rows={filteredRecentUsers}
        />
      )}
    </DashboardLayout>
  )
}
