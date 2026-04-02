import { Link, useNavigate, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, EmptyState, ErrorState, PermissionDeniedInline } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { IngredientForm } from '../forms/IngredientForm'
import { useIngredient, useUpdateIngredient } from '../hooks/useIngredients'
import { useIngredientCategories, useUnitsOfMeasure } from '../hooks/useProducts'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import { canReadIngredients, canWriteIngredients } from '../services/catalogPermission.service'

export function IngredientEditPage() {
  const { ingredientId: ingredientIdParam } = useParams<{ ingredientId: string }>()
  const ingredientId = Number(ingredientIdParam)
  usePageTitle('Edit Ingredient — Catalog')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canWrite = canWriteIngredients(principal)
  const canRead = canReadIngredients(principal)
  const hasSystemScope = principal?.scopeRoots?.system === true
  const ingredientQuery = useIngredient(ingredientId, { enabled: canRead && Number.isFinite(ingredientId) && ingredientId > 0 })
  const categoriesQuery = useIngredientCategories({ enabled: canWrite && hasSystemScope })
  const uomsQuery = useUnitsOfMeasure({ enabled: canWrite && hasSystemScope })
  const updateMutation = useUpdateIngredient(ingredientId)

  if (!canWrite) {
    return (
      <DashboardLayout title="Edit Ingredient" description="Update an existing catalog ingredient.">
        <PermissionDeniedInline message="Bạn cần quyền catalog.ingredient.write để sửa nguyên liệu." />
      </DashboardLayout>
    )
  }

  if (!hasSystemScope) {
    return (
      <DashboardLayout title="Edit Ingredient" description="Update an existing catalog ingredient.">
        <PermissionDeniedInline message="Sửa nguyên liệu yêu cầu system scope vì backend enforce catalog.ingredient.write ở phạm vi system." />
      </DashboardLayout>
    )
  }

  if (!canRead) {
    return (
      <DashboardLayout title="Edit Ingredient" description="Update an existing catalog ingredient.">
        <PermissionDeniedInline message="Trang edit cần thêm catalog.ingredient.read để tải dữ liệu nguyên liệu hiện tại." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(ingredientId) || ingredientId <= 0) {
    return (
      <DashboardLayout
        title="Edit Ingredient"
        description="Update an existing catalog ingredient."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/ingredients">Back to ingredients</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa ingredientId hợp lệ." title="Thiếu ingredientId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (ingredientQuery.isLoading) {
    return (
      <DashboardLayout title="Edit Ingredient" description="Update an existing catalog ingredient.">
        <Card title="Đang tải nguyên liệu">
          <p className="muted-text">Đang tải ingredient detail để chỉnh sửa...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (ingredientQuery.error) {
    return (
      <DashboardLayout
        title="Edit Ingredient"
        description="Update an existing catalog ingredient."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/ingredients">Back to ingredients</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Retry"
          message={getCatalogErrorMessage(ingredientQuery.error, 'Không thể tải nguyên liệu để chỉnh sửa.')}
          onAction={() => void ingredientQuery.refetch()}
          title="Không thể tải nguyên liệu"
        />
      </DashboardLayout>
    )
  }

  if (!ingredientQuery.data) {
    return (
      <DashboardLayout
        title="Edit Ingredient"
        description="Update an existing catalog ingredient."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/catalog/ingredients">Back to ingredients</Link>
          </Button>
        }
      >
        <EmptyState description="Nguyên liệu này không tồn tại hoặc đã bị xóa." title="Không tìm thấy nguyên liệu" />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Edit Ingredient"
      description="Write workflow cho ingredient master đã được publish theo backend public API."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/catalog/ingredients">Back to ingredients</Link>
        </Button>
      }
    >
      {categoriesQuery.error || uomsQuery.error ? (
        <p className="error-text">
          {categoriesQuery.error instanceof Error
            ? categoriesQuery.error.message
            : uomsQuery.error instanceof Error
              ? uomsQuery.error.message
              : 'Không thể tải metadata nguyên liệu.'}
        </p>
      ) : null}

      <Card title="Ingredient form">
        <IngredientForm
          categories={categoriesQuery.data ?? []}
          initial={ingredientQuery.data}
          onCancel={() => navigate('/catalog/ingredients')}
          onSubmit={async (payload) => {
            await updateMutation.mutateAsync(payload)
            navigate('/catalog/ingredients')
          }}
          submitting={updateMutation.isPending}
          uoms={uomsQuery.data ?? []}
        />
      </Card>

      {updateMutation.error ? (
        <p className="error-text">
          {updateMutation.error instanceof Error ? updateMutation.error.message : 'Không thể cập nhật nguyên liệu.'}
        </p>
      ) : null}
    </DashboardLayout>
  )
}
