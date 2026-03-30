import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
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

  const visibleOutlets = useMemo(
    () => outletsQuery.rows.filter((outlet) => outlet.regionId === regionalContext.resolvedRegionId),
    [outletsQuery.rows, regionalContext.resolvedRegionId],
  )

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
    </DashboardLayout>
  )
}
