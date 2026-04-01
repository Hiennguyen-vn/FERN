import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { DataTable, Input, PermissionDeniedInline } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRegionList } from '../hooks/useOrg'
import type { OrgRegion } from '../model/org.types'
import { getOrgErrorMessage } from '../services/orgError.service'
import { buildParentRegionLabel, formatOrgInstant } from '../services/orgReadModel.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

export function RegionsPage() {
  usePageTitle('Regions — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canOpen = orgUiPolicy.canOpenRegionsPage(principal)
  const [searchText, setSearchText] = useState('')
  const regionsQuery = useRegionList(
    { search: searchText.trim() || undefined, page: 0, size: 100 },
    { enabled: canOpen },
  )

  const rows = useMemo(
    () => [...(regionsQuery.data?.items ?? [])].sort((left, right) => left.name.localeCompare(right.name, 'vi')),
    [regionsQuery.data],
  )
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
    >
      <Input
        label="Search regions"
        onChange={(event) => setSearchText(event.target.value)}
        placeholder="Tên, mã, timezone, currency..."
        value={searchText}
      />

      <DataTable
        columns={columns}
        emptyDescription="Không có region nào khớp bộ lọc hiện tại hoặc scope hiện tại."
        emptyTitle="No matching regions"
        error={regionsQuery.error ? getOrgErrorMessage(regionsQuery.error, 'Không thể tải region trong scope hiện tại.') : null}
        loading={regionsQuery.isLoading}
        loadingDescription="Đang tải regions trong scope hiện tại..."
        loadingTitle="Đang tải regions"
        onRetry={() => void regionsQuery.refetch()}
        onRowClick={(region) => navigate(`/org/regions/${region.id}`)}
        rowKey={(region) => region.id}
        rows={rows}
      />
    </DashboardLayout>
  )
}
