import { useMemo, useState } from 'react'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Badge,
  Card,
  DataTable,
  EmptyState,
  PermissionDeniedInline,
  ReadonlyBanner,
  SummaryCards,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { formatMoney, formatDate } from '@shared/formatters'
import { useRegionalOutlets } from '../../regional-ops/hooks/useRegionalOps'
import { useOutletTodayStats } from '../../pos/hooks/useOutletStats'
import { canReadRevenueReport } from '../services/reportsUiPolicy.service'

interface OutletRevenueStat {
  outletId: number
  outletName: string
  outletCode: string
  sessionStatus: string
  currencyCode: string
  totalRevenue: number
  cashCollected: number
  nonCashCollected: number
  completed: number
  cancelled: number
  open: number
  isLoading: boolean
}

function buildRevenueSummaryCards(stats: OutletRevenueStat[]) {
  const totalRevenue = stats.reduce((sum, s) => sum + s.totalRevenue, 0)
  const totalCompleted = stats.reduce((sum, s) => sum + s.completed, 0)
  const totalOpen = stats.reduce((sum, s) => sum + s.open, 0)
  const activeOutlets = stats.filter((s) => s.sessionStatus === 'OPEN').length

  return [
    {
      label: 'Tổng doanh thu hôm nay',
      value: formatMoney(totalRevenue, 'VND'),
      tone: 'success' as const,
    },
    {
      label: 'Đơn hoàn thành',
      value: totalCompleted.toString(),
      tone: 'success' as const,
    },
    {
      label: 'Đơn đang mở',
      value: totalOpen.toString(),
      tone: (totalOpen > 0 ? 'warning' : 'success') as 'warning' | 'success',
    },
    {
      label: 'Outlet đang mở ca',
      value: activeOutlets.toString(),
      tone: 'default' as const,
    },
  ]
}

