import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, DataTable, Input, PermissionDeniedInline, ReadonlyBanner } from '@design-system/index'
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

  const columns = useMemo<Array<DataTableColumn<OrgRegion>>>(
    () => [
      {
        key: 'region',
        header: 'Region',
        render: (region) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
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
    >
      <Input
        label="Search regions"
        onChange={(event) => { setSearchText(event.target.value); setPage(0) }}
        placeholder="Tên, mã, timezone, currency..."
        value={searchText}
      />
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
