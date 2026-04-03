import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import {
  Button,
  DataTable,
  ErrorState,
  Input,
  PermissionDeniedInline,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { parsePositiveInt } from '@shared/validators/parseInput'
import { useSupplierPayments } from '../hooks/useSupplierPayment'
import type { SupplierPayment } from '../model/procurement.types'
import { canReadPayments, canRecordPayment } from '../services/procurementPermission.service'

function formatPaymentAmount(amount: number, currencyCode: string) {
  try {
    return new Intl.NumberFormat('en-US', {
      currency: currencyCode,
      maximumFractionDigits: 2,
      style: 'currency',
    }).format(amount)
  } catch {
    return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(amount)} ${currencyCode}`
  }
}

function formatPaymentTimeLabel(paymentTime: string) {
  const parsed = Date.parse(paymentTime)
  if (Number.isNaN(parsed)) {
    return paymentTime
  }

  return new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(parsed))
}

export function SupplierPaymentListPage() {
  usePageTitle('Supplier Payments')

  const principal = usePrincipal()
  const [supplierId, setSupplierId] = useState('')

  const canRead = canReadPayments(principal)
  const parsedSupplierId = parsePositiveInt(supplierId) ?? undefined

  const query = useSupplierPayments({ supplierId: parsedSupplierId, limit: 50 })
  const payments = query.data ?? []
  const paymentSummary = useMemo(() => {
    const totalAmount = payments.reduce((sum, payment) => sum + payment.amount, 0)
    const bankTransfers = payments.filter((payment) => payment.paymentMethod === 'BANK_TRANSFER').length
    const allocationCount = payments.reduce((sum, payment) => sum + payment.invoiceAllocations.length, 0)
    const recentPayments = [...payments]
      .sort((left, right) => Date.parse(right.paymentTime) - Date.parse(left.paymentTime))
      .slice(0, 4)

    return {
      allocationCount,
      bankTransfers,
      recentPayments,
      totalAmount,
    }
  }, [payments])

  const columns: Array<DataTableColumn<SupplierPayment>> = [
    { key: 'id', header: 'ID', render: (row) => `#${row.id}` },
    { key: 'paymentNumber', header: 'Payment #', render: (row) => row.paymentNumber },
    { key: 'supplierId', header: 'Supplier', render: (row) => `#${row.supplierId}` },
    { key: 'paymentMethod', header: 'Method', render: (row) => row.paymentMethod },
    { key: 'amount', header: 'Amount', render: (row) => formatPaymentAmount(row.amount, row.currencyCode) },
    { key: 'currencyCode', header: 'Currency', render: (row) => row.currencyCode },
  ]

  if (!canRead) {
    return (
      <DashboardLayout description="Browse and manage supplier payments." title="Supplier Payments">
        <PermissionDeniedInline message="Bạn cần quyền procurement.payment.read để xem supplier payments." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        canRecordPayment(principal) ? (
          <Link to="/finance/supplier-payments/new">
            <Button>Record Payment</Button>
          </Link>
        ) : undefined
      }
      description="Browse and manage supplier payments."
      title="Supplier Payments"
    >
      <section className="surface-panel command-stage" aria-label="Supplier payments command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Finance workspace</span>
            <span className="meta-chip">Loaded records {payments.length}</span>
            <span className={parsedSupplierId ? 'meta-chip-success' : 'meta-chip'}>
              {parsedSupplierId ? `Supplier #${parsedSupplierId}` : 'All suppliers'}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Finance / Supplier Payments</p>
            <strong className="action-summary-title">Current payment schedule slice</strong>
            <p className="muted-text">
              Browse recorded supplier payments with method, currency, and allocation context before
              moving into new payment capture.
            </p>
          </div>
          <div className="meta-grid">
            <span>Payments loaded: {payments.length}</span>
            <span>Supplier filter: {parsedSupplierId ? `#${parsedSupplierId}` : 'All suppliers'}</span>
            <span>Bank transfers: {paymentSummary.bankTransfers}</span>
            <span>Invoice allocations: {paymentSummary.allocationCount}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Recent activity</span>
            <strong>Latest payment records</strong>
            <p>
              Keep the most recent payment postings in view so finance can confirm timing and payment
              method without leaving the queue.
            </p>
          </div>
          <div className="command-support-list">
            {paymentSummary.recentPayments.length > 0 ? (
              paymentSummary.recentPayments.map((payment) => (
                <article className="command-support-item" key={payment.id}>
                  <div className="command-support-copy">
                    <strong>{payment.paymentNumber}</strong>
                    <span className="muted-text">Supplier #{payment.supplierId}</span>
                    <span className="muted-text">{formatPaymentTimeLabel(payment.paymentTime)}</span>
                  </div>
                  <div className="command-support-stack">
                    <span className="command-support-metric">
                      {formatPaymentAmount(payment.amount, payment.currencyCode)}
                    </span>
                    <span className="meta-chip">{payment.paymentMethod}</span>
                  </div>
                </article>
              ))
            ) : (
              <div className="command-empty-note">
                No supplier payments are loaded yet. Record a payment or broaden the supplier filter.
              </div>
            )}
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Supplier payment summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge success">Loaded</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Payment records</span>
            <strong className="workspace-stat-value">{payments.length}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="account_balance" />
            </span>
            <span className="workspace-stat-badge">Value</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Loaded payment value</span>
            <strong className="workspace-stat-value">
              {formatPaymentAmount(paymentSummary.totalAmount, payments[0]?.currencyCode ?? 'USD')}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="sync_alt" />
            </span>
            <span className="workspace-stat-badge">Method</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Bank transfers</span>
            <strong className="workspace-stat-value">{paymentSummary.bankTransfers}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="fact_check" />
            </span>
            <span className="workspace-stat-badge warning">Match</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Invoice allocations</span>
            <strong className="workspace-stat-value">{paymentSummary.allocationCount}</strong>
          </div>
        </article>
      </section>

      <section className="surface-panel command-filter-panel">
        <div className="state-panel-heading">
          <span className="eyebrow">Queue filter</span>
          <h2 className="card-title">Supplier scope</h2>
          <p className="muted-text">
            Narrow the queue to a single supplier when you need a tighter payment schedule slice.
          </p>
        </div>
        <div className="field-grid">
          <Input
            label="Supplier ID"
            onChange={(e) => setSupplierId(e.target.value)}
            placeholder="Optional"
            type="number"
            value={supplierId}
          />
        </div>
      </section>

      {query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load supplier payments'}
          onAction={() => void query.refetch()}
          title="Không thể tải supplier payments"
        />
      ) : null}

      {!query.error ? (
        <DataTable
          columns={columns}
          emptyDescription="Chưa có supplier payment nào."
          emptyTitle="No supplier payments"
          loading={query.isLoading}
          rows={query.data ?? []}
        />
      ) : null}
    </DashboardLayout>
  )
}
