import { useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import {
  Button,
  Card,
  DataTable,
  ErrorState,
  Input,
  PermissionDeniedInline,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAuthStore } from '@core/auth/auth.store'
import { useSupplierPayments } from '../hooks/useSupplierPayment'
import type { SupplierPayment } from '../model/procurement.types'
import { canReadPayments, canRecordPayment } from '../services/procurementPermission.service'

export function SupplierPaymentListPage() {
  usePageTitle('Supplier Payments')

  const principal = useAuthStore((state) => state.principal)
  const [supplierId, setSupplierId] = useState('')

  const canRead = canReadPayments(principal)

  const query = useSupplierPayments()

  const columns: Array<DataTableColumn<SupplierPayment>> = [
    { key: 'id', header: 'ID', render: (row) => `#${row.id}` },
    { key: 'paymentNumber', header: 'Payment #', render: (row) => row.paymentNumber },
    { key: 'supplierId', header: 'Supplier', render: (row) => `#${row.supplierId}` },
    { key: 'paymentMethod', header: 'Method', render: (row) => row.paymentMethod },
    { key: 'amount', header: 'Amount', render: (row) => row.amount },
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
      <Card title="Filters">
        <div className="field-grid">
          <Input
            label="Supplier ID"
            onChange={(e) => setSupplierId(e.target.value)}
            placeholder="Optional — filter not yet applied"
            value={supplierId}
          />
        </div>
      </Card>

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
