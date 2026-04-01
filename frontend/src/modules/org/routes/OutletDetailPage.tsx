import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  PermissionDeniedInline,
  StatusBadge,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useOutlet, useRegion } from '../hooks/useOrg'
import { getOrgErrorMessage } from '../services/orgError.service'
import { saveRecentOutletId } from '../services/orgBrowseHistory.service'
import {
  buildOutletContactLabel,
  buildRegionLabel,
  formatOrgDate,
  formatOrgInstant,
} from '../services/orgReadModel.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

export function OutletDetailPage() {
  const { outletId: outletIdParam } = useParams<{ outletId: string }>()
  const outletId = Number(outletIdParam)
  const principal = usePrincipal()
  const canOpen = orgUiPolicy.canOpenOutletDetail(principal)

  const outletQuery = useOutlet(outletId, {
    enabled: canOpen && Number.isFinite(outletId) && outletId > 0,
  })

  const regionQuery = useRegion(outletQuery.data?.regionId ?? 0, {
    enabled: canOpen && Boolean(outletQuery.data?.regionId),
  })

  usePageTitle(outletQuery.data ? `${outletQuery.data.name} — Org` : 'Outlet Detail — Org')

  useEffect(() => {
    if (outletQuery.data) {
      saveRecentOutletId(outletQuery.data.id)
    }
  }, [outletQuery.data])

  if (!canOpen) {
    return (
      <DashboardLayout title="Outlet Detail" description="Inspect outlet operational metadata and region context.">
        <PermissionDeniedInline message="Bạn cần quyền org.outlet.read để mở outlet detail." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(outletId) || outletId <= 0) {
    return (
      <DashboardLayout
        title="Outlet Detail"
        description="Inspect outlet operational metadata and region context."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/outlets">Back to outlets</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa outletId hợp lệ." title="Thiếu outletId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (outletQuery.isLoading) {
    return (
      <DashboardLayout title="Outlet Detail" description="Inspect outlet operational metadata and region context.">
        <Card title="Đang tải outlet detail">
          <p className="muted-text">Đang tải outlet #{outletId}...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (outletQuery.error) {
    return (
      <DashboardLayout
        title="Outlet Detail"
        description="Inspect outlet operational metadata and region context."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/outlets">Back to outlets</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Retry"
          message={getOrgErrorMessage(outletQuery.error, `Không thể tải outlet #${outletId}.`)}
          onAction={() => void outletQuery.refetch()}
          title="Unable to load outlet"
        />
      </DashboardLayout>
    )
  }

  if (!outletQuery.data) {
    return (
      <DashboardLayout
        title="Outlet Detail"
        description="Inspect outlet operational metadata and region context."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/outlets">Back to outlets</Link>
          </Button>
        }
      >
        <EmptyState description="Outlet này không tồn tại hoặc nằm ngoài scope hiện tại." title="Outlet not found" />
      </DashboardLayout>
    )
  }

  const outlet = outletQuery.data
  const region = regionQuery.data

  return (
    <DashboardLayout
      title="Outlet Detail"
      description="Structured detail view cho outlet identity, operational dates và linked region context."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/org/outlets">Back to outlets</Link>
        </Button>
      }
    >
      <EntityHeader
        eyebrow="Org / Outlet"
        metadata={
          <>
            <span>Outlet code: {outlet.code}</span>
            <span>Region: {buildRegionLabel(outlet.regionId, region)}</span>
            <span>Opened: {formatOrgDate(outlet.openedAt)}</span>
          </>
        }
        status={<StatusBadge status={outlet.status} />}
        title={outlet.name}
      />

      <FormSection description="Thông tin nhận diện và trạng thái vận hành của outlet." title="Outlet overview">
        <div className="meta-grid">
          <span>Outlet ID: #{outlet.id}</span>
          <span>Code: {outlet.code}</span>
          <span>Status: {outlet.status}</span>
          <span>Region: {buildRegionLabel(outlet.regionId, region)}</span>
        </div>
      </FormSection>

      <FormSection description="Context region liên kết của outlet cho tổ chức và scope." title="Region context">
        <div className="meta-grid">
          <span>Region ID: #{outlet.regionId}</span>
          <span>Region label: {buildRegionLabel(outlet.regionId, region)}</span>
          <span>Timezone / currency phải xem ở region detail.</span>
        </div>
      </FormSection>

      <FormSection description="Các mốc operational và lifecycle của outlet." title="Operational dates">
        <div className="meta-grid">
          <span>Opened at: {formatOrgDate(outlet.openedAt)}</span>
          <span>Closed at: {formatOrgDate(outlet.closedAt)}</span>
        </div>
      </FormSection>

      <FormSection description="Contact metadata của outlet." title="Contact">
        <div className="meta-grid">
          <span>Address: {outlet.address ?? 'No address'}</span>
          <span>Contact: {buildOutletContactLabel(outlet)}</span>
          <span>Email: {outlet.email ?? 'No email'}</span>
          <span>Phone: {outlet.phone ?? 'No phone'}</span>
        </div>
      </FormSection>

      <FormSection description="Audit metadata của outlet reference record." title="Audit trail">
        <div className="meta-grid">
          <span>Created at: {formatOrgInstant(outlet.createdAt)}</span>
          <span>Updated at: {formatOrgInstant(outlet.updatedAt)}</span>
        </div>
      </FormSection>
    </DashboardLayout>
  )
}
