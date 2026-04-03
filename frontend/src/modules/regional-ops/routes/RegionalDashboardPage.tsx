import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
  SummaryCards,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { formatMoney } from '@shared/formatters'
import { useOutletRevenueTodayStats } from '../../reports/hooks/useOutletRevenueTodayStats'
import { useRegionalOutlets, useRegionalRegion } from '../hooks/useRegionalOps'
import type { RegionalOutlet } from '../model/regionalOps.types'
import { getRegionalOpsErrorMessage } from '../services/regionalError.service'
import {
  buildOutletContactLabel,
  buildRegionalDashboardCards,
  buildRegionalScopeLabel,
  formatRegionalDate,
  formatRegionalInstant,
} from '../services/regionalReadModel.service'
import { canOpenRegionalDashboard, resolveRegionalContext } from '../services/regionalUiPolicy.service'

export function RegionalDashboardPage() {
  usePageTitle('Regional Ops')
  const principal = usePrincipal()
  const { outletIds, regionIds, selectedRegionId, selectedOutletId } = useScopeContext()
  const canOpen = canOpenRegionalDashboard(principal)
  const regionalContext = resolveRegionalContext(principal, selectedRegionId, regionIds)
  const regionQuery = useRegionalRegion(regionalContext.resolvedRegionId, {
    enabled: canOpen && regionalContext.status === 'resolved',
  })
  const outletsQuery = useRegionalOutlets(outletIds, {
    enabled: canOpen && regionalContext.status === 'resolved',
  })
  const outletStatsQuery = useOutletRevenueTodayStats(
    outletIds,
    canOpen && regionalContext.status === 'resolved',
  )

  const visibleOutlets = useMemo(
    () => outletsQuery.rows.filter((outlet) => outlet.regionId === regionalContext.resolvedRegionId),
    [outletsQuery.rows, regionalContext.resolvedRegionId],
  )
  const visibleOutletStats = useMemo(
    () => outletStatsQuery.outletStats.filter((row) => visibleOutlets.some((outlet) => outlet.id === row.outletId)),
    [outletStatsQuery.outletStats, visibleOutlets],
  )

  const columns = useMemo<Array<DataTableColumn<RegionalOutlet>>>(
    () => [
      {
        key: 'outlet',
        header: 'Outlet',
        render: (outlet) => (
          <div className="compact-stack-tight">
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
        key: 'contact',
        header: 'Contact',
        render: (outlet) => buildOutletContactLabel(outlet),
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Regional Ops" description="Regional oversight console for high-level operational visibility.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.read hoặc org.outlet.read để mở Regional Ops." />
      </DashboardLayout>
    )
  }

  if (regionalContext.status !== 'resolved') {
    return (
      <DashboardLayout title="Regional Ops" description="Regional oversight console for high-level operational visibility.">
        <ReadonlyBanner message={regionalContext.message ?? 'Region context is required to open Regional Ops.'} />
        <EmptyState
          description="Regional Ops chỉ query dữ liệu khi đã có region context rõ ràng từ app shell hoặc scope hiện tại."
          title="Region context required"
        />
      </DashboardLayout>
    )
  }

  if (regionQuery.isLoading || outletsQuery.isLoading) {
    return (
      <DashboardLayout title="Regional Ops" description="Regional oversight console for high-level operational visibility.">
        <Card title="Loading regional dashboard">
          <p className="muted-text">Loading scoped region context and outlet oversight data...</p>
        </Card>
      </DashboardLayout>
    )
  }

  const pageError = regionQuery.error ?? outletsQuery.error
  if (pageError) {
    return (
      <DashboardLayout title="Regional Ops" description="Regional oversight console for high-level operational visibility.">
        <ErrorState
          actionLabel="Retry"
          message={getRegionalOpsErrorMessage(pageError, 'Không thể tải regional dashboard.')}
          onAction={() => {
            void regionQuery.refetch()
            void outletsQuery.refresh()
          }}
          title="Không thể tải regional dashboard"
        />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Regional Ops"
      description="Overview-first regional command center với summary signals và drill-down trực tiếp xuống outlet oversight."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/regional-ops/outlets">Open outlet summary</Link>
        </Button>
      }
    >
      <ReadonlyBanner message="Regional Ops là bề mặt oversight/read-first. Trang này tập trung vào visibility và drill-down, không hỗ trợ transaction editing." />

      <Card title="Current regional context">
        <div className="meta-grid">
          <span>Region: {buildRegionalScopeLabel(regionQuery.data?.id, regionQuery.data)}</span>
          <span>Timezone: {regionQuery.data?.timezoneName ?? 'N/A'}</span>
          <span>Currency: {regionQuery.data?.currencyCode ?? 'N/A'}</span>
          <span>Selected outlet: {selectedOutletId ? `#${selectedOutletId}` : 'None'}</span>
          <span>Updated: {formatRegionalInstant(regionQuery.data?.updatedAt)}</span>
        </div>
      </Card>

      <SummaryCards items={buildRegionalDashboardCards(regionQuery.data, visibleOutlets)} />

      <Card title="Regional focus">
        <div className="meta-grid">
          <span>Visible outlets in region: {visibleOutlets.length}</span>
          <span>Total scoped outlets: {outletIds.length}</span>
          <span>Scoped regions: {regionIds.length}</span>
          <span>Parent region: {regionQuery.data?.parentRegionId ? `#${regionQuery.data.parentRegionId}` : 'Root region'}</span>
        </div>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription="Không có outlet nào trong scoped region hiện tại."
        emptyTitle="No visible outlets"
        rowKey={(outlet) => outlet.id}
        rows={visibleOutlets}
      />

      <Card title="Thống kê cuối ca theo outlet (hôm nay)">
        <DataTable
          columns={[
            {
              key: 'outletId',
              header: 'Outlet',
              render: (row) => {
                const outlet = visibleOutlets.find((o) => o.id === row.outletId)
                return (
                  <div className="compact-stack-tight">
                    <strong>{outlet?.name ?? `#${row.outletId}`}</strong>
                    <span className="muted-text">{outlet?.code ?? ''}</span>
                  </div>
                )
              },
            },
            {
              key: 'sessionStatus',
              header: 'Phiên POS',
              render: (row) => <StatusBadge status={row.sessionStatus} />,
            },
            {
              key: 'totalRevenue',
              header: 'Doanh thu',
              render: (row) => (
                <strong className="metric-value-positive">
                  {row.isLoading ? '...' : formatMoney(row.totalRevenue, row.currencyCode)}
                </strong>
              ),
            },
            {
              key: 'cashCollected',
              header: 'Tiền mặt',
              render: (row) => (row.isLoading ? '...' : formatMoney(row.cashCollected, row.currencyCode)),
            },
            {
              key: 'nonCashCollected',
              header: 'Thẻ/Ví',
              render: (row) => (row.isLoading ? '...' : formatMoney(row.nonCashCollected, row.currencyCode)),
            },
            {
              key: 'completed',
              header: 'Đơn xong',
              render: (row) => (
                <strong className={row.completed > 0 ? 'metric-value-positive' : undefined}>
                  {row.isLoading ? '...' : row.completed}
                </strong>
              ),
            },
            {
              key: 'open',
              header: 'Đơn mở',
              render: (row) => (
                <span className={row.open > 0 ? 'metric-value-warning' : undefined}>
                  {row.isLoading ? '...' : row.open}
                </span>
              ),
            },
          ]}
          emptyDescription="Không có outlet nào có session hôm nay."
          emptyTitle="Chưa có dữ liệu ca"
          loading={outletStatsQuery.isLoading}
          loadingTitle="Đang tải thống kê cuối ca..."
          rowKey={(row) => row.outletId}
          rows={visibleOutletStats}
        />
      </Card>
    </DashboardLayout>
  )
}
