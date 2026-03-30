import { DataTable, StatusBadge } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { formatDateTime, formatMoney } from '@shared/formatters'
import type { SalePayment } from '../model/pos.types'

interface PosPaymentHistoryTableProps {
  currencyCode: string
  payments: SalePayment[]
}

export function PosPaymentHistoryTable({ currencyCode, payments }: PosPaymentHistoryTableProps) {
  const columns: Array<DataTableColumn<SalePayment>> = [
    { key: 'id', header: 'Payment ID', render: (row) => `#${row.id}` },
    { key: 'paymentMethod', header: 'Method', render: (row) => row.paymentMethod },
    { key: 'amount', header: 'Amount', render: (row) => formatMoney(row.amount, currencyCode) },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    { key: 'paymentTime', header: 'Time', render: (row) => formatDateTime(row.paymentTime) },
    { key: 'transactionRef', header: 'Reference', render: (row) => row.transactionRef ?? 'N/A' },
  ]

  return <DataTable columns={columns} emptyDescription="No payments recorded yet." emptyTitle="No payments" rows={payments} />
}
