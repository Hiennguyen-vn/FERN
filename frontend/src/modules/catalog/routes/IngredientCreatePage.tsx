import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, PermissionDeniedInline } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { IngredientForm } from '../forms/IngredientForm'
import { useCreateIngredient } from '../hooks/useIngredients'
import { useIngredientCategories, useUnitsOfMeasure } from '../hooks/useProducts'
import { canWriteIngredients } from '../services/catalogPermission.service'

export function IngredientCreatePage() {
  usePageTitle('Create Ingredient — Catalog')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canWrite = canWriteIngredients(principal)
  const hasSystemScope = principal?.scopeRoots?.system === true
  const categoriesQuery = useIngredientCategories({ enabled: canWrite && hasSystemScope })
  const uomsQuery = useUnitsOfMeasure({ enabled: canWrite && hasSystemScope })
  const createMutation = useCreateIngredient()

  if (!canWrite) {
    return (
      <DashboardLayout title="Create Ingredient" description="Create a new catalog ingredient.">
        <PermissionDeniedInline message="Bạn cần quyền catalog.ingredient.write để tạo nguyên liệu." />
      </DashboardLayout>
    )
  }

  if (!hasSystemScope) {
    return (
      <DashboardLayout title="Create Ingredient" description="Create a new catalog ingredient.">
        <PermissionDeniedInline message="Tạo nguyên liệu yêu cầu system scope vì backend enforce catalog.ingredient.write ở phạm vi system." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Create Ingredient"
      description="Publish write workflow cho ingredient master theo backend contract hiện tại."
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
          onCancel={() => navigate('/catalog/ingredients')}
          onSubmit={async (payload) => {
            await createMutation.mutateAsync(payload)
            navigate('/catalog/ingredients')
          }}
          submitting={createMutation.isPending}
          uoms={uomsQuery.data ?? []}
        />
      </Card>

      {createMutation.error ? (
        <p className="error-text">
          {createMutation.error instanceof Error ? createMutation.error.message : 'Không thể tạo nguyên liệu.'}
        </p>
      ) : null}
    </DashboardLayout>
  )
}
