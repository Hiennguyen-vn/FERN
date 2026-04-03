import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  DataTable,
  PermissionDeniedInline,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useOutletList, useRegionList } from '../hooks/useOrg'
import type { OrgOutlet } from '../model/org.types'
import { getOrgErrorMessage } from '../services/orgError.service'
import {
  buildOutletContactLabel,
  buildRegionLabel,
  formatOrgDate,
} from '../services/orgReadModel.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

const PAGE_SIZE = 50

// Static options derived from the backend OutletStatus enum (DRAFT | ACTIVE | INACTIVE | CLOSED).
const STATUS_OPTIONS: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Closed', value: 'CLOSED' },
]

export function OutletsPage() {
  usePageTitle('Outlets — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { selectedRegionId } = useScopeContext()
  const canOpen = orgUiPolicy.canOpenOutletsPage(principal)
  const canCreate = orgUiPolicy.canOpenOutletCreate(principal)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [regionFilter, setRegionFilter] = useState(selectedRegionId ? String(selectedRegionId) : 'ALL')
  const [page, setPage] = useState(0)

  const outletsQuery = useOutletList(
    {
      regionId: regionFilter === 'ALL' ? undefined : Number(regionFilter),
      search: search.trim() || undefined,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      page,
      size: PAGE_SIZE,
    },
    { enabled: canOpen },
  )

  const rows = outletsQuery.data?.items ?? []
  const hasMore = outletsQuery.data?.hasMore ?? false

  const regionIds = useMemo(
    () => Array.from(new Set(rows.map((row) => row.regionId).concat(selectedRegionId ? [selectedRegionId] : []))),
    [rows, selectedRegionId],
  )
  const regionsQuery = useRegionList(
    { page: 0, size: Math.max(regionIds.length, 50) },
    { enabled: canOpen && regionIds.length > 0 },
  )
  const regionLookup = useMemo(
    () => new Map((regionsQuery.data?.items ?? []).map((row) => [row.id, row] as const)),
    [regionsQuery.data],
  )

  const regionOptions = useMemo<SelectOption[]>(() => {
    return [
      { label: 'Tất cả regions', value: 'ALL' },
      ...regionIds.map((regionId) => ({
        label: buildRegionLabel(regionId, regionLookup.get(regionId)),
        value: String(regionId),
      })),
    ]
  }, [regionIds, regionLookup])

  const columns = useMemo<Array<DataTableColumn<OrgOutlet>>>(
    () => [
      {
        key: 'outlet',
        header: 'Outlet',
        render: (outlet) => (
          <div className="compact-stack">
            <strong>{outlet.name}</strong>
            <span className="muted-text">{outlet.code}</span>
          </div>
        ),
      },
      {
        key: 'region',
        header: 'Region',
        render: (outlet) => buildRegionLabel(outlet.regionId, regionLookup.get(outlet.regionId)),
      },
      {
        key: 'status',
        header: 'Status',
        render: (outlet) => <StatusBadge status={outlet.status} />,
      },
      {
        key: 'dates',
        header: 'Operational dates',
        render: (outlet) => `${formatOrgDate(outlet.openedAt)} → ${formatOrgDate(outlet.closedAt)}`,
      },
      {
        key: 'contact',
        header: 'Contact',
        render: (outlet) => buildOutletContactLabel(outlet),
      },
    ],
    [regionLookup],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Outlets" description="Outlet administration surface.">
        <PermissionDeniedInline message="Bạn cần quyền org.outlet.read để mở danh sách outlet." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        canCreate ? (
          <Button asChild size="sm">
            <Link to="/org/outlets/new">+ Create outlet</Link>
          </Button>
        ) : null
      }
      title="Outlets"
      description="Table-first outlet browse cho operational metadata, status và region context."
      eyebrow="Organization"
    >
      <section className="surface-panel command-stage" aria-label="Outlet browse signal">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Operational browse</span>
            <span className="meta-chip">
              Region filter {regionFilter === 'ALL' ? 'All visible' : `#${regionFilter}`}
            </span>
            <span className={statusFilter === 'ALL' ? 'meta-chip' : 'meta-chip-success'}>
              Status {statusFilter}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Outlets</p>
            <strong className="action-summary-title">Current outlet slice</strong>
            <p className="muted-text">
              Review outlet status, region coverage, and operational contact data before opening a
              branch detail or create flow.
            </p>
          </div>
          <div className="meta-grid">
            <span>Visible outlets: {rows.length}</span>
            <span>Regions represented: {regionIds.length}</span>
            <span>Attention states: {rows.filter((row) => row.status !== 'ACTIVE').length}</span>
            <span>Current page: {page + 1}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Outlet watch</span>
            <strong>Status watchlist</strong>
            <p>
              Surface the latest non-active or recently updated outlets here so operations can drill
              into the right branch quickly.
            </p>
          </div>
          <div className="command-support-list">
            {[...rows]
              .sort((left, right) => {
                const leftAttention = left.status === 'ACTIVE' ? 0 : 1
                const rightAttention = right.status === 'ACTIVE' ? 0 : 1
                if (leftAttention !== rightAttention) {
                  return rightAttention - leftAttention
                }
                return Date.parse(right.updatedAt) - Date.parse(left.updatedAt)
              })
              .slice(0, 4)
              .map((outlet) => (
                <article className="command-support-item" key={outlet.id}>
                  <div className="command-support-copy">
                    <strong>{outlet.name}</strong>
                    <span className="muted-text">
                      {buildRegionLabel(outlet.regionId, regionLookup.get(outlet.regionId))}
                    </span>
                    <span className="muted-text">{buildOutletContactLabel(outlet)}</span>
                  </div>
                  <div className="command-support-stack">
                    <StatusBadge status={outlet.status} />
                    <span className="command-support-metric">{formatOrgDate(outlet.updatedAt)}</span>
                  </div>
                </article>
              ))}
            {rows.length === 0 ? (
              <div className="command-empty-note">
                No outlets are visible in this slice. Adjust search, region, or scope to load branch
                records.
              </div>
            ) : null}
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Outlet summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="storefront" />
            </span>
            <span className="workspace-stat-badge success">Visible</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Outlets in current slice</span>
            <strong className="workspace-stat-value">{rows.length}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="check_circle" />
            </span>
            <span className="workspace-stat-badge success">Open</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Active outlets</span>
            <strong className="workspace-stat-value">
              {rows.filter((outlet) => outlet.status === 'ACTIVE').length}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="warning" />
            </span>
            <span className="workspace-stat-badge warning">Attention</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Needs attention</span>
            <strong className="workspace-stat-value">
              {rows.filter((outlet) => outlet.status !== 'ACTIVE').length}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="public" />
            </span>
            <span className="workspace-stat-badge">Coverage</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Regions represented</span>
            <strong className="workspace-stat-value">{regionIds.length}</strong>
          </div>
        </article>
      </section>

      <section className="workspace-filter-bar" aria-label="Outlet filters">
        <div className="workspace-inline-search">
          <AppIcon name="search" size="sm" />
          <input
            aria-label="Search outlets"
            className="workspace-inline-input"
            onChange={(event) => { setSearch(event.target.value); setPage(0) }}
            placeholder="Search by name, code or contact..."
            value={search}
          />
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Region</span>
          <select
            className="workspace-inline-select"
            onChange={(event) => { setRegionFilter(event.target.value); setPage(0) }}
            value={regionFilter}
          >
            {regionOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Status</span>
          <select
            className="workspace-inline-select"
            onChange={(event) => { setStatusFilter(event.target.value); setPage(0) }}
            value={statusFilter}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
      </section>

      <DataTable
        canNext={hasMore}
        canPrevious={page > 0}
        columns={columns}
        currentPage={page}
        emptyDescription="Không có outlet nào khớp bộ lọc hiện tại hoặc scope hiện tại."
        emptyTitle="No matching outlets"
        error={outletsQuery.error ? getOrgErrorMessage(outletsQuery.error, 'Không thể tải outlet trong scope hiện tại.') : null}
        loading={outletsQuery.isLoading}
        loadingDescription="Đang tải outlets trong scope hiện tại..."
        loadingTitle="Đang tải outlets"
        onNext={() => setPage((p) => p + 1)}
        onPrevious={() => setPage((p) => Math.max(0, p - 1))}
        onRetry={() => void outletsQuery.refetch()}
        onRowClick={(outlet) => navigate(`/org/outlets/${outlet.id}`)}
        rowKey={(outlet) => outlet.id}
        rows={rows}
      />
    </DashboardLayout>
  )
}
