import { useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Badge, Card, DataTable, EmptyState, ErrorState, Input, Pagination, ReadonlyBanner } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useStockBalances } from '../hooks/useStockBalances'
import type { StockBalance } from '../model/inventory.types'

function toOptionalNumber(value: string): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

export function StockOverviewPage() {
  usePageTitle('Stock Overview')

  const { selectedOutletId } = useScopeContext()
  const [ingredientId, setIngredientId] = useState('')
  const [page, setPage] = useState(0)
  const size = 20

  const query = useStockBalances(
    selectedOutletId
      ? {
          outletId: selectedOutletId,
          ingredientId: toOptionalNumber(ingredientId),
          page,
          size,
        }
      : null,
  )

  const columns: Array<DataTableColumn<StockBalance>> = [
    { key: 'ingredientId', header: 'Ingredient', render: (row) => `#${row.ingredientId}` },
    { key: 'qtyOnHand', header: 'On hand', render: (row) => row.qtyOnHand },
    { key: 'qtyReserved', header: 'Reserved', render: (row) => row.qtyReserved },
    {
      key: 'qtyAvailable',
      header: 'Available',
      render: (row) => (
        <Badge tone={Number(row.qtyAvailable) > 0 ? 'success' : 'warning'}>{row.qtyAvailable}</Badge>
      ),
    },
    { key: 'unitCost', header: 'Unit cost', render: (row) => row.unitCost },
    { key: 'lastCountDate', header: 'Last count', render: (row) => row.lastCountDate ?? 'N/A' },
  ]

  return (
    <DashboardLayout
      description="Read live stock balances for the currently selected outlet."
      title="Stock Overview"
    >
      {!selectedOutletId ? (
        <>
          <ReadonlyBanner message="Select an outlet in the app shell to load stock balances." />
          <EmptyState
            description="Stock Overview chỉ hiển thị dữ liệu khi outlet context đã được chọn rõ ràng trong app shell."
            title="Outlet context required"
          />
        </>
      ) : null}
      {selectedOutletId && query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load stock balances'}
          onAction={() => void query.refetch()}
          title="Không thể tải stock balances"
        />
      ) : null}
      {selectedOutletId ? (
        <>
      <Card title="Filters">
        <div className="field-grid">
          <Input
            label="Outlet ID"
            readOnly
            value={selectedOutletId ?? ''}
          />
          <Input
            label="Ingredient ID"
            onChange={(event) => {
              setIngredientId(event.target.value)
              setPage(0)
            }}
            placeholder="Optional ingredient id"
            value={ingredientId}
          />
        </div>
      </Card>
      {!query.error ? (
        <>
          <DataTable
            columns={columns}
            emptyDescription={
              ingredientId
                ? 'Không có stock balance nào khớp ingredient đang lọc tại outlet hiện tại.'
                : 'Outlet hiện tại chưa có stock balance nào để hiển thị.'
            }
            emptyTitle="No stock balances"
            loading={query.isLoading}
            loadingDescription="Loading live stock balances for the selected outlet..."
            loadingTitle="Loading stock balances"
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
