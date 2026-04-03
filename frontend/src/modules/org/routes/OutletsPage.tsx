import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  DataTable,
  Input,
  PermissionDeniedInline,
  Select,
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
          <div className="page-stack" style={{ gap: '0.35rem' }}>
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
    >
      <div className="field-grid">
        <Input
          label="Search outlets"
          onChange={(event) => { setSearch(event.target.value); setPage(0) }}
          placeholder="Tên, mã hoặc contact..."
          value={search}
        />
        <Select
          label="Region filter"
          onChange={(event) => { setRegionFilter(event.target.value); setPage(0) }}
          options={regionOptions}
          value={regionFilter}
        />
        <Select
          label="Status filter"
          onChange={(event) => { setStatusFilter(event.target.value); setPage(0) }}
          options={STATUS_OPTIONS}
          value={statusFilter}
        />
      </div>

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
