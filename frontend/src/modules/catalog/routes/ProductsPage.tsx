import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import {
  Badge,
  Button,
  DataTable,
  PermissionDeniedInline,
  ReadonlyBanner,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ProductStatusBadge, STATUS_LABELS } from '../components/ProductStatusBadge'
import { useIngredients } from '../hooks/useIngredients'
import { useProductCategories, useProducts } from '../hooks/useProducts'
import type { Product, ProductStatus } from '../model/catalog.types'
import {
  canReadProducts,
  canReadIngredients,
  canWriteProducts,
} from '../services/catalogPermission.service'
import { matchesSearch } from '../services/catalogReadModel.service'

const statusOptions: SelectOption[] = [
  { label: 'All statuses', value: 'ALL' },
  { label: STATUS_LABELS.DRAFT, value: 'DRAFT' },
  { label: STATUS_LABELS.ACTIVE, value: 'ACTIVE' },
  { label: STATUS_LABELS.INACTIVE, value: 'INACTIVE' },
  { label: STATUS_LABELS.DISCONTINUED, value: 'DISCONTINUED' },
]

export function ProductsPage() {
  usePageTitle('Product Master Catalog')

  const navigate = useNavigate()
  const principal = usePrincipal()
  const { selectedOutletId } = useScopeContext()
  const canViewProducts = canReadProducts(principal)
  const canViewIngredients = canReadIngredients(principal)
  const canWrite = canWriteProducts(principal) && principal?.scopeRoots?.system === true
  const { data: products = [], error, isLoading, refetch } = useProducts({ enabled: canViewProducts })
  const { data: categories = [], error: categoriesError } = useProductCategories({ enabled: canViewProducts })
  const { data: ingredients = [] } = useIngredients({ enabled: canViewIngredients })
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<ProductStatus | 'ALL'>('ALL')
  const [categoryFilter, setCategoryFilter] = useState('ALL')

  const categoryOptions = useMemo<SelectOption[]>(
    () => [
      { label: 'All categories', value: 'ALL' },
      ...categories.map((category) => ({
        label: `${category.name} (${category.code})`,
        value: category.code,
      })),
    ],
    [categories],
  )

  const categoryLookup = useMemo(
    () => new Map(categories.map((category) => [category.code, category] as const)),
    [categories],
  )

  const filteredRows = useMemo(() => {
    return products.filter((product) => {
      const matchesStatus = statusFilter === 'ALL' || product.status === statusFilter
      const matchesCategory = categoryFilter === 'ALL' || product.categoryCode === categoryFilter
      const matchesQuery = matchesSearch(
        [product.code, product.name, product.description, product.categoryCode],
        search,
      )

      return matchesStatus && matchesCategory && matchesQuery
    })
  }, [categoryFilter, products, search, statusFilter])

  const columns = useMemo<Array<DataTableColumn<Product>>>(
    () => [
      {
        key: 'code',
        header: 'Code',
        render: (product) => <span className="cell-kicker">{product.code}</span>,
      },
      {
        key: 'product',
        header: 'Product Name',
        render: (product) => (
          <div className="table-identity">
            <span className="table-avatar">
              <AppIcon filled name="restaurant_menu" size="sm" />
            </span>
            <div className="cell-stack">
              <strong>{product.name}</strong>
              <span className="cell-subtitle">{product.description ?? 'No description has been added yet.'}</span>
            </div>
          </div>
        ),
      },
      {
        key: 'type',
        header: 'Type',
        render: () => <Badge>Product</Badge>,
      },
      {
        key: 'category',
        header: 'Category',
        render: (product) =>
          categoryLookup.get(product.categoryCode ?? '')?.name ?? product.categoryCode ?? 'Uncategorized',
      },
      {
        key: 'lifecycle',
        header: 'Lifecycle',
        render: (product) => <ProductStatusBadge status={product.status} />,
      },
      {
        key: 'outletContext',
        header: 'Outlet Context',
        render: (product) => (
          <div className="cell-stack">
            <strong>{selectedOutletId ? `Outlet #${selectedOutletId}` : 'All outlets'}</strong>
            <span className="cell-subtitle">
              {product.status === 'ACTIVE' ? 'Operational review' : 'Catalog governance'}
            </span>
          </div>
        ),
      },
      {
        key: 'detailState',
        header: 'Detail',
        render: (product) => (
          <span className="health-line">
            <span className={`health-dot ${product.status === 'DISCONTINUED' ? 'danger' : product.status === 'DRAFT' ? 'warning' : 'success'}`} />
            {product.status === 'DISCONTINUED'
              ? 'Read-only'
              : product.status === 'DRAFT'
                ? 'Needs review'
                : 'Live'}
          </span>
        ),
      },
    ],
    [categoryLookup, selectedOutletId],
  )

  const activeProducts = products.filter((product) => product.status === 'ACTIVE').length
  const discontinuedProducts = products.filter((product) => product.status === 'DISCONTINUED').length
  const activeIngredients = ingredients.filter((ingredient) => ingredient.status === 'ACTIVE').length
  const hasFilters = Boolean(search.trim()) || statusFilter !== 'ALL' || categoryFilter !== 'ALL'

  if (!canViewProducts) {
    return (
      <DashboardLayout
        description="Operational master list for active, draft, and discontinued products."
        eyebrow="Catalog"
        title="Product Master Catalog"
      >
        <PermissionDeniedInline message="You need catalog.product.read to open the product master catalog." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <div className="form-actions align-start">
          <Button disabled size="sm" variant="secondary">
            Bulk import
          </Button>
          {canWrite ? (
            <Button asChild size="sm">
              <Link to="/catalog/products/new">New product</Link>
            </Button>
          ) : null}
        </div>
      }
      description="Operational master list for active, draft, and discontinued products."
      eyebrow="Catalog"
      title="Product Master Catalog"
    >
      {!canWrite ? (
        <ReadonlyBanner
          label="Read-first mode"
          message="Product write flows remain restricted to principals with catalog.product.write and system scope."
          title="Catalog browse mode"
        />
      ) : null}

      <section className="workspace-stats-grid">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="inventory_2" />
            </span>
            <span className="workspace-stat-badge success">Live</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Total items</span>
            <strong className="workspace-stat-value">{products.length.toLocaleString('en-US')}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="done_all" />
            </span>
            <span className="workspace-stat-badge success">Published</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Active products</span>
            <strong className="workspace-stat-value">{activeProducts.toLocaleString('en-US')}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="nutrition" />
            </span>
            <span className="workspace-stat-badge success">Reference</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Active ingredients</span>
            <strong className="workspace-stat-value">{activeIngredients.toLocaleString('en-US')}</strong>
          </div>
        </article>
        <article className="workspace-stat-card danger">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="warning" />
            </span>
            <span className="workspace-stat-badge danger">Attention</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Discontinued</span>
            <strong className="workspace-stat-value">{discontinuedProducts.toLocaleString('en-US')}</strong>
          </div>
        </article>
      </section>

      <section className="workspace-filter-bar" aria-label="Product catalog filters">
        <div className="workspace-inline-search">
          <AppIcon name="search" size="sm" />
          <input
            className="workspace-inline-input"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by code or product name..."
            value={search}
          />
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Status</span>
          <select
            className="workspace-inline-select"
            onChange={(event) => setStatusFilter(event.target.value as ProductStatus | 'ALL')}
            value={statusFilter}
          >
            {statusOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Category</span>
          <select
            className="workspace-inline-select"
            onChange={(event) => setCategoryFilter(event.target.value)}
            value={categoryFilter}
          >
            {categoryOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="workspace-filter-field">
          <span className="eyebrow">Outlet availability</span>
          <span className="workspace-inline-pill">
            <AppIcon name="storefront" size="sm" />
            {selectedOutletId ? `Outlet #${selectedOutletId}` : 'All outlets'}
          </span>
        </div>
      </section>

      {categoriesError ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Category metadata could not be loaded. The table is still available with raw category codes.
        </div>
      ) : null}

      <section className="surface-panel">
        <div className="page-header">
          <div>
            <h2 className="card-title">Product Master List</h2>
            <p className="muted-text">
              {hasFilters
                ? `${filteredRows.length.toLocaleString('en-US')} rows match the current filters.`
                : `${products.length.toLocaleString('en-US')} product records are available in the current catalog slice.`}
            </p>
          </div>
        </div>

        <DataTable
          columns={columns}
          emptyDescription={
            hasFilters
              ? 'Try broadening the search text, status, or category filters.'
              : 'No product records are available in the catalog yet.'
          }
          emptyTitle={hasFilters ? 'No products match the current filters' : 'Catalog is empty'}
          error={error ? (error instanceof Error ? error.message : 'Unable to load the product catalog.') : null}
          loading={isLoading}
          loadingDescription="Loading product records and category metadata..."
          loadingTitle="Loading product catalog"
          onRetry={() => void refetch()}
          onRowClick={(product) => navigate(`/catalog/products/${product.id}`)}
          rowKey={(product) => product.id}
          rows={filteredRows}
        />
      </section>
    </DashboardLayout>
  )
}
