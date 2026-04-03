import { useEffect, useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  EntityHeader,
  ErrorState,
  PermissionDeniedInline,
  ReadonlyBanner,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { IamRoleStatusBadge, IamUserStatusBadge } from '../components/IamStatusBadge'
import { useIamPermissionOverrides, useIamRoles, useIamUser } from '../hooks/useIam'
import type { IamRole, PermissionOverrideItem } from '../model/iam.types'
import { formatOverrideMode, formatScopeRoots } from '../services/effectiveAccess.service'
import { getIamErrorMessage } from '../services/iamError.service'
import { iamUiPolicy } from '../services/iamUiPolicy.service'
import { saveRecentIamUser } from '../services/recentUsers.service'

export function UserDetailPage() {
  const { userId: userIdParam } = useParams<{ userId: string }>()
  const userId = Number(userIdParam)
  const principal = usePrincipal()
  const canReadUsers = iamUiPolicy.canReadUsers(principal)
  const canReadRoles = iamUiPolicy.canReadRoles(principal)
  const canReadOverrides = iamUiPolicy.canReadPermissionOverrides(principal)
  const userQuery = useIamUser(userId, { enabled: canReadUsers && Number.isInteger(userId) })
  const rolesQuery = useIamRoles({ enabled: canReadUsers && canReadRoles })
  const overridesQuery = useIamPermissionOverrides(userId, {
    enabled: canReadUsers && canReadOverrides && Number.isInteger(userId),
  })

  useEffect(() => {
    if (userQuery.data) {
      saveRecentIamUser(userQuery.data)
    }
  }, [userQuery.data])

  usePageTitle(userQuery.data ? `${userQuery.data.username} — IAM` : 'IAM User Detail')

  const assignedRoles = useMemo(() => {
    const lookup = new Map((rolesQuery.data ?? []).map((role) => [role.code, role] as const))
    return (userQuery.data?.roleCodes ?? []).map((code) => lookup.get(code)).filter(Boolean) as IamRole[]
  }, [rolesQuery.data, userQuery.data?.roleCodes])

  const roleColumns = useMemo<Array<DataTableColumn<IamRole>>>(
    () => [
      { key: 'code', header: 'Role code', render: (role) => role.code },
      {
        key: 'name',
        header: 'Role',
        render: (role) => (
          <div className="compact-stack">
            <strong>{role.name}</strong>
            <span className="muted-text">{role.description ?? 'No role description'}</span>
          </div>
        ),
      },
      {
        key: 'permissions',
        header: 'Permissions',
        render: (role) => role.permissionCodes.length,
      },
      {
        key: 'status',
        header: 'Status',
        render: (role) => <IamRoleStatusBadge status={role.status} />,
      },
    ],
    [],
  )

  const overrideColumns = useMemo<Array<DataTableColumn<PermissionOverrideItem>>>(
    () => [
      { key: 'permissionCode', header: 'Permission', render: (item) => item.permissionCode },
      { key: 'mode', header: 'Override', render: (item) => formatOverrideMode(item.overrideMode) },
      { key: 'reason', header: 'Reason', render: (item) => item.reason ?? 'No reason' },
      { key: 'expiresAt', header: 'Expires at', render: (item) => item.expiresAt ?? 'No expiry' },
    ],
    [],
  )

  if (!canReadUsers) {
    return (
      <DashboardLayout title="IAM User Detail" description="Inspect user profile và assignments">
        <PermissionDeniedInline message="Bạn cần quyền iam.user.read để xem chi tiết user." />
      </DashboardLayout>
    )
  }

  if (!Number.isInteger(userId) || userId <= 0) {
    return (
      <DashboardLayout title="IAM User Detail" description="Inspect user profile và assignments">
        <EmptyState description="User detail route cần userId hợp lệ." title="Invalid userId" />
      </DashboardLayout>
    )
  }

  if (userQuery.isLoading) {
    return (
      <DashboardLayout title="IAM User Detail" description="Inspect user profile và assignments">
        <Card title="Đang tải user">
          <p className="muted-text">Đang tải user profile và assignment snapshot...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (userQuery.error) {
    return (
      <DashboardLayout title="IAM User Detail" description="Inspect user profile và assignments">
        <ErrorState
          actionLabel="Retry"
          message={getIamErrorMessage(userQuery.error, `Không thể tải user #${userId}.`)}
          onAction={() => void userQuery.refetch()}
          title="Unable to load user"
        />
      </DashboardLayout>
    )
  }

  if (!userQuery.data) {
    return (
      <DashboardLayout title="IAM User Detail" description="Inspect user profile và assignments">
        <EmptyState description="User không tồn tại hoặc không còn truy cập được." title="User not found" />
      </DashboardLayout>
    )
  }

  const user = userQuery.data

  return (
    <DashboardLayout
      title="IAM User Detail"
      description="Read-first user inspection và assignment snapshot."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/iam/users">Back to users</Link>
        </Button>
      }
    >
      <ReadonlyBanner message="IAM detail đang publish theo read-first mode. Assignment/effective access được giải thích nhưng không mở full admin edit workflow ở bước này." />

      <EntityHeader
        actions={
          <div className="form-actions align-start">
            <Button asChild size="sm" variant="secondary">
              <Link to={`/iam/effective-access/${user.id}`}>Effective access</Link>
            </Button>
            <Button asChild size="sm" variant="ghost">
              <Link to="/iam/assignments">Assignments console</Link>
            </Button>
          </div>
        }
        eyebrow="IAM / User"
        metadata={
          <>
            <span>User ID: #{user.id}</span>
            <span>Roles: {user.roleCodes.length}</span>
            <span>{formatScopeRoots(user.scopeRoots)}</span>
          </>
        }
        status={<IamUserStatusBadge status={user.status} />}
        title={user.username}
      />

      <Card title="Profile">
        <div className="field-grid">
          <div>
            <p className="eyebrow">Full name</p>
            <strong>{user.fullName ?? 'No full name'}</strong>
          </div>
          <div>
            <p className="eyebrow">Email</p>
            <strong>{user.email ?? 'No email'}</strong>
          </div>
          <div>
            <p className="eyebrow">Phone</p>
            <strong>{user.phone ?? 'No phone'}</strong>
          </div>
          <div>
            <p className="eyebrow">Status</p>
            <IamUserStatusBadge status={user.status} />
          </div>
        </div>
      </Card>

      <Card title="Assignments">
        <div className="field-grid">
          <div>
            <p className="eyebrow">Role codes</p>
            <strong>{user.roleCodes.length > 0 ? user.roleCodes.join(', ') : 'No assigned roles'}</strong>
          </div>
          <div>
            <p className="eyebrow">Scope roots</p>
            <strong>{formatScopeRoots(user.scopeRoots)}</strong>
          </div>
        </div>
      </Card>

      <Card title="Role details">
        {!canReadRoles ? (
          <PermissionDeniedInline message="Bạn có thể thấy role codes từ user profile, nhưng thiếu iam.role.read nên không xem được role metadata." />
        ) : (
          <DataTable
            columns={roleColumns}
            emptyDescription="User hiện chưa có active role metadata nào."
            emptyTitle="No assigned role details"
            error={rolesQuery.error ? getIamErrorMessage(rolesQuery.error, 'Không thể tải role metadata.') : null}
            loading={rolesQuery.isLoading}
            onRetry={() => void rolesQuery.refetch()}
            rowKey={(role) => role.id}
            rows={assignedRoles}
          />
        )}
      </Card>

      <Card title="Permission overrides">
        {!canReadOverrides ? (
          <PermissionDeniedInline message="Thiếu iam.permission_override.read nên phần permission overrides bị ẩn." />
        ) : (
          <DataTable
            columns={overrideColumns}
            emptyDescription="User này chưa có permission override active nào."
            emptyTitle="No active permission overrides"
            error={
              overridesQuery.error
                ? getIamErrorMessage(overridesQuery.error, 'Không thể tải permission overrides.')
                : null
            }
            loading={overridesQuery.isLoading}
            onRetry={() => void overridesQuery.refetch()}
            rowKey={(override) => `${override.permissionCode}-${override.overrideMode}`}
            rows={overridesQuery.data?.overrides ?? []}
          />
        )}
      </Card>
    </DashboardLayout>
  )
}
