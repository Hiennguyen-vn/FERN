import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
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

      <FormSection description="Audit metadata cho region reference record." title="Audit trail">
        <div className="meta-grid">
          <span>Created at: {formatOrgInstant(region.createdAt)}</span>
          <span>Updated at: {formatOrgInstant(region.updatedAt)}</span>
        </div>
      </FormSection>
    </DashboardLayout>
  )
}
