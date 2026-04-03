import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  PermissionDeniedInline,
  ReadonlyBanner,
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
  const canEdit = orgUiPolicy.canOpenOutletEdit(principal)

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
  let readonlyMessage = 'Outlet detail là read-first surface cho identity, region linkage và operational history.'
  if (outlet.status === 'CLOSED') {
    readonlyMessage = 'Outlet này đã CLOSED và đang ở chế độ terminal/read-only.'
  } else if (outlet.status === 'INACTIVE') {
    readonlyMessage = 'Outlet này đang INACTIVE. Review contact và region context trước khi chỉnh sửa.'
  }

  return (
    <DashboardLayout
      title="Outlet Detail"
      description="Structured detail view cho outlet identity, operational dates và linked region context."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/outlets">Back to outlets</Link>
          </Button>
          {canEdit ? (
            <Button asChild size="sm">
              <Link to={`/org/outlets/${outletId}/edit`}>Edit outlet</Link>
            </Button>
          ) : null}
        </div>
      }
    >
      <ReadonlyBanner message={readonlyMessage} />

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

      <section className="surface-panel command-stage" aria-label="Outlet detail command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Operational profile</span>
            <span className="meta-chip">{buildRegionLabel(outlet.regionId, region)}</span>
            <span className={outlet.status === 'ACTIVE' ? 'meta-chip-success' : 'meta-chip'}>
              {outlet.status}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Outlet Detail</p>
            <strong className="action-summary-title">Current outlet profile</strong>
            <p className="muted-text">
              Review identity, regional linkage, and operational contact data before editing or
              changing lifecycle status.
            </p>
          </div>
          <div className="meta-grid">
            <span>Outlet code: {outlet.code}</span>
            <span>Region: {buildRegionLabel(outlet.regionId, region)}</span>
            <span>Opened: {formatOrgDate(outlet.openedAt)}</span>
            <span>Last updated: {formatOrgInstant(outlet.updatedAt)}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Operations watch</span>
            <strong>Outlet readiness</strong>
            <p>
              Keep lifecycle state, contact reachability, and regional context visible here before
              taking administrative action.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Lifecycle state</strong>
                <span className="muted-text">Administrative status for the current branch record.</span>
              </div>
              <div className="command-support-stack">
                <StatusBadge status={outlet.status} />
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Contact reachability</strong>
                <span className="muted-text">
                  {outlet.email ?? 'No email'} · {outlet.phone ?? 'No phone'}
                </span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">
                  {[outlet.email, outlet.phone].filter(Boolean).length}/2
                </span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Regional reference</strong>
                <span className="muted-text">
                  {region ? `${region.timezoneName} · ${region.currencyCode}` : 'Region reference unavailable'}
                </span>
              </div>
              <div className="command-support-stack">
                <span className="meta-chip">{region ? 'Linked' : 'Unavailable'}</span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Outlet detail summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="storefront" />
            </span>
            <span className="workspace-stat-badge">Identity</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Outlet record</span>
            <strong className="workspace-stat-value">#{outlet.id}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="contact_page" />
            </span>
            <span className="workspace-stat-badge success">Reachable</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Contact points available</span>
            <strong className="workspace-stat-value">
              {[outlet.email, outlet.phone].filter(Boolean).length}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="schedule" />
            </span>
            <span className="workspace-stat-badge">Region ref</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Timezone / currency</span>
            <strong className="workspace-stat-value">
              {region ? `${region.timezoneName}` : 'No region ref'}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="event" />
            </span>
            <span className="workspace-stat-badge warning">Lifecycle</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Opened at</span>
            <strong className="workspace-stat-value">{formatOrgDate(outlet.openedAt)}</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
          <FormSection description="Thông tin nhận diện và trạng thái vận hành của outlet." title="Outlet overview">
            <div className="meta-grid">
              <span>Outlet ID: #{outlet.id}</span>
              <span>Code: {outlet.code}</span>
              <span>Status: {outlet.status}</span>
              <span>Region: {buildRegionLabel(outlet.regionId, region)}</span>
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
        </div>

        <aside className="surface-grid-side">
          <FormSection description="Context region liên kết của outlet cho tổ chức và scope." title="Region context">
            <div className="meta-grid">
              <span>Region ID: #{outlet.regionId}</span>
              <span>Region label: {buildRegionLabel(outlet.regionId, region)}</span>
              <span>
                {region ? `${region.timezoneName} · ${region.currencyCode}` : 'Timezone / currency phải xem ở region detail.'}
              </span>
            </div>
          </FormSection>

          <FormSection description="Audit metadata của outlet reference record." title="Audit trail">
            <div className="meta-grid">
              <span>Created at: {formatOrgInstant(outlet.createdAt)}</span>
              <span>Updated at: {formatOrgInstant(outlet.updatedAt)}</span>
            </div>
          </FormSection>
        </aside>
      </div>
    </DashboardLayout>
  )
}
