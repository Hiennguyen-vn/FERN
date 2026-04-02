import type { ReactNode } from 'react'
import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  EntityHeader,
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
  const availabilityQuery = useAvailability({ productId }, { enabled: canViewProduct && canViewPrices && Number.isFinite(productId) })

  const product = productQuery.data
  const productPrices = useMemo(
    () => (pricesQuery.data ?? []).filter((price) => price.productId === productId),
    [pricesQuery.data, productId],
  )
  const availabilityRows = useMemo(
    () => sortAvailabilityRows(availabilityQuery.data ?? [], selectedOutletId),
    [availabilityQuery.data, selectedOutletId],
  )

  usePageTitle(product ? `${product.name} — Catalog` : 'Chi tiết sản phẩm — Catalog')

  const recipeVersionColumns = useMemo<Array<DataTableColumn<RecipeVersion>>>(
    () => [
      { key: 'version', header: 'Phiên bản', render: (version) => version.versionNo },
      {
        key: 'yield',
        header: 'Yield',
        render: (version) => `${version.yieldQty} ${version.yieldUomCode}`,
      },
      {
        key: 'status',
        header: 'Trạng thái',
        render: (version) => <RecipeVersionStatusBadge status={version.status} />,
      },
      {
        key: 'effective',
        header: 'Hiệu lực',
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
        header: 'Loại giá',
        render: (price) => price.priceType,
      },
      {
        key: 'value',
        header: 'Giá',
        render: (price) => formatCurrencyAmount(price.priceValue, price.currencyCode),
      },
      {
        key: 'effective',
        header: 'Hiệu lực',
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
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span>Outlet #{availability.outletId}</span>
            {selectedOutletId === availability.outletId ? <span className="badge badge-warning">Current outlet</span> : null}
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
      <DashboardLayout title="Chi tiết sản phẩm" description="Inspect sản phẩm trong Catalog">
        <PermissionDeniedInline message="Bạn cần quyền catalog.product.read để xem chi tiết sản phẩm." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(productId) || productId <= 0) {
    return (
      <DashboardLayout
        title="Chi tiết sản phẩm"
        description="Inspect sản phẩm trong Catalog"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Quay lại danh sách</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa mã sản phẩm hợp lệ." title="Thiếu productId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (productQuery.isLoading) {
    return (
      <DashboardLayout title="Chi tiết sản phẩm" description="Inspect sản phẩm trong Catalog">
        <Card title="Đang tải sản phẩm">
          <p className="muted-text">Đang tải product overview, pricing và availability snapshot...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (productQuery.error) {
    return (
      <DashboardLayout
        title="Chi tiết sản phẩm"
        description="Inspect sản phẩm trong Catalog"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Quay lại danh sách</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Tải lại"
          message={getCatalogErrorMessage(productQuery.error, 'Không thể tải chi tiết sản phẩm.')}
          onAction={() => void productQuery.refetch()}
          title="Không thể tải sản phẩm"
        />
      </DashboardLayout>
    )
  }

  if (!product) {
    return (
      <DashboardLayout
        title="Chi tiết sản phẩm"
        description="Inspect sản phẩm trong Catalog"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Quay lại danh sách</Link>
          </Button>
        }
      >
        <EmptyState description="Sản phẩm này không tồn tại hoặc đã bị xóa khỏi Catalog." title="Không tìm thấy sản phẩm" />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Chi tiết sản phẩm"
      description="Read-only inspection cho product, recipe, pricing và outlet availability."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Quay lại danh sách</Link>
          </Button>
          {canWriteProduct ? (
            <Button asChild size="sm">
              <Link to={`/catalog/products/${productId}/edit`}>Edit product</Link>
            </Button>
          ) : null}
        </div>
      }
    >
      <ReadonlyBanner
        message={
          canWriteProduct
            ? 'Product edit đã được publish cho principal có catalog.product.write + system scope. Recipe/pricing/availability writes vẫn đi theo pass riêng.'
            : 'Catalog detail hiện vẫn là read-first với tài khoản hiện tại. Product edit cần catalog.product.write + system scope.'
        }
      />

      <EntityHeader
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Danh sách sản phẩm</Link>
          </Button>
        }
        eyebrow="Catalog / Product"
        metadata={
          <>
            <span>Code: {product.code}</span>
            <span>Category: {product.categoryCode ?? 'Chưa phân loại'}</span>
            <span>Selected outlet: {selectedOutletId ? `#${selectedOutletId}` : 'Chưa chọn'}</span>
          </>
        }
        status={<ProductStatusBadge status={product.status} />}
        title={product.name}
      />

      <SectionCard title="Product overview">
        <div className="field-grid">
          <div>
            <p className="eyebrow">Mã sản phẩm</p>
            <strong>{product.code}</strong>
          </div>
          <div>
            <p className="eyebrow">Danh mục</p>
            <strong>{product.categoryCode ?? 'Chưa phân loại'}</strong>
          </div>
          <div>
            <p className="eyebrow">Trạng thái</p>
            <ProductStatusBadge status={product.status} />
          </div>
          <div>
            <p className="eyebrow">Hình ảnh</p>
            <strong>{product.imageUrl ? 'Có image URL' : 'Chưa cấu hình'}</strong>
          </div>
        </div>
        <div>
          <p className="eyebrow">Mô tả</p>
          <p className="muted-text">{product.description ?? 'Chưa có mô tả cho sản phẩm này.'}</p>
        </div>
      </SectionCard>

      <SectionCard description="Recipe snapshot chỉ hiển thị nếu current principal có catalog.recipe.read." title="Recipe snapshot">
        {!canViewRecipes ? (
          <PermissionDeniedInline message="Bạn không có quyền catalog.recipe.read nên phần recipe snapshot bị ẩn." />
        ) : recipesQuery.error || recipeVersionsQuery.error ? (
          <ErrorState
            actionLabel="Tải lại"
            message={getCatalogErrorMessage(
              recipesQuery.error ?? recipeVersionsQuery.error,
              'Không thể tải dữ liệu công thức cho sản phẩm này.',
            )}
            onAction={() => {
              void recipesQuery.refetch()
              if (productRecipe?.id) {
                void recipeVersionsQuery.refetch()
              }
            }}
            title="Không thể tải recipe snapshot"
          />
        ) : recipesQuery.isLoading || recipeVersionsQuery.isLoading ? (
          <Card title="Đang tải công thức">
            <p className="muted-text">Đang tải recipe và recipe versions cho sản phẩm này...</p>
          </Card>
        ) : !productRecipe ? (
          <EmptyState
            description="Sản phẩm này chưa được gắn recipe read model trong Catalog."
            title="Chưa có recipe"
          />
        ) : (
          <div className="page-stack">
            <div className="meta-grid">
              <span>Recipe code: {productRecipe.recipeCode}</span>
              <span>Description: {productRecipe.description ?? 'Không có mô tả'}</span>
            </div>
            <DataTable
              columns={recipeVersionColumns}
              emptyDescription="Recipe đã có metadata nhưng chưa có version snapshot nào."
              emptyTitle="Chưa có recipe version"
              rows={recipeVersionsQuery.data ?? []}
              rowKey={(version) => version.id}
            />
          </div>
        )}
      </SectionCard>

      <SectionCard description="Pricing snapshot được join client-side từ product-prices theo productId." title="Pricing snapshot">
        {!canViewPrices ? (
          <PermissionDeniedInline message="Bạn không có quyền catalog.price.read nên pricing snapshot bị ẩn." />
        ) : (
          <DataTable
            columns={pricingColumns}
            emptyDescription="Sản phẩm này chưa có cấu hình giá hiệu lực ở GLOBAL/REGION/OUTLET."
            emptyTitle="Chưa có giá hiệu lực"
            error={pricesQuery.error ? getCatalogErrorMessage(pricesQuery.error, 'Không thể tải pricing snapshot.') : null}
            loading={pricesQuery.isLoading}
            loadingDescription="Đang tải toàn bộ price list và lọc theo productId..."
            loadingTitle="Đang tải bảng giá"
            onRetry={() => void pricesQuery.refetch()}
            rowKey={(price) => price.id}
            rows={productPrices}
          />
        )}
      </SectionCard>

      <SectionCard description="Availability snapshot hiển thị outlet availability theo productId." title="Outlet availability">
        {!canViewPrices ? (
          <PermissionDeniedInline message="Availability dùng cùng quyền catalog.price.read nên phần này đang bị ẩn." />
        ) : (
          <DataTable
            columns={availabilityColumns}
            emptyDescription="Chưa có availability row nào cho sản phẩm này."
            emptyTitle="Chưa có dữ liệu availability"
            error={
              availabilityQuery.error
                ? getCatalogErrorMessage(availabilityQuery.error, 'Không thể tải outlet availability.')
                : null
            }
            loading={availabilityQuery.isLoading}
            loadingDescription="Đang tải availability theo productId..."
            loadingTitle="Đang tải availability"
            onRetry={() => void availabilityQuery.refetch()}
            rowKey={(availability) => `${availability.productId}-${availability.outletId}`}
            rows={availabilityRows}
          />
        )}
      </SectionCard>
    </DashboardLayout>
  )
}