function exportRevenueCsv(rows: OutletRevenueStat[], date: string) {
  const headers = ['Outlet', 'Code', 'Session Status', 'Revenue', 'Cash', 'Non-Cash', 'Completed', 'Open', 'Cancelled', 'Currency']
  const csvRows = rows.map((r) => [
    r.outletName,
    r.outletCode,
    r.sessionStatus,
    r.totalRevenue,
    r.cashCollected,
    r.nonCashCollected,
    r.completed,
    r.open,
    r.cancelled,
    r.currencyCode,
  ].map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))
  const content = [headers.join(','), ...csvRows].join('\n')
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `outlet-revenue-${date}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

export function OutletRevenueReportPage() {
  usePageTitle('Outlet Revenue Summary')
  const principal = usePrincipal()
  const { outletIds } = useScopeContext()
  const canRead = canReadRevenueReport(principal)
  const today = new Date().toISOString().slice(0, 10)
  const [exportDate] = useState(today)

  const outletsQuery = useRegionalOutlets(outletIds, { enabled: canRead && outletIds.length > 0 })
  const statsQuery = useOutletTodayStats(outletIds, canRead && outletIds.length > 0)

  const rows = useMemo<OutletRevenueStat[]>(() => {
    return statsQuery.outletStats.map((stat) => {
      const outlet = outletsQuery.rows.find((o) => o.id === stat.outletId)
      return {
        outletId: stat.outletId,
        outletName: outlet?.name ?? `Outlet #${stat.outletId}`,
        outletCode: outlet?.code ?? '',
        sessionStatus: stat.sessionStatus,
        currencyCode: stat.currencyCode ?? 'VND',
        totalRevenue: stat.totalRevenue ?? 0,
        cashCollected: stat.cashCollected ?? 0,
        nonCashCollected: stat.nonCashCollected ?? 0,
        completed: stat.completed ?? 0,
        cancelled: stat.cancelled ?? 0,
        open: stat.open ?? 0,
        isLoading: stat.isLoading,
      }
    })
  }, [statsQuery.outletStats, outletsQuery.rows])

  const summaryCards = useMemo(() => buildRevenueSummaryCards(rows.filter((r) => !r.isLoading)), [rows])

  const columns: Array<DataTableColumn<OutletRevenueStat>> = [
    {
      key: 'outlet',
      header: 'Outlet',
      render: (row) => (
        <div className="page-stack" style={{ gap: '0.25rem' }}>
          <strong>{row.outletName}</strong>
          <span className="muted-text">{row.outletCode}</span>
        </div>
      ),
    },
    {
      key: 'sessionStatus',
      header: 'Ca POS',
      render: (row) => <Badge tone={row.sessionStatus === 'OPEN' ? 'success' : 'neutral'}>{row.sessionStatus}</Badge>,
    },
    {
      key: 'totalRevenue',
      header: 'Doanh thu',
      render: (row) =>
        row.isLoading ? (
          <span className="muted-text">Loading…</span>
        ) : (
          <strong style={{ color: row.totalRevenue > 0 ? 'var(--color-success, #16a34a)' : undefined }}>
            {formatMoney(row.totalRevenue, row.currencyCode)}
          </strong>
        ),
    },
    {
      key: 'cashCollected',
      header: 'Tiền mặt',
      render: (row) => (row.isLoading ? '…' : formatMoney(row.cashCollected, row.currencyCode)),
    },
    {
      key: 'nonCashCollected',
      header: 'Thẻ/Ví điện tử',
      render: (row) => (row.isLoading ? '…' : formatMoney(row.nonCashCollected, row.currencyCode)),
    },
    {
      key: 'completed',
      header: 'Đơn xong',
      render: (row) => (
        <span style={{ color: row.completed > 0 ? 'var(--color-success)' : undefined }}>
          {row.isLoading ? '…' : row.completed}
        </span>
      ),
    },
    {
      key: 'open',
      header: 'Đơn mở',
      render: (row) => (
        <span style={{ color: row.open > 0 ? 'var(--color-warning, #d97706)' : undefined }}>
          {row.isLoading ? '…' : row.open}
        </span>
      ),
    },
    {
      key: 'cancelled',
      header: 'Hủy',
      render: (row) => (row.isLoading ? '…' : row.cancelled),
    },
  ]

  if (!canRead) {
    return (
      <DashboardLayout
        description="Xem thống kê doanh thu cuối ca theo outlet cho Region Manager."
        title="Outlet Revenue Summary"
      >
        <PermissionDeniedInline message="Bạn cần quyền report.revenue.read để xem báo cáo doanh thu." />
      </DashboardLayout>
    )
  }

  if (outletIds.length === 0) {
    return (
      <DashboardLayout
        description="Xem thống kê doanh thu cuối ca theo outlet cho Region Manager."
        title="Outlet Revenue Summary"
      >
        <ReadonlyBanner message="Báo cáo này chỉ hiển thị khi bạn có ít nhất một outlet trong scope." />
        <EmptyState
          description="Không tìm thấy outlet nào trong scope của bạn để tổng hợp doanh thu."
          title="Không có outlet trong scope"
        />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <button
          className="button button--secondary button--sm"
          disabled={rows.length === 0}
          id="btn-export-outlet-revenue"
          onClick={() => exportRevenueCsv(rows, exportDate)}
          type="button"
        >
          ⬇ Export CSV
        </button>
      }
      description={`Thống kê doanh thu theo outlet hôm nay (${formatDate(today)}). Dữ liệu thời gian thực từ POS sessions.`}
      title="Outlet Revenue Summary"
    >
      <Card title="Tổng hợp Region Manager">
        <div className="meta-grid">
          <span>Ngày: <strong>{formatDate(today)}</strong></span>
          <span>Số outlets: <strong>{outletIds.length}</strong></span>
          <span>Outlets có dữ liệu: <strong>{rows.filter((r) => r.completed > 0 || r.totalRevenue > 0).length}</strong></span>
        </div>
      </Card>

      <SummaryCards items={summaryCards} />

      <DataTable
        columns={columns}
        emptyDescription="Hôm nay chưa có outlet nào trong scope mở phiên POS hoặc có đơn hàng."
        emptyTitle="Chưa có dữ liệu doanh thu"
        loading={statsQuery.isLoading || outletsQuery.isLoading}
        loadingDescription="Đang tải dữ liệu doanh thu từ tất cả outlets trong scope..."
        loadingTitle="Đang tải Outlet Revenue Summary"
        rowKey={(row) => row.outletId}
        rows={rows}
      />
    </DashboardLayout>
  )
}
