import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Badge, Button, Card, DataTable, EmptyState, ErrorState, Input, PermissionDeniedInline, ReadonlyBanner, Select } from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useStockBalances } from '../hooks/useStockBalances'
import type { StockBalance } from '../model/inventory.types'
import {
  canCreateStockAdjustments,
  canCreateStockCountSessions,
  canCreateWasteRecords,
  canReadStockBalances,
} from '../services/inventoryPermission.service'
import { toOptionalNumber } from '@shared/validators/parseInput'
import { useIngredients } from '@modules/catalog/hooks/useIngredients'


export function StockOverviewPage() {
  usePageTitle('Stock Overview')

  const principal = usePrincipal()
  const { selectedOutletId, outletIds, setSelectedOutletId } = useScopeContext()
  const [ingredientId, setIngredientId] = useState('')
  const [page, setPage] = useState(0)
  const size = 20

  const hasPermission = canReadStockBalances(principal)
  const canCreateAdjustment = canCreateStockAdjustments(principal)
  const canCreateWaste = canCreateWasteRecords(principal)
  const canCreateStockCount = canCreateStockCountSessions(principal)

  // Fetch all ingredients to resolve IDs → names in the table
  const ingredientsQuery = useIngredients()
  const ingredientNameMap = useMemo(() => {
    const map = new Map<number, string>()
    for (const ing of ingredientsQuery.data ?? []) {
      map.set(ing.id, ing.name)
    }
    return map
  }, [ingredientsQuery.data])

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
  const balances = query.data?.items ?? []
  const lowStockCount = balances.filter((row) => Number(row.qtyAvailable) > 0 && Number(row.qtyAvailable) <= 5).length
  const outOfStockCount = balances.filter((row) => Number(row.qtyAvailable) <= 0).length
  const recentlyCounted = balances.filter((row) => row.lastCountDate).length

  const columns: Array<DataTableColumn<StockBalance>> = [
    {
      key: 'ingredientId',
      header: 'Ingredient',
      render: (row) => ingredientNameMap.get(row.ingredientId) ?? `#${row.ingredientId}`,
    },
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

  if (!hasPermission) {
    return (
      <DashboardLayout description="Read live stock balances for the currently selected outlet." eyebrow="Inventory / Outlet Control" title="Stock Overview">
        <PermissionDeniedInline message="You need `inventory.balance.read` to open the stock overview." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      description="Read live stock balances for the currently selected outlet."
      eyebrow="Inventory / Outlet Control"
      title="Stock Overview"
      actions={
        <div className="form-actions align-start">
          {canCreateAdjustment ? (
            <Button asChild size="sm" variant="secondary">
              <Link to="/inventory/stock-adjustments/new">
                <AppIcon name="edit_note" size="sm" />
                Quick adjustment
              </Link>
            </Button>
          ) : null}
          {canCreateWaste ? (
            <Button asChild size="sm" variant="secondary">
              <Link to="/inventory/waste-records/new">
                <AppIcon name="delete_sweep" size="sm" />
                Waste record
              </Link>
            </Button>
          ) : null}
          {canCreateStockCount ? (
            <Button asChild size="sm">
              <Link to="/inventory/stock-count-sessions/new">
                <AppIcon name="inventory" size="sm" />
                Stock count
              </Link>
            </Button>
          ) : null}
        </div>
      }
    >
      {!selectedOutletId ? (
        <>
          <ReadonlyBanner message="Select an outlet to inspect live inventory balances. Use the outlet switcher below or in the shell." />
          <EmptyState
            description="Stock Overview renders only after a single outlet context has been chosen in the app shell."
            title="No outlet selected"
          >
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
                This account has not been assigned an outlet scope. Contact a system administrator to continue.
              </p>
            )}
          </EmptyState>
        </>
      ) : null}
      {selectedOutletId && query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load stock balances'}
          onAction={() => void query.refetch()}
          title="Unable to load stock balances"
        />
      ) : null}
      {selectedOutletId ? (
        <>
          {!query.error ? (
            <>
              <section className="workspace-stats-grid" aria-label="Stock summary">
                <article className="workspace-stat-card">
                  <div className="workspace-stat-topline">
                    <span className="workspace-stat-icon">
                      <AppIcon name="inventory_2" size="sm" />
                    </span>
                    <span className="workspace-stat-badge success">Visible</span>
                  </div>
                  <div>
                    <p className="workspace-stat-label">Ingredients on current page</p>
                    <strong className="workspace-stat-value">{balances.length}</strong>
                  </div>
                </article>
                <article className="workspace-stat-card warning">
                  <div className="workspace-stat-topline">
                    <span className="workspace-stat-icon">
                      <AppIcon name="warning" size="sm" />
                    </span>
                    <span className="workspace-stat-badge warning">Watch</span>
                  </div>
                  <div>
                    <p className="workspace-stat-label">Low stock lines</p>
                    <strong className="workspace-stat-value">{lowStockCount}</strong>
                  </div>
                </article>
                <article className="workspace-stat-card danger">
                  <div className="workspace-stat-topline">
                    <span className="workspace-stat-icon">
                      <AppIcon name="error" size="sm" />
                    </span>
                    <span className="workspace-stat-badge danger">Critical</span>
                  </div>
                  <div>
                    <p className="workspace-stat-label">Out of stock lines</p>
                    <strong className="workspace-stat-value">{outOfStockCount}</strong>
                  </div>
                </article>
                <article className="workspace-stat-card">
                  <div className="workspace-stat-topline">
                    <span className="workspace-stat-icon">
                      <AppIcon name="history" size="sm" />
                    </span>
                    <span className="workspace-stat-badge success">Today</span>
                  </div>
                  <div>
                    <p className="workspace-stat-label">Recently counted</p>
                    <strong className="workspace-stat-value">{recentlyCounted}</strong>
                  </div>
                </article>
              </section>

              <section className="workspace-filter-bar" aria-label="Stock filters">
                <label className="workspace-inline-search" htmlFor="stock-ingredient-filter">
                  <AppIcon name="search" size="sm" />
                  <input
                    className="workspace-inline-input"
                    id="stock-ingredient-filter"
                    onChange={(event) => {
                      setIngredientId(event.target.value)
                      setPage(0)
                    }}
                    placeholder="Search by ingredient ID..."
                    value={ingredientId}
                  />
                </label>
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

              <Card title="Outlet context">
                <div className="field-grid">
                  <Input label="Outlet ID" readOnly value={selectedOutletId ?? ''} />
                  <Input label="Ingredient filter" readOnly value={ingredientId || 'None'} />
                </div>
              </Card>

              <DataTable
                canNext={Boolean(query.data?.hasMore)}
                canPrevious={page > 0}
                columns={columns}
                currentPage={page}
                emptyDescription={
                  ingredientId
                    ? 'No stock balances match the filtered ingredient at the current outlet.'
                    : 'The current outlet has no stock balances to display.'
                }
                emptyTitle="No stock balances"
                loading={query.isLoading}
                loadingDescription="Loading live stock balances for the selected outlet..."
                loadingTitle="Loading stock balances"
                onNext={() => setPage((value) => value + 1)}
                onPrevious={() => setPage((value) => Math.max(0, value - 1))}
                rows={balances}
              />
            </>
          ) : null}
        </>
      ) : null}
    </DashboardLayout>
  )
}
