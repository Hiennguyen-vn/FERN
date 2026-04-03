import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
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
  const commandMetrics = useMemo(() => {
    const totalRevenue = visibleOutletStats.reduce((sum, row) => sum + row.totalRevenue, 0)
    const totalOrders = visibleOutletStats.reduce((sum, row) => sum + row.totalOrders, 0)
    const openOrders = visibleOutletStats.reduce((sum, row) => sum + row.open, 0)
    const avgTransactionValue = totalOrders > 0 ? totalRevenue / totalOrders : 0
    const topOutletStat = visibleOutletStats.reduce<(typeof visibleOutletStats)[number] | null>((current, row) => {
      if (!current || row.totalRevenue > current.totalRevenue) {
        return row
      }

      return current
    }, null)
    const topOutlet = visibleOutlets.find((outlet) => outlet.id === topOutletStat?.outletId) ?? null
    const attentionCount =
      visibleOutlets.filter((outlet) => outlet.status !== 'ACTIVE').length +
      visibleOutletStats.filter((row) => row.open > 0).length

    const revenueMixRows = visibleOutletStats
      .map((row) => ({
        ...row,
        outlet: visibleOutlets.find((outlet) => outlet.id === row.outletId) ?? null,
        share: totalRevenue > 0 ? Math.max(6, Math.round((row.totalRevenue / totalRevenue) * 100)) : 0,
      }))
      .sort((left, right) => right.totalRevenue - left.totalRevenue)
      .slice(0, 6)

    const watchlistRows = visibleOutlets
      .map((outlet) => {
        const stat = visibleOutletStats.find((row) => row.outletId === outlet.id)
        const open = stat?.open ?? 0
        const priority = (outlet.status !== 'ACTIVE' ? 2 : 0) + (open > 0 ? 2 : 0) + (!outlet.email && !outlet.phone ? 1 : 0)

        return {
          contact: buildOutletContactLabel(outlet),
          open,
          outlet,
          priority,
          revenue: stat?.totalRevenue ?? 0,
          sessionStatus: stat?.sessionStatus ?? 'NO_SESSION',
        }
      })
      .sort((left, right) => right.priority - left.priority || right.revenue - left.revenue)
      .slice(0, 5)

    return {
      attentionCount,
      avgTransactionValue,
      openOrders,
      revenueMixRows,
      topOutlet,
      topOutletStat,
      totalOrders,
      totalRevenue,
      watchlistRows,
    }
  }, [visibleOutletStats, visibleOutlets])

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

      <section className="surface-panel regional-command-stage">
        <div className="page-heading regional-command-copy">
          <p className="workspace-breadcrumb">Regional Ops / Dashboard</p>
          <div className="regional-command-meta">
            <span className="action-hub-persona-badge">{regionQuery.data?.code ?? `Region #${regionalContext.resolvedRegionId}`}</span>
            <span className="meta-chip">{regionQuery.data?.timezoneName ?? 'Timezone unavailable'}</span>
            <span className="meta-chip">Outlet scope {selectedOutletId ? `#${selectedOutletId}` : 'All visible'}</span>
          </div>
          <strong className="action-summary-title">Current regional context</strong>
          <p className="muted-text">
            {buildRegionalScopeLabel(regionQuery.data?.id, regionQuery.data)} is pinned as the active oversight scope.
            Use the topbar selectors to shift region or outlet before drilling into performance.
          </p>
          <div className="meta-grid">
            <span>Region: {buildRegionalScopeLabel(regionQuery.data?.id, regionQuery.data)}</span>
            <span>Currency: {regionQuery.data?.currencyCode ?? 'N/A'}</span>
            <span>Parent region: {regionQuery.data?.parentRegionId ? `#${regionQuery.data.parentRegionId}` : 'Root region'}</span>
            <span>Updated: {formatRegionalInstant(regionQuery.data?.updatedAt)}</span>
          </div>
        </div>

        <div className="regional-command-kpis workspace-stats-grid">
          <article className="workspace-stat-card">
            <div className="workspace-stat-topline">
              <span className="workspace-stat-icon">
                <AppIcon filled name="payments" />
              </span>
              <span className="workspace-stat-badge success">Today</span>
            </div>
            <div className="compact-stack">
              <span className="workspace-stat-label">Regional revenue</span>
              <strong className="workspace-stat-value">
                {formatMoney(commandMetrics.totalRevenue, regionQuery.data?.currencyCode ?? 'VND')}
              </strong>
            </div>
          </article>

          <article className="workspace-stat-card">
            <div className="workspace-stat-topline">
              <span className="workspace-stat-icon">
                <AppIcon filled name="receipt_long" />
              </span>
              <span className="workspace-stat-badge success">Live</span>
            </div>
            <div className="compact-stack">
              <span className="workspace-stat-label">Avg transaction value</span>
              <strong className="workspace-stat-value">
                {formatMoney(commandMetrics.avgTransactionValue, regionQuery.data?.currencyCode ?? 'VND')}
              </strong>
            </div>
          </article>

          <article className="workspace-stat-card">
            <div className="workspace-stat-topline">
              <span className="workspace-stat-icon">
                <AppIcon filled name="storefront" />
              </span>
              <span className="workspace-stat-badge">Lead outlet</span>
            </div>
            <div className="compact-stack">
              <span className="workspace-stat-label">Top revenue outlet</span>
              <strong className="workspace-stat-value regional-stat-text">
                {commandMetrics.topOutlet?.name ?? 'No revenue yet'}
              </strong>
              {commandMetrics.topOutletStat ? (
                <p className="muted-text">
                  {formatMoney(commandMetrics.topOutletStat.totalRevenue, commandMetrics.topOutletStat.currencyCode)} across{' '}
                  {commandMetrics.topOutletStat.completed} completed orders.
                </p>
              ) : null}
            </div>
          </article>

          <article className={`workspace-stat-card ${commandMetrics.attentionCount > 0 ? 'warning' : ''}`}>
            <div className="workspace-stat-topline">
              <span className="workspace-stat-icon">
                <AppIcon filled name="warning" />
              </span>
              <span className={`workspace-stat-badge ${commandMetrics.attentionCount > 0 ? 'warning' : 'success'}`}>
                Attention
              </span>
            </div>
            <div className="compact-stack">
              <span className="workspace-stat-label">Open orders now</span>
              <strong className="workspace-stat-value">{commandMetrics.openOrders}</strong>
              <p className="muted-text">
                {commandMetrics.attentionCount} outlets currently need follow-up or still have open tickets.
              </p>
            </div>
          </article>
        </div>
      </section>

      <section className="regional-command-grid">
        <section className="surface-panel">
          <div className="page-header">
            <div>
              <p className="eyebrow">Selected KPIs</p>
              <h2 className="card-title">Today revenue mix</h2>
              <p className="muted-text">Revenue distribution across visible outlets in the current region context.</p>
            </div>
          </div>
          <div className="regional-meter-list">
            {commandMetrics.revenueMixRows.length > 0 ? (
              commandMetrics.revenueMixRows.map((row) => (
                <article className="regional-meter-row" key={row.outletId}>
                  <div className="regional-meter-copy">
                    <strong>{row.outlet?.name ?? `Outlet #${row.outletId}`}</strong>
                    <span className="muted-text">
                      {row.outlet?.code ?? `#${row.outletId}`} · {row.completed} completed · {row.open} open
                    </span>
                  </div>
                  <div className="regional-meter-visual">
                    <svg
                      aria-label={`${row.outlet?.name ?? `Outlet #${row.outletId}`}: ${formatMoney(row.totalRevenue, row.currencyCode)}`}
                      className="regional-meter-svg"
                      preserveAspectRatio="none"
                      role="img"
                      viewBox="0 0 100 10"
                    >
                      <rect className="regional-meter-track" height="10" rx="5" ry="5" width="100" x="0" y="0" />
                      <rect className="regional-meter-fill" height="10" rx="5" ry="5" width={row.share} x="0" y="0" />
                    </svg>
                    <strong>{formatMoney(row.totalRevenue, row.currencyCode)}</strong>
                  </div>
                </article>
              ))
            ) : (
              <EmptyState
                description="No outlet sessions have reported revenue for the current region context."
                title="No revenue mix available"
              />
            )}
          </div>
        </section>

        <aside className="surface-panel regional-watchlist-panel">
          <div className="page-header">
            <div>
              <p className="eyebrow">Plan status</p>
              <h2 className="card-title">Outlet watchlist</h2>
            </div>
          </div>
          <div className="regional-watchlist">
            {commandMetrics.watchlistRows.map((row) => (
              <article className="regional-watchlist-item" key={row.outlet.id}>
                <div className="regional-watchlist-copy">
                  <strong>{row.outlet.name}</strong>
                  <span className="muted-text">{row.contact}</span>
                </div>
                <div className="compact-stack-tight">
                  <StatusBadge status={row.outlet.status} />
                  <span className={row.open > 0 ? 'metric-value-warning' : 'muted-text'}>
                    {row.open > 0 ? `${row.open} open orders` : row.sessionStatus}
                  </span>
                </div>
              </article>
            ))}
          </div>
        </aside>
      </section>

      <section className="surface-panel">
        <div className="page-header">
          <div>
            <h2 className="card-title">Outlet performance matrix</h2>
            <p className="muted-text">
              Visible outlets in region {regionQuery.data?.code ?? regionalContext.resolvedRegionId}, grouped for drill-down into outlet oversight.
            </p>
          </div>
        </div>
        <DataTable
          columns={columns}
          emptyDescription="Không có outlet nào trong scoped region hiện tại."
          emptyTitle="No visible outlets"
          rowKey={(outlet) => outlet.id}
          rows={visibleOutlets}
        />
      </section>

      <section className="surface-panel">
        <div className="page-header">
          <div>
            <h2 className="card-title">Thống kê cuối ca theo outlet (hôm nay)</h2>
            <p className="muted-text">Live POS session snapshot for outlets that reported activity today.</p>
          </div>
        </div>
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
      </section>
    </DashboardLayout>
  )
}
