import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, PermissionDeniedInline } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ProductForm } from '../forms/ProductForm'
import { useCreateProduct, useProductCategories } from '../hooks/useProducts'
import { canWriteProducts } from '../services/catalogPermission.service'

export function ProductCreatePage() {
  usePageTitle('Create Product — Catalog')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canWrite = canWriteProducts(principal)
  const hasSystemScope = principal?.scopeRoots?.system === true
  const categoriesQuery = useProductCategories({ enabled: canWrite && hasSystemScope })
  const createMutation = useCreateProduct()

  if (!canWrite) {
    return (
      <DashboardLayout title="Create Product" description="Create a new catalog product.">
        <PermissionDeniedInline message="Bạn cần quyền catalog.product.write để tạo sản phẩm." />
      </DashboardLayout>
    )
  }

  if (!hasSystemScope) {
    return (
      <DashboardLayout title="Create Product" description="Create a new catalog product.">
        <PermissionDeniedInline message="Tạo sản phẩm yêu cầu system scope vì backend enforce catalog.product.write ở phạm vi system." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Create Product"
      description="Publish write workflow cho product master theo backend contract hiện tại."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/catalog/products">Back to products</Link>
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
          onCancel={() => navigate('/catalog/products')}
          onSubmit={async (payload) => {
            const product = await createMutation.mutateAsync(payload)
            navigate(`/catalog/products/${product.id}`)
          }}
          submitting={createMutation.isPending}
        />
      </Card>

      {createMutation.error ? (
        <p className="error-text">
          {createMutation.error instanceof Error ? createMutation.error.message : 'Không thể tạo sản phẩm.'}
        </p>
      ) : null}
    </DashboardLayout>
  )
}
