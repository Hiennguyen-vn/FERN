import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { Badge, Button, DataTable, EmptyState, ErrorState, PermissionDeniedInline, ReadonlyBanner, Select } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { formatDate, formatDateTime } from '@shared/formatters/formatDateTime'
import { useStockCountSessionList } from '../hooks/useStockCountSessionList'
import type { StockCountSessionStatus, StockCountSessionSummary } from '../model/inventory.types'
import {
  canCreateStockCountSessions,
  canReadStockBalances,
} from '../services/inventoryPermission.service'

function statusTone(status: StockCountSessionStatus): 'neutral' | 'warning' | 'success' | 'danger' {
  switch (status) {
    case 'DRAFT':
      return 'neutral'
    case 'COUNTING':
      return 'warning'
    case 'POSTED':
      return 'success'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'neutral'
  }
}

const STATUS_FILTER_OPTIONS = [
  { label: 'All statuses', value: '' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Counting', value: 'COUNTING' },
  { label: 'Posted', value: 'POSTED' },
  { label: 'Cancelled', value: 'CANCELLED' },
]

export function StockCountSessionsPage() {
  usePageTitle('Stock count sessions')
  const principal = usePrincipal()
  const { selectedOutletId, outletIds, setSelectedOutletId } = useScopeContext()
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const size = 20

  const canRead = canReadStockBalances(principal)
  const canCreate = canCreateStockCountSessions(principal)

  const filters = useMemo(
    () =>
      selectedOutletId
        ? {
            outletId: selectedOutletId,
            ...(statusFilter ? { status: statusFilter } : {}),
            page,
            size,
          }
        : null,
    [selectedOutletId, statusFilter, page, size],
  )

  const query = useStockCountSessionList(filters)
  const rows = query.data?.items ?? []

  const columns: Array<DataTableColumn<StockCountSessionSummary>> = [
    {
      key: 'id',
      header: 'Session',
      render: (row) => (
        <Link to={`/inventory/stock-count-sessions/${row.id}`}>#{row.id}</Link>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge>,
    },
    { key: 'countDate', header: 'Count date', render: (row) => formatDate(row.countDate) },
    { key: 'startedAt', header: 'Started', render: (row) => formatDateTime(row.startedAt) },
    { key: 'postedAt', header: 'Posted', render: (row) => formatDateTime(row.postedAt) },
  ]

  if (!canRead) {
    return (
      <DashboardLayout
        description="Outlet stock count history (read-only list)."
        eyebrow="Inventory / Outlet Control"
        title="Stock count sessions"
      >
        <PermissionDeniedInline message="You need `inventory.balance.read` to view stock count sessions." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      description="Sessions for the selected outlet, newest first."
      eyebrow="Inventory / Outlet Control"
      title="Stock count sessions"
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/inventory/stock-balances">
              <AppIcon name="inventory_2" size="sm" />
              Stock overview
            </Link>
          </Button>
          {canCreate ? (
            <Button asChild size="sm">
              <Link to="/inventory/stock-count-sessions/new">
                <AppIcon name="add" size="sm" />
                New session
              </Link>
            </Button>
          ) : null}
        </div>
      }
    >
      {!selectedOutletId ? (
        <>
          <ReadonlyBanner message="Select an outlet to load stock count history." />
          <EmptyState title="No outlet selected" description="Choose an outlet from the shell or below.">
            {outletIds.length > 0 ? (
              <div style={{ marginTop: '1rem', maxWidth: '320px' }}>
                <Select
                  label="Choose outlet"
                  onChange={(event) => {
                    if (event.target.value) {
                      setSelectedOutletId(Number(event.target.value))
                    }
                  }}
                  options={outletIds.map((id) => ({ label: `Outlet #${id}`, value: String(id) }))}
                  placeholder="-- Choose outlet --"
                  value={selectedOutletId ? String(selectedOutletId) : ''}
                />
              </div>
            ) : (
              <p className="muted-text" style={{ marginTop: '0.5rem' }}>
                This account has not been assigned an outlet scope.
              </p>
            )}
          </EmptyState>
        </>
      ) : null}

      {selectedOutletId && query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load sessions'}
          onAction={() => void query.refetch()}
          title="Unable to load stock count sessions"
        />
      ) : null}

      {selectedOutletId && !query.error ? (
        <>
          <section className="workspace-filter-bar" aria-label="Stock count filters">
            <Select
              label="Status"
              onChange={(event) => {
                setStatusFilter(event.target.value)
                setPage(0)
              }}
              options={STATUS_FILTER_OPTIONS}
              value={statusFilter}
            />
            <div className="workspace-inline-actions">
              <div className="workspace-inline-pill">
                <AppIcon name="storefront" size="sm" />
                Outlet #{selectedOutletId}
              </div>
              <div className="workspace-inline-pill">
                <AppIcon name="view_list" size="sm" />
                Page {page + 1}
              </div>
            </div>
          </section>

          <DataTable
            canNext={Boolean(query.data?.hasMore)}
            canPrevious={page > 0}
            columns={columns}
            currentPage={page}
            emptyDescription="No stock count sessions match the current filters."
            emptyTitle="No sessions"
            loading={query.isLoading}
            loadingDescription="Loading stock count sessions…"
            loadingTitle="Loading"
            onNext={() => setPage((p) => p + 1)}
            onPrevious={() => setPage((p) => Math.max(0, p - 1))}
            rows={rows}
          />
        </>
      ) : null}
    </DashboardLayout>
  )
}
