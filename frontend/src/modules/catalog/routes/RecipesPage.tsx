import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Card,
  DataTable,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useProducts } from '../hooks/useProducts'
import { useRecipes } from '../hooks/useRecipes'
import type { Recipe } from '../model/catalog.types'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import { canReadProducts, canReadRecipes } from '../services/catalogPermission.service'
import { buildProductMap, formatProductLabel, matchesSearch } from '../services/catalogReadModel.service'

export function RecipesPage() {
  usePageTitle('Công thức — Catalog')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canViewRecipes = canReadRecipes(principal)
  const canViewProductLabels = canReadProducts(principal)
  const recipesQuery = useRecipes({ enabled: canViewRecipes })
  const productsQuery = useProducts({ enabled: canViewRecipes && canViewProductLabels })
  const [search, setSearch] = useState('')

  const productMap = useMemo(() => buildProductMap(productsQuery.data ?? []), [productsQuery.data])
  const filteredRows = useMemo(() => {
    return (recipesQuery.data ?? []).filter((recipe) => {
      const product = productMap.get(recipe.productId)
      return matchesSearch(
        [recipe.recipeCode, recipe.description, recipe.productId, product?.code, product?.name],
        search,
      )
    })
  }, [productMap, recipesQuery.data, search])

  const columns = useMemo<Array<DataTableColumn<Recipe>>>(
    () => [
      {
        key: 'recipeCode',
        header: 'Recipe code',
        render: (recipe) => recipe.recipeCode,
      },
      {
        key: 'description',
        header: 'Mô tả',
        render: (recipe) => recipe.description ?? 'Không có mô tả',
      },
      {
        key: 'product',
        header: 'Sản phẩm',
        render: (recipe) => formatProductLabel(productMap.get(recipe.productId), recipe.productId),
      },
    ],
    [productMap],
  )

  if (!canViewRecipes) {
    return (
      <DashboardLayout title="Công thức" description="Browse recipe definitions trong Catalog">
        <PermissionDeniedInline message="Bạn cần quyền catalog.recipe.read để xem recipe list." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout title="Công thức" description="Browse recipe definitions đang được publish read-first.">
      <ReadonlyBanner message="Recipes đang publish ở mức browse/inspect. Detailed version workflow hiện nằm trong Product Detail read-only snapshot." />

      {!canViewProductLabels ? (
        <PermissionDeniedInline message="Bạn không có quyền catalog.product.read nên cột sản phẩm sẽ hiển thị theo productId thay vì tên sản phẩm." />
      ) : productsQuery.error ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Không thể tải metadata sản phẩm. Bảng recipe vẫn hiển thị nhưng tên sản phẩm sẽ fallback sang productId.
        </div>
      ) : null}

      <Card title="Tìm recipe">
        <div className="field-grid">
          <Input
            label="Tìm theo recipe code, mô tả hoặc sản phẩm"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="VD: REC-COFFEE hoặc #1001"
            value={search}
          />
        </div>
        <div className="meta-grid">
          <span>Tổng recipe: {recipesQuery.data?.length ?? 0}</span>
          <span>Kết quả sau lọc: {filteredRows.length}</span>
          <span>Sản phẩm join: {canViewProductLabels && !productsQuery.error ? 'Available' : 'Fallback by productId'}</span>
        </div>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription={
          search.trim()
            ? 'Không có recipe nào khớp từ khóa hiện tại.'
            : 'Catalog chưa có recipe nào để hiển thị.'
        }
        emptyTitle={search.trim() ? 'Không có recipe khớp bộ lọc' : 'Recipe list đang trống'}
        error={recipesQuery.error ? getCatalogErrorMessage(recipesQuery.error, 'Không thể tải danh sách recipe.') : null}
        loading={recipesQuery.isLoading}
        loadingDescription="Đang tải recipe list..."
        loadingTitle="Đang tải công thức"
        onRetry={() => void recipesQuery.refetch()}
        onRowClick={canViewProductLabels ? (recipe) => navigate(`/catalog/products/${recipe.productId}`) : undefined}
        rowKey={(recipe) => recipe.id}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
