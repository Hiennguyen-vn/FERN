import { Link, useNavigate, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, EmptyState, ErrorState, PermissionDeniedInline } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ProductForm } from '../forms/ProductForm'
import { useProduct, useProductCategories, useUpdateProduct } from '../hooks/useProducts'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import { canReadProducts, canWriteProducts } from '../services/catalogPermission.service'

export function ProductEditPage() {
  const { productId: productIdParam } = useParams<{ productId: string }>()
  const productId = Number(productIdParam)
  usePageTitle('Edit Product — Catalog')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canWrite = canWriteProducts(principal)
  const canRead = canReadProducts(principal)
  const hasSystemScope = principal?.scopeRoots?.system === true
  const productQuery = useProduct(productId, { enabled: canRead && Number.isFinite(productId) && productId > 0 })
  const categoriesQuery = useProductCategories({ enabled: canWrite && hasSystemScope })
  const updateMutation = useUpdateProduct(productId)

  if (!canWrite) {
    return (
      <DashboardLayout title="Edit Product" description="Update an existing catalog product.">
        <PermissionDeniedInline message="Bạn cần quyền catalog.product.write để sửa sản phẩm." />
      </DashboardLayout>
    )
  }

  if (!hasSystemScope) {
    return (
      <DashboardLayout title="Edit Product" description="Update an existing catalog product.">
        <PermissionDeniedInline message="Sửa sản phẩm yêu cầu system scope vì backend enforce catalog.product.write ở phạm vi system." />
      </DashboardLayout>
    )
  }

  if (!canRead) {
    return (
      <DashboardLayout title="Edit Product" description="Update an existing catalog product.">
        <PermissionDeniedInline message="Trang edit cần thêm catalog.product.read để tải dữ liệu sản phẩm hiện tại." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(productId) || productId <= 0) {
    return (
      <DashboardLayout
        title="Edit Product"
        description="Update an existing catalog product."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Back to products</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa productId hợp lệ." title="Thiếu productId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (productQuery.isLoading) {
    return (
      <DashboardLayout title="Edit Product" description="Update an existing catalog product.">
        <Card title="Đang tải sản phẩm">
          <p className="muted-text">Đang tải product detail để chỉnh sửa...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (productQuery.error) {
    return (
      <DashboardLayout
        title="Edit Product"
        description="Update an existing catalog product."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Back to products</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Retry"
          message={getCatalogErrorMessage(productQuery.error, 'Không thể tải sản phẩm để chỉnh sửa.')}
          onAction={() => void productQuery.refetch()}
          title="Không thể tải sản phẩm"
        />
      </DashboardLayout>
    )
  }

  if (!productQuery.data) {
    return (
      <DashboardLayout
        title="Edit Product"
        description="Update an existing catalog product."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/products">Back to products</Link>
          </Button>
        }
      >
        <EmptyState description="Sản phẩm này không tồn tại hoặc đã bị xóa." title="Không tìm thấy sản phẩm" />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Edit Product"
      description="Write workflow cho product master đã được publish theo backend public API."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to={`/catalog/products/${productId}`}>Back to product detail</Link>
        </Button>
      }
    >
      {categoriesQuery.error ? (
        <p className="error-text">
          {categoriesQuery.error instanceof Error ? categoriesQuery.error.message : 'Không thể tải product categories.'}
        </p>
      ) : null}

      <Card title="Product form">
        <ProductForm
          categories={categoriesQuery.data ?? []}
          initial={productQuery.data}
          onCancel={() => navigate(`/catalog/products/${productId}`)}
          onSubmit={async (payload) => {
            const product = await updateMutation.mutateAsync(payload)
            navigate(`/catalog/products/${product.id}`)
          }}
          submitting={updateMutation.isPending}
        />
      </Card>

      {updateMutation.error ? (
        <p className="error-text">
          {updateMutation.error instanceof Error ? updateMutation.error.message : 'Không thể cập nhật sản phẩm.'}
        </p>
      ) : null}
    </DashboardLayout>
  )
}
