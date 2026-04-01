import { useMemo } from 'react'
import { Card, SummaryCards } from '@design-system/index'
import type { SummaryCardItem } from '@design-system/index'
import { formatMoney } from '@shared/formatters'
import type { SaleOrder } from '../model/pos.types'

interface PosSessionShiftSummaryProps {
  orders: SaleOrder[]
  currencyCode?: string
  isLoading?: boolean
}

export function computeShiftStats(orders: SaleOrder[]) {
  const completed = orders.filter((o) => String(o.status).toUpperCase() === 'COMPLETED')
  const open = orders.filter((o) => String(o.status).toUpperCase() === 'OPEN')
  const cancelled = orders.filter((o) => String(o.status).toUpperCase() === 'CANCELLED')

  let totalRevenue = 0
  let cashCollected = 0
  let nonCashCollected = 0

  for (const order of completed) {
    totalRevenue += Number(order.totalAmount ?? 0)
    for (const payment of order.payments ?? []) {
      if (String(payment.status).toUpperCase() === 'SUCCESS') {
        const amt = Number(payment.amount ?? 0)
        if (String(payment.paymentMethod).toUpperCase() === 'CASH') {
          cashCollected += amt
        } else {
          nonCashCollected += amt
        }
      }
    }
  }

  return {
    totalOrders: orders.length,
    completed: completed.length,
    open: open.length,
    cancelled: cancelled.length,
    totalRevenue,
    cashCollected,
    nonCashCollected,
  }
}

export function PosSessionShiftSummary({ orders, currencyCode = 'VND', isLoading }: PosSessionShiftSummaryProps) {
  const stats = useMemo(() => computeShiftStats(orders), [orders])

  const cards: SummaryCardItem[] = [
    {
      label: 'Tổng doanh thu',
      value: isLoading ? '...' : formatMoney(stats.totalRevenue, currencyCode),
      tone: stats.totalRevenue > 0 ? 'success' : 'default',
      description: 'Từ các đơn đã hoàn thành',
    },
    {
      label: 'Tiền mặt thu',
      value: isLoading ? '...' : formatMoney(stats.cashCollected, currencyCode),
      tone: 'info',
      description: 'CASH payments thành công',
    },
    {
      label: 'Thu khác (thẻ/ví)',
      value: isLoading ? '...' : formatMoney(stats.nonCashCollected, currencyCode),
      tone: 'default',
      description: 'CARD / EWALLET / BANK',
    },
    {
      label: 'Đơn hoàn thành',
      value: isLoading ? '...' : String(stats.completed),
      tone: stats.completed > 0 ? 'success' : 'default',
      description: `Tổng ${stats.totalOrders} đơn`,
    },
    {
      label: 'Đơn đang mở',
      value: isLoading ? '...' : String(stats.open),
      tone: stats.open > 0 ? 'warning' : 'default',
    },
    {
      label: 'Đơn đã hủy',
      value: isLoading ? '...' : String(stats.cancelled),
      tone: stats.cancelled > 0 ? 'danger' : 'default',
    },
  ]

  return (
    <Card title="📊 Thống kê ca bán hàng">
      <SummaryCards items={cards} />
    </Card>
  )
}
