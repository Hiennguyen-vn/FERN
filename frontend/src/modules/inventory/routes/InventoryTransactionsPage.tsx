import { useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Badge, Card, DataTable, EmptyState, ErrorState, Input, Pagination, PermissionDeniedInline, ReadonlyBanner } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAuthStore } from '@core/auth/auth.store'
import { useInventoryTransactions } from '../hooks/useInventoryTransactions'
import type { InventoryTransaction } from '../model/inventory.types'
import { buildInventoryTransactionSummary } from '../services/inventoryWorkflow.service'
import { canReadInventoryLedger } from '../services/inventoryPermission.service'

function toOptionalNumber(value: string): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

export function InventoryTransactionsPage() {
  usePageTitle('Inventory Transactions')

  const principal = useAuthStore((state) => state.principal)
  const { selectedOutletId } = useScopeContext()

  if (!canReadInventoryLedger(principal)) {
    return <PermissionDeniedInline message="Bạn cần quyền inventory.ledger.read để xem giao dịch kho." />
  }
  const [filters, setFilters] = useState({
    ingredientId: '',
    txnType: '',
    from: '',
    to: '',
  })
  const [page, setPage] = useState(0)
  const size = 20

  const query = useInventoryTransactions(
    selectedOutletId
      ? {
          outletId: selectedOutletId,
          ingredientId: toOptionalNumber(filters.ingredientId),
          txnType: filters.txnType || undefined,
          from: filters.from || undefined,
          to: filters.to || undefined,
          page,
          size,
        }
      : null,
  )

  const columns: Array<DataTableColumn<InventoryTransaction>> = [
    { key: 'id', header: 'Txn ID', render: (row) => `#${row.id}` },
    { key: 'businessDate', header: 'Business date', render: (row) => row.businessDate },
    { key: 'txnType', header: 'Type', render: (row) => <Badge>{row.txnType}</Badge> },
    { key: 'qtyChange', header: 'Qty change', render: (row) => row.qtyChange },
    { key: 'unitCost', header: 'Unit cost', render: (row) => row.unitCost },
    {
      key: 'source',
      header: 'Source',
      render: (row) => buildInventoryTransactionSummary(row.txnType, row.sourceReferenceType),
    },
    { key: 'txnTime', header: 'Txn time', render: (row) => new Date(row.txnTime).toLocaleString() },
  ]

  return (
    <DashboardLayout
      description="Inspect inventory movement history for the selected outlet."
      title="Inventory Transactions"
    >
      {!selectedOutletId ? (
        <>
          <ReadonlyBanner message="Select an outlet in the app shell to load inventory transactions." />
          <EmptyState
            description="Inventory Transactions cần outlet context để tránh hiển thị movement history ngoài phạm vi."
            title="Outlet context required"
          />
        </>
      ) : null}
      {selectedOutletId && query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load inventory transactions'}
          onAction={() => void query.refetch()}
          title="Không thể tải inventory transactions"
        />
      ) : null}
      {selectedOutletId ? (
        <>
      <Card title="Filters">
        <div className="field-grid">
          <Input label="Outlet ID" readOnly value={selectedOutletId ?? ''} />
          <Input
            label="Ingredient ID"
            onChange={(event) => {
              setFilters((current) => ({ ...current, ingredientId: event.target.value }))
              setPage(0)
            }}
            value={filters.ingredientId}
          />
          <Input
            label="Txn Type"
            onChange={(event) => {
              setFilters((current) => ({ ...current, txnType: event.target.value }))
              setPage(0)
            }}
            placeholder="SALE_CONSUME"
            value={filters.txnType}
          />
          <Input
            label="From"
            onChange={(event) => {
              setFilters((current) => ({ ...current, from: event.target.value }))
              setPage(0)
            }}
            type="date"
            value={filters.from}
          />
          <Input
            label="To"
            onChange={(event) => {
              setFilters((current) => ({ ...current, to: event.target.value }))
              setPage(0)
            }}
            type="date"
            value={filters.to}
          />
        </div>
      </Card>
      {!query.error ? (
        <>
          <DataTable
            columns={columns}
            emptyDescription={
              filters.ingredientId || filters.txnType || filters.from || filters.to
                ? 'Không có transaction nào khớp bộ lọc hiện tại.'
                : 'Outlet hiện tại chưa có inventory transaction nào để hiển thị.'
            }
            emptyTitle="No inventory transactions"
            loading={query.isLoading}
            loadingDescription="Loading transaction history for the selected outlet..."
            loadingTitle="Loading inventory transactions"
            rows={query.data?.items ?? []}
          />
          <Pagination
            canNext={Boolean(query.data?.hasMore)}
            canPrevious={page > 0}
            currentPage={page}
            onNext={() => setPage((value) => value + 1)}
            onPrevious={() => setPage((value) => Math.max(0, value - 1))}
          />
        </>
      ) : null}
        </>
      ) : null}
    </DashboardLayout>
  )
}
