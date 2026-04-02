import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import {
  Button,
  DataTable,
  ErrorState,
  PermissionDeniedInline,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { usePurchaseOrders } from '../hooks/usePurchaseOrder'
import { useSuppliers } from '../hooks/useSuppliers'
import type { PurchaseOrder, Supplier } from '../model/procurement.types'
import { canReadPurchaseOrders, canReadSuppliers } from '../services/procurementPermission.service'

const statusOptions: SelectOption[] = [
  { label: 'All statuses', value: '' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Submitted', value: 'SUBMITTED' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Ordered', value: 'ORDERED' },
  { label: 'Partially received', value: 'PARTIALLY_RECEIVED' },
  { label: 'Completed', value: 'COMPLETED' },
  { label: 'Cancelled', value: 'CANCELLED' },
]

function formatCompactAmount(amount: number) {
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    maximumFractionDigits: amount >= 1_000_000 ? 1 : 0,
  }).format(amount)
}

function formatAmount(amount: number) {
  return new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 0,
  }).format(amount)
}

function formatMonthLabel(value: string) {
  return new Intl.DateTimeFormat('en-US', { month: 'short' }).format(new Date(`${value}-01`))
}

function getMonthKey(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'Unknown'
  }

  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`
}

function toAmount(value: string) {
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : 0
}

function getLeadDays(order: PurchaseOrder) {
  if (!order.expectedDeliveryDate) {
    return null
  }

  const start = new Date(order.orderDate)
  const end = new Date(order.expectedDeliveryDate)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return null
  }

  return Math.max(0, Math.round((end.getTime() - start.getTime()) / 86_400_000))
}

function isOpenOrder(status: string) {
  return !['COMPLETED', 'CANCELLED'].includes(status.toUpperCase())
}

export function PurchaseOrderListPage() {
  usePageTitle('Procurement Workspace')

  const principal = usePrincipal()
  const [outletId, setOutletId] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const [status, setStatus] = useState('')
  const canRead = canReadPurchaseOrders(principal)
  const canReadSupplierDirectory = canReadSuppliers(principal)

  const query = usePurchaseOrders(
    canRead
      ? {
          outletId: outletId ? Number(outletId) : undefined,
          supplierId: supplierId ? Number(supplierId) : undefined,
          status: status || undefined,
          limit: 50,
        }
      : undefined,
  )
  const suppliersQuery = useSuppliers({ enabled: canRead && canReadSupplierDirectory })

  const purchaseOrders = query.data ?? []

  const supplierLookup = useMemo(
    () =>
      new Map((suppliersQuery.data ?? []).map((supplier: Supplier) => [supplier.id, supplier] as const)),
    [suppliersQuery.data],
  )

  const dashboardMetrics = useMemo(() => {
    const today = new Date()
    const totalVolume = purchaseOrders.reduce((sum, order) => sum + toAmount(order.totalAmount), 0)
    const pendingApprovals = purchaseOrders.filter((order) => order.status === 'SUBMITTED').length
    const activeOrders = purchaseOrders.filter((order) => isOpenOrder(order.status))
    const maturedOrders = activeOrders.filter((order) =>
      ['APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED'].includes(order.status.toUpperCase()),
    ).length
    const lateDeliveries = purchaseOrders.filter((order) => {
      if (!order.expectedDeliveryDate || !isOpenOrder(order.status)) {
        return false
      }
      const expected = new Date(order.expectedDeliveryDate)
      return !Number.isNaN(expected.getTime()) && expected.getTime() < today.getTime()
    }).length

    return {
      activeOrders: activeOrders.length,
      lateDeliveries,
      pendingApprovals,
      throughputRate:
        activeOrders.length > 0 ? Math.round((maturedOrders / activeOrders.length) * 100) : 0,
      totalVolume,
    }
  }, [purchaseOrders])

  const trendBars = useMemo(() => {
    const monthTotals = new Map<string, number>()

    purchaseOrders.forEach((order) => {
      const key = getMonthKey(order.orderDate)
      monthTotals.set(key, (monthTotals.get(key) ?? 0) + toAmount(order.totalAmount))
    })

    const rows = Array.from(monthTotals.entries())
      .sort((left, right) => left[0].localeCompare(right[0]))
      .slice(-6)
      .map(([key, total]) => ({ key, label: formatMonthLabel(key), total }))

    const maxTotal = Math.max(...rows.map((row) => row.total), 0)
    return rows.map((row, index) => ({
      ...row,
      height: maxTotal > 0 ? Math.max(16, Math.round((row.total / maxTotal) * 100)) : 16,
      muted: index !== rows.length - 1,
    }))
  }, [purchaseOrders])

  const largeOrders = useMemo(
    () => [...purchaseOrders].sort((left, right) => toAmount(right.totalAmount) - toAmount(left.totalAmount)).slice(0, 3),
    [purchaseOrders],
  )

  const supplierPulseRows = useMemo(() => {
    const supplierStats = new Map<
      number,
      {
        averageLeadTime: number | null
        lateOrders: number
        openOrders: number
        orderCount: number
        supplierId: number
        totalLeadTime: number
        totalSpend: number
      }
    >()

    purchaseOrders.forEach((order) => {
      const current = supplierStats.get(order.supplierId) ?? {
        averageLeadTime: null,
        lateOrders: 0,
        openOrders: 0,
        orderCount: 0,
        supplierId: order.supplierId,
        totalLeadTime: 0,
        totalSpend: 0,
      }

      const leadDays = getLeadDays(order)
      const next = {
        ...current,
        lateOrders:
          current.lateOrders +
          (order.expectedDeliveryDate && isOpenOrder(order.status) && new Date(order.expectedDeliveryDate) < new Date()
            ? 1
            : 0),
        openOrders: current.openOrders + (isOpenOrder(order.status) ? 1 : 0),
        orderCount: current.orderCount + 1,
        totalLeadTime: current.totalLeadTime + (leadDays ?? 0),
        totalSpend: current.totalSpend + toAmount(order.totalAmount),
      }

      next.averageLeadTime = next.orderCount > 0 ? next.totalLeadTime / next.orderCount : null
      supplierStats.set(order.supplierId, next)
    })

    return Array.from(supplierStats.values())
      .sort((left, right) => right.totalSpend - left.totalSpend)
      .slice(0, 6)
  }, [purchaseOrders])

  const supplierPulseColumns = useMemo<Array<DataTableColumn<(typeof supplierPulseRows)[number]>>>(
    () => [
      {
        key: 'supplier',
        header: 'Supplier',
        render: (row) => {
          const supplier = supplierLookup.get(row.supplierId)
          return (
            <div className="cell-stack">
              <strong>{supplier?.name ?? `Supplier #${row.supplierId}`}</strong>
              <span className="cell-subtitle">{supplier?.supplierCode ?? 'Supplier directory unavailable'}</span>
            </div>
          )
        },
      },
      {
        key: 'spend',
        header: 'PO Volume',
        render: (row) => formatAmount(row.totalSpend),
      },
      {
        key: 'leadTime',
        header: 'Avg Lead Time',
        render: (row) => (row.averageLeadTime == null ? 'Open-ended' : `${row.averageLeadTime.toFixed(1)} days`),
      },
      {
        key: 'openOrders',
        header: 'Active POs',
        render: (row) => row.openOrders,
      },
      {
        key: 'health',
        header: 'Status',
        render: (row) => {
          const tone = row.lateOrders > 0 ? 'danger' : row.openOrders > 2 ? 'warning' : 'success'
          const label = row.lateOrders > 0 ? 'At risk' : row.openOrders > 2 ? 'Monitoring' : 'Healthy'
          return (
            <span className="health-line">
              <span className={`health-dot ${tone}`} />
              {label}
            </span>
          )
        },
      },
    ],
    [supplierLookup],
  )

  const poColumns = useMemo<Array<DataTableColumn<PurchaseOrder>>>(
    () => [
      {
        key: 'poNumber',
        header: 'Purchase Order',
        render: (row) => (
          <div className="cell-stack">
            <Link className="queue-item-title" to={`/procurement/purchase-orders/${row.id}`}>
              {row.poNumber}
            </Link>
            <span className="cell-subtitle">{supplierLookup.get(row.supplierId)?.name ?? `Supplier #${row.supplierId}`}</span>
          </div>
        ),
      },
      {
        key: 'outlet',
        header: 'Outlet',
        render: (row) => (
          <div className="cell-stack">
            <strong>Outlet #{row.outletId}</strong>
            <span className="cell-subtitle">Region #{row.regionId}</span>
          </div>
        ),
      },
      {
        key: 'orderDate',
        header: 'Order Date',
        render: (row) => row.orderDate,
      },
      {
        key: 'amount',
        header: 'Total',
        render: (row) => formatAmount(toAmount(row.totalAmount)),
      },
      {
        key: 'status',
        header: 'Status',
        render: (row) => <StatusBadge status={row.status} />,
      },
    ],
    [supplierLookup],
  )

  if (!canRead) {
    return (
      <DashboardLayout
        description="Regional purchase order oversight, approvals, and supplier activity."
        eyebrow="Regional Operations"
        title="Procurement Workspace"
      >
        <PermissionDeniedInline message="You need procurement.po.read to open the procurement workspace." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <Link to="/procurement/purchase-orders/new">
          <Button>New purchase order</Button>
        </Link>
      }
      description="Regional purchase order oversight, approvals, and supplier activity."
      eyebrow="Regional Operations"
      title="Procurement Workspace"
    >
      <section className="workspace-stats-grid">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge success">Live view</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">Total PO volume</span>
            <strong className="workspace-stat-value">{formatCompactAmount(dashboardMetrics.totalVolume)}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="pending_actions" />
            </span>
            <span className="workspace-stat-badge warning">Urgent</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">Pending approvals</span>
            <strong className="workspace-stat-value">{dashboardMetrics.pendingApprovals}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="verified" />
            </span>
            <span className="workspace-stat-badge success">Ops flow</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">Approval throughput</span>
            <strong className="workspace-stat-value">{dashboardMetrics.throughputRate}%</strong>
          </div>
          <div className="metric-meter">
            <div className="metric-meter-fill" style={{ width: `${dashboardMetrics.throughputRate}%` }} />
          </div>
        </article>
        <article className="workspace-stat-card danger">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="warning" />
            </span>
            <span className="workspace-stat-badge danger">Action</span>
          </div>
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span className="workspace-stat-label">Late deliveries</span>
            <strong className="workspace-stat-value">{dashboardMetrics.lateDeliveries}</strong>
          </div>
        </article>
      </section>

      <section className="workspace-filter-bar" aria-label="Procurement filters">
        <div className="workspace-filter-field" style={{ flex: 1, minWidth: '13rem' }}>
          <span className="eyebrow">Outlet</span>
          <input
            className="workspace-inline-select"
            inputMode="numeric"
            onChange={(event) => setOutletId(event.target.value)}
            placeholder="All outlets"
            value={outletId}
          />
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Supplier</span>
          <input
            className="workspace-inline-select"
            inputMode="numeric"
            onChange={(event) => setSupplierId(event.target.value)}
            placeholder="All suppliers"
            value={supplierId}
          />
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Status</span>
          <select className="workspace-inline-select" onChange={(event) => setStatus(event.target.value)} value={status}>
            {statusOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </section>

      {query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load procurement workspace'}
          onAction={() => void query.refetch()}
          title="Unable to load procurement workspace"
        />
      ) : (
        <>
          <section className="workspace-split-grid">
            <div className="surface-panel">
              <div className="page-header">
                <div>
                  <h2 className="card-title">Procurement Trends</h2>
                  <p className="muted-text">Monthly purchase order volume for the current procurement slice.</p>
                </div>
                <div className="trend-toggle-group" aria-hidden="true">
                  <span className="trend-toggle-chip">Volume</span>
                  <span className="trend-toggle-chip is-active">Spend</span>
                </div>
              </div>
              <div className="trend-card-body">
                <div className="trend-bars" aria-label="Procurement trend chart">
                  {trendBars.length > 0 ? (
                    trendBars.map((bar) => (
                      <div className="trend-bar-group" key={bar.key}>
                        <div
                          className={`trend-bar ${bar.muted ? 'muted' : ''}`}
                          style={{ height: `${bar.height}%` }}
                          title={`${bar.label}: ${formatAmount(bar.total)}`}
                        />
                        <span className="trend-bar-label">{bar.label}</span>
                      </div>
                    ))
                  ) : (
                    <p className="muted-text">No order history is available for the current filter set.</p>
                  )}
                </div>
              </div>
            </div>

            <aside className="surface-grid-side">
              <section className="surface-panel">
                <div className="page-header">
                  <div>
                    <h2 className="card-title">Large Orders Queue</h2>
                    <p className="muted-text">Highest-value orders in the current procurement slice.</p>
                  </div>
                </div>
                <div className="queue-list">
                  {largeOrders.length > 0 ? (
                    largeOrders.map((order) => (
                      <article className="queue-item" key={order.id}>
                        <div className="queue-item-head">
                          <Link className="queue-item-title" to={`/procurement/purchase-orders/${order.id}`}>
                            {order.poNumber}
                          </Link>
                          <StatusBadge status={order.status} />
                        </div>
                        <div className="cell-stack">
                          <strong>{supplierLookup.get(order.supplierId)?.name ?? `Supplier #${order.supplierId}`}</strong>
                          <span className="cell-subtitle">Outlet #{order.outletId}</span>
                        </div>
                        <strong className="queue-item-amount">{formatAmount(toAmount(order.totalAmount))}</strong>
                      </article>
                    ))
                  ) : (
                    <p className="muted-text">No large orders are available for this view.</p>
                  )}
                </div>
                {purchaseOrders.length > 0 ? (
                  <Link className="queue-item-link" to="/procurement/purchase-orders">
                    View full queue
                  </Link>
                ) : null}
              </section>
            </aside>
          </section>

          <section className="surface-panel">
            <div className="page-header">
              <div>
                <h2 className="card-title">Supplier Pulse</h2>
                <p className="muted-text">Live vendor activity derived from the current purchase order list.</p>
              </div>
            </div>
            <DataTable
              columns={supplierPulseColumns}
              emptyDescription="There are no supplier aggregates for the current filter set."
              emptyTitle="No supplier activity"
              rows={supplierPulseRows}
              rowKey={(row) => row.supplierId}
            />
          </section>

          <section className="surface-panel">
            <div className="page-header">
              <div>
                <h2 className="card-title">Purchase Order Queue</h2>
                <p className="muted-text">Operational queue for drilling into detail, approvals, and receipts.</p>
              </div>
            </div>
            <DataTable
              columns={poColumns}
              emptyDescription="No purchase orders match the current filter set."
              emptyTitle="No purchase orders"
              loading={query.isLoading}
              rows={purchaseOrders}
              rowKey={(row) => row.id}
            />
          </section>
        </>
      )}
    </DashboardLayout>
  )
}
