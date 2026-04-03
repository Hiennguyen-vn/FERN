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
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRegion } from '../hooks/useOrg'
import { getOrgErrorMessage } from '../services/orgError.service'
import { saveRecentRegionId } from '../services/orgBrowseHistory.service'
import {
  buildParentRegionLabel,
  formatOrgInstant,
} from '../services/orgReadModel.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

export function RegionDetailPage() {
  const { regionId: regionIdParam } = useParams<{ regionId: string }>()
  const regionId = Number(regionIdParam)
  const principal = usePrincipal()
  const canOpen = orgUiPolicy.canOpenRegionDetail(principal)
  const canEdit = orgUiPolicy.canOpenRegionEdit(principal)

  const regionQuery = useRegion(regionId, {
    enabled: canOpen && Number.isFinite(regionId) && regionId > 0,
  })

  const parentRegionQuery = useRegion(regionQuery.data?.parentRegionId ?? 0, {
    enabled: canOpen && Boolean(regionQuery.data?.parentRegionId),
  })

  usePageTitle(regionQuery.data ? `${regionQuery.data.name} — Org` : 'Region Detail — Org')

  useEffect(() => {
    if (regionQuery.data) {
      saveRecentRegionId(regionQuery.data.id)
    }
  }, [regionQuery.data])

  if (!canOpen) {
    return (
      <DashboardLayout title="Region Detail" description="Inspect region hierarchy and reference metadata.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.read để mở region detail." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(regionId) || regionId <= 0) {
    return (
      <DashboardLayout
        title="Region Detail"
        description="Inspect region hierarchy and reference metadata."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/regions">Back to regions</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa regionId hợp lệ." title="Thiếu regionId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (regionQuery.isLoading) {
    return (
      <DashboardLayout title="Region Detail" description="Inspect region hierarchy and reference metadata.">
        <Card title="Đang tải region detail">
          <p className="muted-text">Đang tải region #{regionId}...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (regionQuery.error) {
    return (
      <DashboardLayout
        title="Region Detail"
        description="Inspect region hierarchy and reference metadata."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/regions">Back to regions</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Retry"
          message={getOrgErrorMessage(regionQuery.error, `Không thể tải region #${regionId}.`)}
          onAction={() => void regionQuery.refetch()}
          title="Unable to load region"
        />
      </DashboardLayout>
    )
  }

  if (!regionQuery.data) {
    return (
      <DashboardLayout
        title="Region Detail"
        description="Inspect region hierarchy and reference metadata."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/regions">Back to regions</Link>
          </Button>
        }
      >
        <EmptyState description="Region này không tồn tại hoặc nằm ngoài scope hiện tại." title="Region not found" />
      </DashboardLayout>
    )
  }

  const region = regionQuery.data
  const parentRegion = parentRegionQuery.data

  return (
    <DashboardLayout
      title="Region Detail"
      description="Structured detail view cho timezone, currency và hierarchy context của region."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/org/regions">Back to regions</Link>
          </Button>
          {canEdit ? (
            <Button asChild size="sm">
              <Link to={`/org/regions/${regionId}/edit`}>Edit region</Link>
            </Button>
          ) : null}
        </div>
      }
    >
      <ReadonlyBanner
        message={
          canEdit
            ? 'Region edit workflow đã được publish cho principal có org.region.write.'
            : 'Region detail hiện là read-first với tài khoản hiện tại. Region edit cần org.region.write.'
        }
      />

      <EntityHeader
        eyebrow="Org / Region"
        metadata={
          <>
            <span>Region code: {region.code}</span>
            <span>Parent: {buildParentRegionLabel(region.parentRegionId, parentRegion)}</span>
            <span>Currency: {region.currencyCode}</span>
            <span>Timezone: {region.timezoneName}</span>
          </>
        }
        title={region.name}
      />

      <section className="surface-panel command-stage" aria-label="Region detail command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Hierarchy reference record</span>
            <span className="meta-chip">{region.currencyCode}</span>
            <span className={region.parentRegionId ? 'meta-chip' : 'meta-chip-success'}>
              {region.parentRegionId ? `Child of #${region.parentRegionId}` : 'Root region'}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Region Detail</p>
            <strong className="action-summary-title">Current hierarchy profile</strong>
            <p className="muted-text">
              Review the region reference settings, parent linkage, and audit freshness before moving
              into edit or drilling deeper into outlet structure.
            </p>
          </div>
          <div className="meta-grid">
            <span>Region code: {region.code}</span>
            <span>Parent region: {buildParentRegionLabel(region.parentRegionId, parentRegion)}</span>
            <span>Currency: {region.currencyCode}</span>
            <span>Timezone: {region.timezoneName}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Reference checks</span>
            <strong>Hierarchy readiness</strong>
            <p>
              Keep the parent linkage, tax reference, and audit freshness visible here before making
              structural changes.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Parent linkage</strong>
                <span className="muted-text">
                  {buildParentRegionLabel(region.parentRegionId, parentRegion)}
                </span>
              </div>
              <div className="command-support-stack">
                <span className="meta-chip">{region.parentRegionId ? 'Nested' : 'Root'}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Reference settings</strong>
                <span className="muted-text">
                  {region.currencyCode} · {region.timezoneName}
                </span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{region.taxCode ?? 'No tax code'}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Audit freshness</strong>
                <span className="muted-text">Latest reference update for this hierarchy node.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{formatOrgInstant(region.updatedAt)}</span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Region detail summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="account_tree" />
            </span>
            <span className="workspace-stat-badge">Node</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Region record</span>
            <strong className="workspace-stat-value">#{region.id}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge">Currency</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Reference currency</span>
            <strong className="workspace-stat-value">{region.currencyCode}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="schedule" />
            </span>
            <span className="workspace-stat-badge">Timezone</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Reference timezone</span>
            <strong className="workspace-stat-value">{region.timezoneName}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="flag" />
            </span>
            <span className={region.parentRegionId ? 'workspace-stat-badge warning' : 'workspace-stat-badge success'}>
              Hierarchy
            </span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Structure role</span>
            <strong className="workspace-stat-value">{region.parentRegionId ? 'Child' : 'Root'}</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
          <FormSection description="Thông tin nhận diện và reference settings của region." title="Region overview">
            <div className="meta-grid">
              <span>Region ID: #{region.id}</span>
              <span>Code: {region.code}</span>
              <span>Currency: {region.currencyCode}</span>
              <span>Timezone: {region.timezoneName}</span>
              <span>Tax code: {region.taxCode ?? 'No tax code'}</span>
            </div>
          </FormSection>

          <FormSection description="Hierarchy context giúp xác định region đang nằm ở đâu trong cây tổ chức." title="Hierarchy context">
            <div className="meta-grid">
              <span>Parent region: {buildParentRegionLabel(region.parentRegionId, parentRegion)}</span>
              <span>Parent region ID: {region.parentRegionId ? `#${region.parentRegionId}` : 'Root region'}</span>
            </div>
          </FormSection>
        </div>

        <aside className="surface-grid-side">
          <FormSection description="Audit metadata cho region reference record." title="Audit trail">
            <div className="meta-grid">
              <span>Created at: {formatOrgInstant(region.createdAt)}</span>
              <span>Updated at: {formatOrgInstant(region.updatedAt)}</span>
            </div>
          </FormSection>
        </aside>
      </div>
    </DashboardLayout>
  )
}
