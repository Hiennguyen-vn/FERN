import type { ReactNode } from 'react'
import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  PermissionDeniedInline,
  ReadonlyBanner,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ProductStatusBadge } from '../components/ProductStatusBadge'
import { RecipeVersionStatusBadge } from '../components/RecipeVersionStatusBadge'
import { useProduct } from '../hooks/useProducts'
import { useProductPrices, useAvailability } from '../hooks/usePricing'
import { useRecipes, useRecipeVersions } from '../hooks/useRecipes'
import type { ProductAvailability, ProductPrice, RecipeVersion } from '../model/catalog.types'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import {
  canReadPrices,
  canReadProducts,
  canReadRecipes,
  canWriteProducts,
} from '../services/catalogPermission.service'
import {
  formatCurrencyAmount,
  formatDateRange,
  formatScopeLabel,
  getPriceEffectiveState,
  sortAvailabilityRows,
} from '../services/catalogReadModel.service'

const priceStateBadgeClass: Record<Exclude<ReturnType<typeof getPriceEffectiveState>, 'ALL'>, string> = {
  CURRENT: 'badge badge-success',
  UPCOMING: 'badge badge-warning',
  EXPIRED: 'badge badge-neutral',
}

function SectionCard({ children, description, title }: { children: ReactNode; description?: string; title: string }) {
  return (
    <Card title={title}>
      {description ? <p className="muted-text">{description}</p> : null}
      {children}
    </Card>
  )
}

