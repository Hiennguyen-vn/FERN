import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, DataTable, PermissionDeniedInline, ReadonlyBanner } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRegionList } from '../hooks/useOrg'
import type { OrgRegion } from '../model/org.types'
import { getOrgErrorMessage } from '../services/orgError.service'
import { buildParentRegionLabel, formatOrgInstant } from '../services/orgReadModel.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

const PAGE_SIZE = 50

export function RegionsPage() {
  usePageTitle('Regions — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canOpen = orgUiPolicy.canOpenRegionsPage(principal)
  const canCreate = orgUiPolicy.canOpenRegionCreate(principal)
  const [searchText, setSearchText] = useState('')
  const [page, setPage] = useState(0)
  const regionsQuery = useRegionList(
    { search: searchText.trim() || undefined, page, size: PAGE_SIZE },
    { enabled: canOpen },
  )

  // Server returns rows sorted by name asc — no client-side sort needed.
  const rows = regionsQuery.data?.items ?? []
  const hasMore = regionsQuery.data?.hasMore ?? false
  const parentRegionLookup = useMemo(
    () => new Map(rows.map((row) => [row.id, row] as const)),
    [rows],
  )
  const hierarchySummary = useMemo(() => {
    const visibleRegions = rows.length
    const rootRegions = rows.filter((region) => region.parentRegionId == null).length
    const nestedRegions = Math.max(visibleRegions - rootRegions, 0)
    const timezoneCount = new Set(rows.map((region) => region.timezoneName).filter(Boolean)).size
    const currencyCount = new Set(rows.map((region) => region.currencyCode).filter(Boolean)).size
    const watchlist = [...rows]
      .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
      .slice(0, 4)
      .map((region) => ({
        parentLabel: buildParentRegionLabel(
          region.parentRegionId,
          parentRegionLookup.get(region.parentRegionId ?? 0),
        ),
        region,
      }))

    return {
      currencyCount,
      nestedRegions,
      rootRegions,
      timezoneCount,
      visibleRegions,
      watchlist,
    }
  }, [parentRegionLookup, rows])

  const columns = useMemo<Array<DataTableColumn<OrgRegion>>>(
    () => [
      {
        key: 'region',
        header: 'Region',
        render: (region) => (
          <div className="compact-stack">
            <strong>{region.name}</strong>
            <span className="muted-text">{region.code}</span>
          </div>
        ),
      },
      {
        key: 'parent',
        header: 'Parent region',
        render: (region) => buildParentRegionLabel(region.parentRegionId, parentRegionLookup.get(region.parentRegionId ?? 0)),
      },
      {
        key: 'currency',
        header: 'Currency',
        render: (region) => region.currencyCode,
      },
      {
        key: 'timezone',
        header: 'Timezone',
        render: (region) => region.timezoneName,
      },
      {
        key: 'taxCode',
        header: 'Tax code',
        render: (region) => region.taxCode ?? 'No tax code',
      },
      {
        key: 'updatedAt',
        header: 'Updated at',
        render: (region) => formatOrgInstant(region.updatedAt),
      },
    ],
    [parentRegionLookup],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Regions" description="Region administration surface.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.read để mở danh sách region." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Regions"
      description="Region administration browse cho organizational hierarchy, timezone và currency reference."
      actions={
        canCreate ? (
          <Button asChild size="sm">
            <Link to="/org/regions/new">+ Create region</Link>
          </Button>
        ) : null
      }
      eyebrow="Organization"
    >
      <section className="surface-panel command-stage" aria-label="Region hierarchy signal">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Hierarchy-first browse</span>
            <span className="meta-chip">Page {page + 1}</span>
            <span className={hasMore ? 'meta-chip' : 'meta-chip-success'}>
              {hasMore ? 'More hierarchy available' : 'Current slice loaded'}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Regions</p>
            <strong className="action-summary-title">Current hierarchy slice</strong>
            <p className="muted-text">
              Browse root and nested regions with parent, currency, and timezone context before
              drilling into each administrative node.
            </p>
          </div>
          <div className="meta-grid">
            <span>Visible regions: {hierarchySummary.visibleRegions}</span>
            <span>Root nodes: {hierarchySummary.rootRegions}</span>
            <span>Nested nodes: {hierarchySummary.nestedRegions}</span>
            <span>Timezones in view: {hierarchySummary.timezoneCount}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Hierarchy watch</span>
            <strong>Recently updated regions</strong>
            <p>
              Keep the freshest hierarchy changes visible here before opening full region detail or
              moving to the next page.
            </p>
          </div>
          <div className="command-support-list">
            {hierarchySummary.watchlist.length > 0 ? (
              hierarchySummary.watchlist.map(({ parentLabel, region }) => (
                <article className="command-support-item" key={region.id}>
                  <div className="command-support-copy">
                    <strong>{region.name}</strong>
                    <span className="muted-text">{parentLabel}</span>
                    <span className="muted-text">{region.currencyCode} · {region.timezoneName}</span>
                  </div>
                  <div className="command-support-stack">
                    <span className="command-support-metric">{formatOrgInstant(region.updatedAt)}</span>
                    <span className="meta-chip">#{region.id}</span>
                  </div>
                </article>
              ))
            ) : (
              <div className="command-empty-note">
                No region rows are visible in the current scope. Adjust scope or filters to load
                hierarchy data.
              </div>
            )}
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Region summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="account_tree" />
            </span>
            <span className="workspace-stat-badge success">Visible</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Regions in current slice</span>
            <strong className="workspace-stat-value">{hierarchySummary.visibleRegions}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="flag" />
            </span>
            <span className="workspace-stat-badge">Structure</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Root regions</span>
            <strong className="workspace-stat-value">{hierarchySummary.rootRegions}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge">Reference</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Currencies represented</span>
            <strong className="workspace-stat-value">{hierarchySummary.currencyCount}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="schedule" />
            </span>
            <span className="workspace-stat-badge warning">Coverage</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Timezones represented</span>
            <strong className="workspace-stat-value">{hierarchySummary.timezoneCount}</strong>
          </div>
        </article>
      </section>

      <section className="workspace-filter-bar" aria-label="Region filters">
        <div className="workspace-inline-search">
          <AppIcon name="search" size="sm" />
          <input
            className="workspace-inline-input"
            onChange={(event) => { setSearchText(event.target.value); setPage(0) }}
            placeholder="Search by name, code, timezone, currency..."
            value={searchText}
          />
        </div>
      </section>
      {!regionsQuery.isLoading && !regionsQuery.error && rows.length === 0 && searchText ? (
        <ReadonlyBanner message={`Không tìm thấy region nào khớp "${searchText}". Thử từ khoá khác hoặc xoá bộ lọc.`} />
      ) : canCreate ? (
        <ReadonlyBanner message="Region create/edit workflow đã được publish cho principal có org.region.write." />
      ) : null}

      <DataTable
        canNext={hasMore}
        canPrevious={page > 0}
        columns={columns}
        currentPage={page}
        emptyDescription="Không có region nào khớp bộ lọc hiện tại hoặc scope hiện tại."
        emptyTitle="No matching regions"
        error={regionsQuery.error ? getOrgErrorMessage(regionsQuery.error, 'Không thể tải region trong scope hiện tại.') : null}
        loading={regionsQuery.isLoading}
        loadingDescription="Đang tải regions trong scope hiện tại..."
        loadingTitle="Đang tải regions"
        onNext={() => setPage((p) => p + 1)}
        onPrevious={() => setPage((p) => Math.max(0, p - 1))}
        onRetry={() => void regionsQuery.refetch()}
        onRowClick={(region) => navigate(`/org/regions/${region.id}`)}
        rowKey={(region) => region.id}
        rows={rows}
      />
    </DashboardLayout>
  )
}
