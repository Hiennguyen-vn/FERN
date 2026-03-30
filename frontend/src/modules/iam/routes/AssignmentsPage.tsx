import { useState } from 'react'
import { Link } from 'react-router-dom'
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
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { IamRoleStatusBadge } from '../components/IamStatusBadge'
import {
  useEffectiveAccess,
  useIamPermissionOverrides,
  useIamPermissions,
  useIamRoles,
  useIamUser,
} from '../hooks/useIam'
import type { IamPermission, IamRole, PermissionOverrideItem } from '../model/iam.types'
import { formatOverrideMode, formatScopeRoots } from '../services/effectiveAccess.service'
import { getIamErrorMessage } from '../services/iamError.service'
import { iamUiPolicy } from '../services/iamUiPolicy.service'

export function AssignmentsPage() {
  usePageTitle('IAM Assignments')
  const principal = usePrincipal()
  const canReadUsers = iamUiPolicy.canReadUsers(principal)
  const canReadRoles = iamUiPolicy.canReadRoles(principal)
  const canReadPermissions = iamUiPolicy.canReadPermissions(principal)
  const canReadOverrides = iamUiPolicy.canReadPermissionOverrides(principal)
  const canOpenPage = iamUiPolicy.canOpenAssignmentsPage(principal)
  const [lookupInput, setLookupInput] = useState(principal?.userId ? String(principal.userId) : '')
  const [selectedUserId, setSelectedUserId] = useState<number | null>(principal?.userId ?? null)
  const [validationError, setValidationError] = useState<string | null>(null)

  const userQuery = useIamUser(selectedUserId ?? 0, { enabled: canReadUsers && Boolean(selectedUserId) })
  const rolesQuery = useIamRoles({ enabled: canReadRoles })
  const permissionsQuery = useIamPermissions({ enabled: canReadPermissions })
  const overridesQuery = useIamPermissionOverrides(selectedUserId ?? 0, {
    enabled: canReadOverrides && Boolean(selectedUserId),
  })
  const effectiveAccessQuery = useEffectiveAccess(selectedUserId ?? 0, {
    enabled: canReadUsers && Boolean(selectedUserId),
  })

  const submitLookup = () => {
    const parsed = Number(lookupInput)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập user ID hợp lệ để load assignment snapshot.')
      return
    }

    setValidationError(null)
    setSelectedUserId(parsed)
  }

  const roleColumns: Array<DataTableColumn<IamRole>> = [
    { key: 'code', header: 'Role code', render: (role) => role.code },
    {
      key: 'name',
      header: 'Role',
      render: (role) => (
        <div className="page-stack" style={{ gap: '0.35rem' }}>
          <strong>{role.name}</strong>
          <span className="muted-text">{role.description ?? 'No description'}</span>
        </div>
      ),
    },
    { key: 'permissions', header: 'Permission count', render: (role) => role.permissionCodes.length },
    { key: 'status', header: 'Status', render: (role) => <IamRoleStatusBadge status={role.status} /> },
  ]

  const permissionColumns: Array<DataTableColumn<IamPermission>> = [
    { key: 'code', header: 'Permission code', render: (permission) => permission.code },
    { key: 'name', header: 'Name', render: (permission) => permission.name },
    {
      key: 'description',
      header: 'Description',
      render: (permission) => permission.description ?? 'No description',
    },
  ]

  const overrideColumns: Array<DataTableColumn<PermissionOverrideItem>> = [
    { key: 'permissionCode', header: 'Permission', render: (item) => item.permissionCode },
    { key: 'mode', header: 'Override', render: (item) => formatOverrideMode(item.overrideMode) },
    { key: 'reason', header: 'Reason', render: (item) => item.reason ?? 'No reason' },
    { key: 'expiresAt', header: 'Expires at', render: (item) => item.expiresAt ?? 'No expiry' },
  ]

  if (!canOpenPage) {
    return (
      <DashboardLayout title="IAM Assignments" description="Inspect assignments, roles và permissions">
        <PermissionDeniedInline message="Bạn không có IAM read permission cần thiết để mở assignments console." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout title="IAM Assignments" description="Assignment-first console cho role catalog, permission catalog, và user assignment snapshot.">
      <ReadonlyBanner message="Trang này tập trung vào read-first assignment inspection. Backend vẫn là nguồn sự thật cho quyền và scope." />

      <Card title="Select user for assignment snapshot">
        <div className="field-grid">
          <Input
            label="User ID"
            onChange={(event) => setLookupInput(event.target.value)}
            placeholder="VD: 1"
            type="number"
            value={lookupInput}
          />
        </div>
        <div className="form-actions align-start">
          <Button onClick={submitLookup} size="sm">
            Load assignments
          </Button>
          {principal?.userId ? (
            <Button
              onClick={() => {
                setLookupInput(String(principal.userId))
                setSelectedUserId(principal.userId)
                setValidationError(null)
              }}
              size="sm"
              variant="secondary"
            >
              Load current user
            </Button>
          ) : null}
        </div>
        {validationError ? <p className="error-text">{validationError}</p> : null}
      </Card>

      {selectedUserId ? (
        !canReadUsers ? (
          <PermissionDeniedInline message="Thiếu iam.user.read nên không thể load user-specific assignments. Bạn vẫn có thể xem role/permission catalogs bên dưới nếu có quyền." />
        ) : userQuery.isLoading ? (
          <Card title="Đang tải assignments">
            <p className="muted-text">Đang tải assignment snapshot cho user #{selectedUserId}...</p>
          </Card>
        ) : userQuery.error ? (
          <ErrorState
            actionLabel="Retry"
            message={getIamErrorMessage(userQuery.error, `Không thể tải assignments cho user #${selectedUserId}.`)}
            onAction={() => void userQuery.refetch()}
            title="Unable to load user assignments"
          />
        ) : userQuery.data ? (
          <Card title="Selected user assignment snapshot">
            <div className="meta-grid">
              <span>User: {userQuery.data.username}</span>
              <span>Roles: {userQuery.data.roleCodes.join(', ') || 'No roles'}</span>
              <span>{formatScopeRoots(userQuery.data.scopeRoots)}</span>
            </div>
            <div className="form-actions align-start">
              <Button asChild size="sm" variant="secondary">
                <Link to={`/iam/users/${userQuery.data.id}`}>Open user detail</Link>
              </Button>
              <Button asChild size="sm" variant="ghost">
                <Link to={`/iam/effective-access/${userQuery.data.id}`}>Open effective access</Link>
              </Button>
            </div>
            {effectiveAccessQuery.data ? (
              <div className="meta-grid">
                <span>Granted: {effectiveAccessQuery.data.grantedPermissions.length}</span>
                <span>Denied: {effectiveAccessQuery.data.deniedPermissions.length}</span>
                <span>Effective: {effectiveAccessQuery.data.effectivePermissions.length}</span>
              </div>
            ) : null}
          </Card>
        ) : (
          <EmptyState description="Không tìm thấy user tương ứng để inspect assignments." title="User not found" />
        )
      ) : (
        <EmptyState description="Nhập user ID để inspect role, scope và permission overrides cho một user cụ thể." title="No user selected" />
      )}

      <Card title="Permission overrides">
        {!canReadOverrides ? (
          <PermissionDeniedInline message="Thiếu iam.permission_override.read nên phần override snapshot bị ẩn." />
        ) : !selectedUserId ? (
          <EmptyState description="Chọn user trước khi xem direct grants/denies." title="Select a user first" />
        ) : (
          <DataTable
            columns={overrideColumns}
            emptyDescription="User này chưa có direct grant/deny overrides."
            emptyTitle="No active overrides"
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

      <Card title="Role catalog">
        {!canReadRoles ? (
          <PermissionDeniedInline message="Thiếu iam.role.read nên role catalog bị ẩn." />
        ) : (
          <DataTable
            columns={roleColumns}
            emptyDescription="IAM chưa có role nào trong catalog."
            emptyTitle="No roles"
            error={rolesQuery.error ? getIamErrorMessage(rolesQuery.error, 'Không thể tải role catalog.') : null}
            loading={rolesQuery.isLoading}
            onRetry={() => void rolesQuery.refetch()}
            rowKey={(role) => role.id}
            rows={rolesQuery.data ?? []}
          />
        )}
      </Card>

      <Card title="Permission catalog">
        {!canReadPermissions ? (
          <PermissionDeniedInline message="Thiếu iam.permission.read nên permission catalog bị ẩn." />
        ) : (
          <DataTable
            columns={permissionColumns}
            emptyDescription="IAM chưa có permission metadata nào để hiển thị."
            emptyTitle="No permissions"
            error={
              permissionsQuery.error
                ? getIamErrorMessage(permissionsQuery.error, 'Không thể tải permission catalog.')
                : null
            }
            loading={permissionsQuery.isLoading}
            onRetry={() => void permissionsQuery.refetch()}
            rowKey={(permission) => permission.code}
            rows={permissionsQuery.data ?? []}
          />
        )}
      </Card>
    </DashboardLayout>
  )
}