export function ProductDetailPage() {
  const { productId: productIdParam } = useParams<{ productId: string }>()
  const productId = Number(productIdParam)
  const principal = usePrincipal()
  const { selectedOutletId } = useScopeContext()
  const canViewProduct = canReadProducts(principal)
  const canViewRecipes = canReadRecipes(principal)
  const canViewPrices = canReadPrices(principal)
  const canWriteProduct = canWriteProducts(principal) && principal?.scopeRoots?.system === true

  const productQuery = useProduct(productId, { enabled: canViewProduct && Number.isFinite(productId) })
  const recipesQuery = useRecipes({ enabled: canViewProduct && canViewRecipes })
  const productRecipe = useMemo(
    () => recipesQuery.data?.find((recipe) => recipe.productId === productId) ?? null,
    [productId, recipesQuery.data],
  )
  const recipeVersionsQuery = useRecipeVersions(productRecipe?.id ?? 0, {
    enabled: canViewProduct && canViewRecipes && Boolean(productRecipe?.id),
  })
  const pricesQuery = useProductPrices({ enabled: canViewProduct && canViewPrices })
  const availabilityQuery = useAvailability(
    { productId },
    { enabled: canViewProduct && canViewPrices && Number.isFinite(productId) },
  )

  const product = productQuery.data
  const productPrices = useMemo(
    () => (pricesQuery.data ?? []).filter((price) => price.productId === productId),
    [pricesQuery.data, productId],
  )
  const availabilityRows = useMemo(
    () => sortAvailabilityRows(availabilityQuery.data ?? [], selectedOutletId),
    [availabilityQuery.data, selectedOutletId],
  )

  const currentPrice = useMemo(() => {
    const current = productPrices.find((price) => getPriceEffectiveState(price) === 'CURRENT')
    return current ?? productPrices[0] ?? null
  }, [productPrices])

  const availableOutlets = availabilityRows.filter((row) => row.available).length
  const coverage = availabilityRows.length > 0 ? Math.round((availableOutlets / availabilityRows.length) * 100) : 0

  usePageTitle(product ? `${product.name} Product Profile` : 'Product Profile')

  const recipeVersionColumns = useMemo<Array<DataTableColumn<RecipeVersion>>>(
    () => [
      { key: 'version', header: 'Version', render: (version) => version.versionNo },
      {
        key: 'yield',
        header: 'Yield',
        render: (version) => `${version.yieldQty} ${version.yieldUomCode}`,
      },
      {
        key: 'status',
        header: 'Status',
        render: (version) => <RecipeVersionStatusBadge status={version.status} />,
      },
      {
        key: 'effective',
        header: 'Effective Window',
        render: (version) => formatDateRange(version.effectiveFrom, version.effectiveTo),
      },
    ],
    [],
  )

  const pricingColumns = useMemo<Array<DataTableColumn<ProductPrice>>>(
    () => [
      {
        key: 'scope',
        header: 'Scope',
        render: (price) => formatScopeLabel(price.scopeType, price.scopeId),
      },
      {
        key: 'type',
        header: 'Price Type',
        render: (price) => price.priceType,
      },
      {
        key: 'value',
        header: 'Price',
        render: (price) => formatCurrencyAmount(price.priceValue, price.currencyCode),
      },
      {
        key: 'effective',
        header: 'Effective Window',
        render: (price) => formatDateRange(price.effectiveFrom, price.effectiveTo),
      },
      {
        key: 'state',
        header: 'State',
        render: (price) => {
          const state = getPriceEffectiveState(price)
          return <span className={priceStateBadgeClass[state]}>{state}</span>
        },
      },
    ],
    [],
  )

  const availabilityColumns = useMemo<Array<DataTableColumn<ProductAvailability>>>(
    () => [
      {
        key: 'outlet',
        header: 'Outlet',
        render: (availability) => (
          <div className="cell-stack">
            <strong>Outlet #{availability.outletId}</strong>
            <span className="cell-subtitle">
              {selectedOutletId === availability.outletId ? 'Current outlet context' : 'Catalog scope'}
            </span>
          </div>
        ),
      },
      {
        key: 'available',
        header: 'Availability',
        render: (availability) =>
          availability.available ? (
            <span className="badge badge-success">Available</span>
          ) : (
            <span className="badge badge-danger">Unavailable</span>
          ),
      },
    ],
    [selectedOutletId],
  )

  if (!canViewProduct) {
    return (
      <DashboardLayout
        description="Inspect catalog metadata, pricing, and outlet availability."
        eyebrow="Catalog"
        title="Product Profile"
      >
        <PermissionDeniedInline message="You need catalog.product.read to inspect product detail." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(productId) || productId <= 0) {
    return (
      <DashboardLayout
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Back to product master</Link>
          </Button>
        }
        description="Inspect catalog metadata, pricing, and outlet availability."
        eyebrow="Catalog"
        title="Product Profile"
      >
        <EmptyState description="The current URL does not include a valid product ID." title="Missing product ID" />
      </DashboardLayout>
    )
  }

  if (productQuery.isLoading) {
    return (
      <DashboardLayout
        description="Inspect catalog metadata, pricing, and outlet availability."
        eyebrow="Catalog"
        title="Product Profile"
      >
        <Card title="Loading product profile">
          <p className="muted-text">Loading product metadata, pricing, and outlet availability snapshots...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (productQuery.error) {
    return (
      <DashboardLayout
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Back to product master</Link>
          </Button>
        }
        description="Inspect catalog metadata, pricing, and outlet availability."
        eyebrow="Catalog"
        title="Product Profile"
      >
        <ErrorState
          actionLabel="Retry"
          message={getCatalogErrorMessage(productQuery.error, 'Unable to load this product profile.')}
          onAction={() => void productQuery.refetch()}
          title="Unable to load product profile"
        />
      </DashboardLayout>
    )
  }

  if (!product) {
    return (
      <DashboardLayout
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Back to product master</Link>
          </Button>
        }
        description="Inspect catalog metadata, pricing, and outlet availability."
        eyebrow="Catalog"
        title="Product Profile"
      >
        <EmptyState description="This product could not be found in the current catalog scope." title="Product not found" />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Back to product master</Link>
          </Button>
          {canWriteProduct ? (
            <Button asChild size="sm">
              <Link to={`/catalog/products/${productId}/edit`}>Edit product</Link>
            </Button>
          ) : null}
        </div>
      }
      description="Inspect catalog metadata, pricing, and outlet availability."
      eyebrow="Catalog"
      title="Product Profile"
    >
      {product.status === 'DISCONTINUED' ? (
        <ReadonlyBanner
          icon="event_busy"
          label="Archived entry"
          message="This product is discontinued. All fields remain visible for audit and archive purposes."
          title="Read-only: this product is discontinued"
          tone="danger"
        />
      ) : !canWriteProduct ? (
        <ReadonlyBanner
          label="Browse only"
          message="Product detail is available, but product editing requires catalog.product.write and system scope."
          title="Catalog detail is currently read-first"
          tone="warning"
        />
      ) : null}

      <section className="detail-hero-grid">
        <article className="surface-panel detail-hero-card">
          <div className="detail-hero-body">
            <div className="detail-media-frame">
              {product.imageUrl ? (
                <img alt={product.name} src={product.imageUrl} />
              ) : (
                <AppIcon name="restaurant_menu" size="lg" />
              )}
            </div>
            <div className="detail-summary-copy">
              <span className="meta-chip">Item #{product.code}</span>
              <div className="entity-header-title">
                <h2>{product.name}</h2>
                <ProductStatusBadge status={product.status} />
              </div>
              <p className="muted-text">
                {product.description ?? 'No narrative description has been added for this product.'}
              </p>
              <div className="detail-kpi-grid">
                <div className="detail-kpi">
                  <span className="detail-kpi-label">Current price</span>
                  <span className="detail-kpi-value">
                    {currentPrice ? formatCurrencyAmount(currentPrice.priceValue, currentPrice.currencyCode) : '—'}
                  </span>
                </div>
                <div className="detail-kpi">
                  <span className="detail-kpi-label">Price entries</span>
                  <span className="detail-kpi-value">{productPrices.length}</span>
                </div>
                <div className="detail-kpi">
                  <span className="detail-kpi-label">Availability</span>
                  <span className="detail-kpi-value">{availabilityRows.length > 0 ? `${coverage}%` : '—'}</span>
                </div>
              </div>
            </div>
          </div>
        </article>

        <aside className="detail-side-stack">
          <Card className="detail-side-card" title="Metadata Architecture">
            <div className="detail-side-list">
              <div className="detail-side-row">
                <span>Category</span>
                <strong>{product.categoryCode ?? 'Uncategorized'}</strong>
              </div>
              <div className="detail-side-row">
                <span>Selected outlet</span>
                <strong>{selectedOutletId ? `Outlet #${selectedOutletId}` : 'All outlets'}</strong>
              </div>
              <div className="detail-side-row">
                <span>Recipe linkage</span>
                <strong>{productRecipe ? productRecipe.recipeCode : 'No linked recipe'}</strong>
              </div>
              <div className="detail-side-row">
                <span>Pricing access</span>
                <strong>{canViewPrices ? `${productPrices.length} entries` : 'Restricted'}</strong>
              </div>
            </div>
          </Card>

          <Card className="detail-side-card" title="Operational State">
            <div className="detail-side-list">
              <div className="detail-side-row">
                <span>Lifecycle</span>
                <strong>{product.status.replace(/_/g, ' ')}</strong>
              </div>
              <div className="detail-side-row">
                <span>Availability rows</span>
                <strong>{availabilityRows.length}</strong>
              </div>
              <div className="detail-side-row">
                <span>Currently available</span>
                <strong>{availableOutlets}</strong>
              </div>
            </div>
          </Card>
        </aside>
      </section>

      <SectionCard description="Recipe detail is shown only when the current principal can read catalog recipes." title="Recipe Snapshot">
        {!canViewRecipes ? (
          <PermissionDeniedInline message="You do not have catalog.recipe.read, so recipe detail is hidden on this product." />
        ) : recipesQuery.error || recipeVersionsQuery.error ? (
          <ErrorState
            actionLabel="Retry"
            message={getCatalogErrorMessage(
              recipesQuery.error ?? recipeVersionsQuery.error,
              'Unable to load recipe detail for this product.',
            )}
            onAction={() => {
              void recipesQuery.refetch()
              if (productRecipe?.id) {
                void recipeVersionsQuery.refetch()
              }
            }}
            title="Unable to load recipe snapshot"
          />
        ) : recipesQuery.isLoading || recipeVersionsQuery.isLoading ? (
          <Card title="Loading recipe detail">
            <p className="muted-text">Loading recipe metadata and recipe versions...</p>
          </Card>
        ) : !productRecipe ? (
          <EmptyState description="No recipe read model has been linked to this product yet." title="No recipe linked" />
        ) : (
          <div className="page-stack">
            <div className="key-value-list">
              <div className="key-value-row">
                <span>Recipe code</span>
                <strong>{productRecipe.recipeCode}</strong>
              </div>
              <div className="key-value-row">
                <span>Description</span>
                <strong>{productRecipe.description ?? 'No recipe description'}</strong>
              </div>
            </div>
            <DataTable
              columns={recipeVersionColumns}
              emptyDescription="Recipe metadata exists but no version snapshot is currently available."
              emptyTitle="No recipe versions"
              rowKey={(version) => version.id}
              rows={recipeVersionsQuery.data ?? []}
            />
          </div>
        )}
      </SectionCard>

      <SectionCard description="Pricing snapshot is assembled from the product price read model." title="Pricing Snapshot">
        {!canViewPrices ? (
          <PermissionDeniedInline message="You do not have catalog.price.read, so pricing detail is hidden on this product." />
        ) : (
          <DataTable
            columns={pricingColumns}
            emptyDescription="This product does not have any effective price entries yet."
            emptyTitle="No pricing entries"
            error={pricesQuery.error ? getCatalogErrorMessage(pricesQuery.error, 'Unable to load pricing detail.') : null}
            loading={pricesQuery.isLoading}
            loadingDescription="Loading product prices..."
            loadingTitle="Loading pricing snapshot"
            onRetry={() => void pricesQuery.refetch()}
            rowKey={(price) => price.id}
            rows={productPrices}
          />
        )}
      </SectionCard>

      <SectionCard description="Outlet availability rows for the current product." title="Outlet Availability">
        {!canViewPrices ? (
          <PermissionDeniedInline message="Availability uses the same read contract as pricing, so this section is also restricted." />
        ) : (
          <DataTable
            columns={availabilityColumns}
            emptyDescription="No outlet availability rows are available for this product."
            emptyTitle="No availability rows"
            error={availabilityQuery.error ? getCatalogErrorMessage(availabilityQuery.error, 'Unable to load outlet availability.') : null}
            loading={availabilityQuery.isLoading}
            loadingDescription="Loading outlet availability..."
            loadingTitle="Loading availability"
            onRetry={() => void availabilityQuery.refetch()}
            rowKey={(availability) => `${availability.productId}-${availability.outletId}`}
            rows={availabilityRows}
          />
        )}
      </SectionCard>
    </DashboardLayout>
  )
}
