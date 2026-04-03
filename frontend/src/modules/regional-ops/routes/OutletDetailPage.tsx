import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  EmptyState,
  EntityHeader,
  ErrorState,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRegionalOutlet, useRegionalRegion } from '../hooks/useRegionalOps'
import { getRegionalOpsErrorMessage } from '../services/regionalError.service'
import { buildOutletContactLabel, buildRegionalScopeLabel, formatRegionalDate, formatRegionalInstant } from '../services/regionalReadModel.service'
import { canOpenOutletDetail } from '../services/regionalUiPolicy.service'

function toOutletId(value: string | undefined) {
  if (!value) {
    return null
  }

  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

export function OutletDetailPage() {
  const { outletId } = useParams<{ outletId: string }>()
  const parsedOutletId = toOutletId(outletId)
  const principal = usePrincipal()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canOpen = canOpenOutletDetail(principal)
  const outletQuery = useRegionalOutlet(parsedOutletId, { enabled: canOpen && Boolean(parsedOutletId) })
  const regionQuery = useRegionalRegion(outletQuery.data?.regionId ?? null, {
    enabled: canOpen && Boolean(outletQuery.data?.regionId),
  })

  usePageTitle(outletQuery.data ? `${outletQuery.data.name} — Regional Outlet Detail` : 'Regional Outlet Detail')

  if (!canOpen) {
    return (
      <DashboardLayout title="Outlet Detail" description="Regional outlet oversight detail.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.read hoặc org.outlet.read để xem outlet detail theo góc nhìn regional." />
      </DashboardLayout>
    )
  }

  if (!parsedOutletId) {
    return (
      <DashboardLayout title="Outlet Detail" description="Regional outlet oversight detail.">
        <EmptyState description="URL không chứa outletId hợp lệ." title="Missing outletId" />
      </DashboardLayout>
    )
  }

  if (outletQuery.isLoading) {
    return (
      <DashboardLayout title="Outlet Detail" description="Regional outlet oversight detail.">
        <Card title="Loading outlet detail">
          <p className="muted-text">Loading outlet oversight context...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (outletQuery.error) {
    return (
      <DashboardLayout
        title="Outlet Detail"
        description="Regional outlet oversight detail."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/regional-ops/outlets">Back to outlet summary</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Retry"
          message={getRegionalOpsErrorMessage(outletQuery.error, 'Không thể tải outlet detail.')}
          onAction={() => void outletQuery.refetch()}
          title="Không thể tải outlet detail"
        />
      </DashboardLayout>
    )
  }

  if (!outletQuery.data) {
    return (
      <DashboardLayout
        title="Outlet Detail"
        description="Regional outlet oversight detail."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/regional-ops/outlets">Back to outlet summary</Link>
          </Button>
        }
      >
        <EmptyState description="Outlet này không tồn tại hoặc nằm ngoài scope hiện tại." title="Outlet not found" />
      </DashboardLayout>
    )
  }

  const outlet = outletQuery.data
  const scopeMessage =
    selectedRegionId && selectedRegionId !== outlet.regionId
      ? `Outlet này thuộc Region #${outlet.regionId}, khác với region đang chọn (#${selectedRegionId}). Bạn đang xem deep-link detail theo regional perspective.`
      : regionIds.length === 0
        ? 'Bạn đang mở outlet detail mà không có region context rõ ràng trong app shell. Trang chỉ hiển thị oversight read-only.'
        : 'Outlet detail trong Regional Ops là read-only và ưu tiên oversight/state visibility thay vì outlet-level transaction editing.'

  return (
    <DashboardLayout
      title="Outlet Detail"
      description="Regional oversight detail cho outlet cụ thể với emphasis vào trạng thái, contact readiness và region placement."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/regional-ops/outlets">Back to outlet summary</Link>
        </Button>
      }
    >
      <ReadonlyBanner message={scopeMessage} />

      <EntityHeader
        eyebrow="Regional Ops / Outlet"
        metadata={
          <>
            <span>Outlet code: {outlet.code}</span>
            <span>Region: {buildRegionalScopeLabel(outlet.regionId, regionQuery.data)}</span>
            <span>Opened: {formatRegionalDate(outlet.openedAt)}</span>
          </>
        }
        status={<StatusBadge status={outlet.status} />}
        title={outlet.name}
      />

      <Card title="Regional placement">
        <div className="meta-grid">
          <span>Region: {buildRegionalScopeLabel(outlet.regionId, regionQuery.data)}</span>
          <span>Selected region: {selectedRegionId ? `#${selectedRegionId}` : 'None'}</span>
          <span>Updated: {formatRegionalInstant(outlet.updatedAt)}</span>
          <span>Created: {formatRegionalInstant(outlet.createdAt)}</span>
        </div>
      </Card>

      <Card title="Operational posture">
        <div className="meta-grid">
          <span>Status: {outlet.status}</span>
          <span>Opened at: {formatRegionalDate(outlet.openedAt)}</span>
          <span>Closed at: {formatRegionalDate(outlet.closedAt)}</span>
          <span>Contact: {buildOutletContactLabel(outlet)}</span>
        </div>
      </Card>

      <Card title="Address and contact">
        <div className="meta-grid">
          <span>Address: {outlet.address ?? 'No address'}</span>
          <span>Phone: {outlet.phone ?? 'No phone'}</span>
          <span>Email: {outlet.email ?? 'No email'}</span>
        </div>
      </Card>
    </DashboardLayout>
  )
}
