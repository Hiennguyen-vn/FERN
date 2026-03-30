import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  DataTable,
  EmptyState,
  ErrorState,
  FilterBar,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
  SummaryCards,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRegionalOutlets, useRegionalRegion } from '../hooks/useRegionalOps'
import type { RegionalOutlet } from '../model/regionalOps.types'
import { getRegionalOpsErrorMessage } from '../services/regionalError.service'
import {
  buildOutletContactLabel,
  buildOutletSummaryCards,
  buildRegionalScopeLabel,
  formatRegionalDate,
  matchesRegionalSearch,
} from '../services/regionalReadModel.service'
import { canOpenOutletSummary, resolveRegionalContext } from '../services/regionalUiPolicy.service'

export function OutletSummaryPage() {
  usePageTitle('Regional Outlet Summary')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { outletIds, regionIds, selectedRegionId } = useScopeContext()
  const canOpen = canOpenOutletSummary(principal)
  const regionalContext = resolveRegionalContext(principal, selectedRegionId, regionIds)
  const regionQuery = useRegionalRegion(regionalContext.resolvedRegionId, {
    enabled: canOpen && regionalContext.status === 'resolved',
  })
  const outletsQuery = useRegionalOutlets(outletIds, {
    enabled: canOpen && regionalContext.status === 'resolved',
  })
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const visibleOutlets = useMemo(
    () => outletsQuery.rows.filter((outlet) => outlet.regionId === regionalContext.resolvedRegionId),
    [outletsQuery.rows, regionalContext.resolvedRegionId],
  )

  const statusOptions = useMemo<SelectOption[]>(() => {
    const statuses = Array.from(new Set(visibleOutlets.map((outlet) => outlet.status).filter(Boolean))).sort()
    return [{ label: 'All statuses', value: 'ALL' }, ...statuses.map((status) => ({ label: status, value: status }))]
  }, [visibleOutlets])

  const filteredOutlets = useMemo(() => {
    return visibleOutlets.filter((outlet) => {
      const matchesStatus = statusFilter === 'ALL' || outlet.status === statusFilter
      const matchesQuery = matchesRegionalSearch(
        [outlet.id, outlet.code, outlet.name, outlet.address, outlet.email, outlet.phone],
        search,
      )

      return matchesStatus && matchesQuery
    })
  }, [search, statusFilter, visibleOutlets])

  const columns = useMemo<Array<DataTableColumn<RegionalOutlet>>>(
    () => [
      {
        key: 'outlet',
        header: 'Outlet',
        render: (outlet) => (
          <div className="page-stack" style={{ gap: '0.25rem' }}>
            <strong>{outlet.name}</strong>
            <span className="muted-text">{outlet.code}</span>
          </div>
        ),
      },
      {
        key: 'status',
        header: 'Status',
        render: (outlet) => <StatusBadge status={outlet.status} />,
      },
      {
        key: 'openedAt',
        header: 'Opened',
        render: (outlet) => formatRegionalDate(outlet.openedAt),
      },
      {
        key: 'closedAt',
        header: 'Closed',
        render: (outlet) => formatRegionalDate(outlet.closedAt),
      },
      {
        key: 'contact',
        header: 'Contact',
        render: (outlet) => buildOutletContactLabel(outlet),
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Outlet Summary" description="Regional outlet summary and drill-down view.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.read hoặc org.outlet.read để mở outlet summary theo regional scope." />
      </DashboardLayout>
    )
  }

  if (regionalContext.status !== 'resolved') {
    return (
      <DashboardLayout title="Outlet Summary" description="Regional outlet summary and drill-down view.">
        <ReadonlyBanner message={regionalContext.message ?? 'Region context is required to open outlet summary.'} />
        <EmptyState
          description="Outlet summary chỉ query khi region context hợp lệ đã được chọn trong app shell."
          title="Region context required"
        />
      </DashboardLayout>
    )
  }

  if (regionQuery.isLoading || outletsQuery.isLoading) {
    return (
      <DashboardLayout title="Outlet Summary" description="Regional outlet summary and drill-down view.">
        <DataTable
          columns={columns}
          loading
          loadingDescription="Đang tải outlet summary theo regional scope..."
          loadingTitle="Đang tải outlet summary"
          rowKey={(outlet) => outlet.id}
          rows={[]}
        />
      </DashboardLayout>
    )
  }

  const pageError = regionQuery.error ?? outletsQuery.error
  if (pageError) {
    return (
      <DashboardLayout title="Outlet Summary" description="Regional outlet summary and drill-down view.">
        <ErrorState
          actionLabel="Retry"
          message={getRegionalOpsErrorMessage(pageError, 'Không thể tải outlet summary.')}
          onAction={() => {
            void regionQuery.refetch()
            void outletsQuery.refresh()
          }}
          title="Không thể tải outlet summary"
        />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Outlet Summary"
      description={`Regional outlet scan cho ${buildRegionalScopeLabel(regionQuery.data?.id, regionQuery.data)}.`}
    >
      <ReadonlyBanner message="Outlet summary là read-first scan surface để regional lead theo dõi tình trạng mở/đóng, contact readiness và drill-down vào từng outlet." />

      <FilterBar description="Scan theo outlet code, name hoặc contact và lọc trạng thái để drill-down nhanh." title="Outlet filters">
        <Input
          label="Quick search"
          onChange={(event) => setSearch(event.target.value)}
          placeholder="code, name, address, contact"
          value={search}
        />
        <Select
          label="Status"
          onChange={(event) => setStatusFilter(event.target.value)}
          options={statusOptions}
          value={statusFilter}
        />
      </FilterBar>

      <SummaryCards items={buildOutletSummaryCards(visibleOutlets)} />

      <DataTable
        columns={columns}
        emptyDescription={
          visibleOutlets.length === 0
            ? 'Không có outlet nào trong region hiện tại hoặc accessible scope hiện tại.'
            : 'Không có outlet nào khớp bộ lọc hiện tại.'
        }
        emptyTitle={visibleOutlets.length === 0 ? 'No outlets in region' : 'No matching outlets'}
        onRowClick={(outlet) => navigate(`/regional-ops/outlets/${outlet.id}`)}
        rowKey={(outlet) => outlet.id}
        rows={filteredOutlets}
      />
    </DashboardLayout>
  )
}
