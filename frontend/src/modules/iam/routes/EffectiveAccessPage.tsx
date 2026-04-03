import { useMemo } from 'react'
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
import { IamUserStatusBadge } from '../components/IamStatusBadge'
import { useEffectiveAccess, useIamPermissionOverrides, useIamPermissions, useIamUser } from '../hooks/useIam'
import { buildEffectivePermissionRows, formatScopeRoots, type EffectivePermissionRow } from '../services/effectiveAccess.service'
import { getIamErrorMessage } from '../services/iamError.service'
import { iamUiPolicy } from '../services/iamUiPolicy.service'

export function EffectiveAccessPage() {
  const { userId: userIdParam } = useParams<{ userId: string }>()
  const userId = Number(userIdParam)
  const principal = usePrincipal()
  const canReadUsers = iamUiPolicy.canReadUsers(principal)
  const canReadPermissions = iamUiPolicy.canReadPermissions(principal)
  const canReadOverrides = iamUiPolicy.canReadPermissionOverrides(principal)
  const userQuery = useIamUser(userId, { enabled: canReadUsers && Number.isInteger(userId) })
  const effectiveAccessQuery = useEffectiveAccess(userId, { enabled: canReadUsers && Number.isInteger(userId) })
  const permissionsQuery = useIamPermissions({ enabled: canReadUsers && canReadPermissions })
  const overridesQuery = useIamPermissionOverrides(userId, {
    enabled: canReadUsers && canReadOverrides && Number.isInteger(userId),
  })

  const rows = useMemo(() => {
    if (!effectiveAccessQuery.data) {
      return []
    }

    return buildEffectivePermissionRows(
      effectiveAccessQuery.data,
      permissionsQuery.data ?? [],
      overridesQuery.data?.overrides ?? [],
    )
  }, [effectiveAccessQuery.data, overridesQuery.data?.overrides, permissionsQuery.data])

  const columns = useMemo<Array<DataTableColumn<EffectivePermissionRow>>>(
    () => [
      { key: 'code', header: 'Permission', render: (row) => row.code },
      {
        key: 'state',
        header: 'Effective state',
        render: (row) =>
          row.effectiveState === 'EFFECTIVE' ? (
            <span className="badge badge-success">Effective</span>
          ) : (
            <span className="badge badge-danger">Denied</span>
          ),
      },
      {
        key: 'flags',
        header: 'Grant / deny',
        render: (row) => `${row.granted ? 'Grant' : 'No grant'} · ${row.denied ? 'Denied' : 'Not denied'}`,
      },
      {
        key: 'source',
        header: 'Why',
        render: (row) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            {row.sourceLabels.length > 0 ? row.sourceLabels.map((label) => <span key={label}>{label}</span>) : <span>No source metadata</span>}
            {row.overrideReason ? <span className="muted-text">Reason: {row.overrideReason}</span> : null}
          </div>
        ),
      },
      {
        key: 'description',
        header: 'Description',
        render: (row) => row.description,
      },
    ],
    [],
  )

  usePageTitle(userQuery.data ? `${userQuery.data.username} effective access — IAM` : 'Effective Access — IAM')

  if (!iamUiPolicy.canOpenEffectiveAccess(principal)) {
    return (
      <DashboardLayout title="Effective Access" description="Explainable access view for IAM users">
        <PermissionDeniedInline message="Bạn cần quyền iam.user.read để xem effective access." />
      </DashboardLayout>
    )
  }

  if (!Number.isInteger(userId) || userId <= 0) {
    return (
      <DashboardLayout title="Effective Access" description="Explainable access view for IAM users">
        <EmptyState description="Effective access route cần userId hợp lệ." title="Invalid userId" />
      </DashboardLayout>
    )
  }

  if (userQuery.isLoading || effectiveAccessQuery.isLoading) {
    return (
      <DashboardLayout title="Effective Access" description="Explainable access view for IAM users">
        <Card title="Đang tải effective access">
          <p className="muted-text">Đang tải user profile, effective permissions và scope roots...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (userQuery.error || effectiveAccessQuery.error) {
    return (
      <DashboardLayout title="Effective Access" description="Explainable access view for IAM users">
        <ErrorState
          actionLabel="Retry"
          message={getIamErrorMessage(
            userQuery.error ?? effectiveAccessQuery.error,
            'Không thể tải effective access cho user này.',
          )}
          onAction={() => {
            void userQuery.refetch()
            void effectiveAccessQuery.refetch()
          }}
          title="Unable to load effective access"
        />
      </DashboardLayout>
    )
  }

  if (!userQuery.data || !effectiveAccessQuery.data) {
    return (
      <DashboardLayout title="Effective Access" description="Explainable access view for IAM users">
        <EmptyState description="Không tìm thấy effective access cho user này." title="No effective access data" />
      </DashboardLayout>
    )
  }

  const user = userQuery.data
  const access = effectiveAccessQuery.data

  return (
    <DashboardLayout
      title="Effective Access"
      description="Explainable access view dựa trên backend effective-access response."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to={`/iam/users/${user.id}`}>Back to user detail</Link>
          </Button>
          <Button asChild size="sm" variant="ghost">
            <Link to="/iam/assignments">Assignments console</Link>
          </Button>
        </div>
      }
    >
      <ReadonlyBanner message="Trang này giải thích effective permissions, sources và scope từ backend contract hiện có. Backend vẫn là nguồn sự thật cho authorization." />

      <EntityHeader
        eyebrow="IAM / Effective Access"
        metadata={
          <>
            <span>User ID: #{user.id}</span>
            <span>Roles: {access.roles.join(', ') || 'No roles'}</span>
            <span>{formatScopeRoots(access.scopeRoots)}</span>
          </>
        }
        status={<IamUserStatusBadge status={user.status} />}
        title={user.username}
      />

      {!canReadPermissions ? (
        <PermissionDeniedInline message="Thiếu iam.permission.read nên mô tả permission sẽ fallback về code; bảng effective access vẫn hiển thị được." />
      ) : null}

      {!canReadOverrides ? (
        <PermissionDeniedInline message="Thiếu iam.permission_override.read nên lý do deny/grant trực tiếp có thể không đầy đủ." />
      ) : null}

      <div className="card-grid">
        <Card title="Roles">
          <strong>{access.roles.length}</strong>
          <p className="muted-text">{access.roles.join(', ') || 'No active roles'}</p>
        </Card>
        <Card title="Granted permissions">
          <strong>{access.grantedPermissions.length}</strong>
          <p className="muted-text">Permissions granted before deny overrides are applied.</p>
        </Card>
        <Card title="Denied permissions">
          <strong>{access.deniedPermissions.length}</strong>
          <p className="muted-text">Permissions explicitly denied by override.</p>
        </Card>
        <Card title="Effective permissions">
          <strong>{access.effectivePermissions.length}</strong>
          <p className="muted-text">Final permissions after grants and denies are resolved.</p>
        </Card>
      </div>

      <Card title="Scope roots">
        <p className="muted-text">{formatScopeRoots(access.scopeRoots)}</p>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription="Backend không trả effective permission nào cho user này."
        emptyTitle="No effective permissions"
        error={
          permissionsQuery.error
            ? getIamErrorMessage(permissionsQuery.error, 'Không thể tải permission metadata; thử reload.')
            : null
        }
        onRetry={() => void permissionsQuery.refetch()}
        rowKey={(row) => row.code}
        rows={rows}
      />
    </DashboardLayout>
  )
}
