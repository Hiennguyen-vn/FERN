import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { Button, Card, DataTable, Input, PermissionDeniedInline, ReadonlyBanner, Select } from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePrincipal } from '@core/auth/auth.selectors'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { LoadedSubsetMeta } from '@shared/ui/LoadedSubsetMeta'
import { useIngredients } from '../hooks/useIngredients'
import { useIngredientCategories } from '../hooks/useProducts'
import type { Ingredient, IngredientStatus } from '../model/catalog.types'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import { canReadIngredients, canWriteIngredients } from '../services/catalogPermission.service'
import { matchesSearch } from '../services/catalogReadModel.service'

// Backend IngredientStatus: ACTIVE, INACTIVE, DISCONTINUED
const STATUS_LABELS: Record<IngredientStatus, string> = {
  ACTIVE: 'Đang dùng',
  INACTIVE: 'Ngừng dùng',
  DISCONTINUED: 'Ngừng kinh doanh',
}

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: STATUS_LABELS.ACTIVE, value: 'ACTIVE' },
  { label: STATUS_LABELS.INACTIVE, value: 'INACTIVE' },
  { label: STATUS_LABELS.DISCONTINUED, value: 'DISCONTINUED' },
]

const STATUS_BADGE_CLASS: Record<IngredientStatus, string> = {
  ACTIVE: 'badge badge-success',
  INACTIVE: 'badge badge-neutral',
  DISCONTINUED: 'badge badge-danger',
}

function IngredientStatusBadge({ status }: { status: IngredientStatus }) {
  return (
    <span className={STATUS_BADGE_CLASS[status] ?? 'badge badge-neutral'}>
      {STATUS_LABELS[status] ?? status}
    </span>
  )
}

export function IngredientsPage() {
  usePageTitle('Nguyên liệu — Catalog')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canViewIngredients = canReadIngredients(principal)
  const canWrite = canWriteIngredients(principal) && principal?.scopeRoots?.system === true
  // Backend CatalogAuthorizer.requireSystemPermission gates all ingredient endpoints.
  // Non-system users with catalog.ingredient.read permission will still get a 403 from
  // the backend. Skip the fetch and show a targeted scope warning instead.
  const hasSystemScope = principal?.scopeRoots?.system === true
  const canFetchIngredients = canViewIngredients && hasSystemScope
  const { data: ingredients = [], error, isLoading, refetch } = useIngredients({ enabled: canFetchIngredients })
  const { data: categories = [], error: categoriesError } = useIngredientCategories({ enabled: canFetchIngredients })
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<IngredientStatus | 'ALL'>('ALL')
  const [categoryFilter, setCategoryFilter] = useState('ALL')

  const categoryOptions = useMemo<SelectOption[]>(
    () => [
      { label: 'Tất cả danh mục', value: 'ALL' },
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
    return ingredients.filter((ingredient) => {
      const matchesStatus = statusFilter === 'ALL' || ingredient.status === statusFilter
      const matchesCategory = categoryFilter === 'ALL' || ingredient.categoryCode === categoryFilter
      const matchesQuery = matchesSearch(
        [ingredient.code, ingredient.name, ingredient.categoryCode, ingredient.baseUomCode],
        search,
      )

      return matchesStatus && matchesCategory && matchesQuery
    })
  }, [categoryFilter, ingredients, search, statusFilter])

  const columns = useMemo<Array<DataTableColumn<Ingredient>>>(
    () => [
      {
        key: 'code',
        header: 'Mã',
        render: (ingredient) => <span>{ingredient.code}</span>,
      },
      {
        key: 'name',
        header: 'Nguyên liệu',
        render: (ingredient) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{ingredient.name}</strong>
            <span className="muted-text">ĐVT gốc: {ingredient.baseUomCode}</span>
          </div>
        ),
      },
      {
        key: 'category',
        header: 'Danh mục',
        render: (ingredient) =>
          categoryLookup.get(ingredient.categoryCode ?? '')?.name ?? ingredient.categoryCode ?? 'Chưa phân loại',
      },
      {
        key: 'stockLevel',
        header: 'Tồn min / max',
        render: (ingredient) => `${ingredient.minStockLevel ?? '—'} / ${ingredient.maxStockLevel ?? '—'}`,
      },
      {
        key: 'status',
        header: 'Trạng thái',
        render: (ingredient) => <IngredientStatusBadge status={ingredient.status} />,
      },
    ],
    [categoryLookup],
  )

  const hasFilters = Boolean(search.trim()) || statusFilter !== 'ALL' || categoryFilter !== 'ALL'

  if (!canViewIngredients) {
    return (
      <DashboardLayout title="Nguyên liệu" description="Browse nguyên liệu trong Catalog">
        <PermissionDeniedInline message="Bạn cần quyền catalog.ingredient.read để xem danh mục nguyên liệu." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Nguyên liệu"
      description="Danh mục nguyên liệu dùng cho recipe và inventory planning."
      actions={
        canWrite ? (
          <Button asChild size="sm">
            <Link to="/catalog/ingredients/new">+ Create ingredient</Link>
          </Button>
        ) : null
      }
    >
      <ReadonlyBanner
        message={
          canWrite
            ? 'Ingredient writes đã được publish cho principal có catalog.ingredient.write + system scope.'
            : 'Ingredient writes chỉ publish cho principal có catalog.ingredient.write + system scope. Tài khoản hiện tại đang ở chế độ browse.'
        }
      />

      {!hasSystemScope && (
        <div className="inline-banner inline-banner-warning" role="status">
          Danh mục nguyên liệu yêu cầu <strong>system scope</strong>. Tài khoản hiện tại chỉ có scope outlet/region
          nên backend sẽ từ chối truy cập endpoint này. Liên hệ System Admin để được cấp system scope nếu cần xem dữ liệu
          nguyên liệu.
        </div>
      )}

      <Card title="Bộ lọc nguyên liệu">
        <div className="field-grid">
          <Input
            label="Tìm theo mã, tên hoặc đơn vị"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="VD: MILK hoặc Whole Milk"
            value={search}
          />
          <Select
            label="Trạng thái"
            onChange={(event) => setStatusFilter(event.target.value as IngredientStatus | 'ALL')}
            options={statusOptions}
            value={statusFilter}
          />
          <Select
            label="Danh mục"
            onChange={(event) => setCategoryFilter(event.target.value)}
            options={categoryOptions}
            value={categoryFilter}
          />
        </div>
        <LoadedSubsetMeta
          entityLabel="nguyên liệu"
          filteredCount={filteredRows.length}
          loadedCount={ingredients.length}
        />
      </Card>

      {categoriesError ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Không thể tải metadata danh mục nguyên liệu. Bảng vẫn hiển thị nhưng một số nhãn có thể dùng mã raw.
        </div>
      ) : null}

      <DataTable
        columns={columns}
        emptyDescription={
          hasFilters
            ? 'Không có nguyên liệu khớp bộ lọc hiện tại.'
            : 'Catalog chưa có nguyên liệu nào để hiển thị.'
        }
        emptyTitle={hasFilters ? 'Không có nguyên liệu khớp bộ lọc' : 'Danh mục nguyên liệu đang trống'}
        error={error ? getCatalogErrorMessage(error, 'Không thể tải danh mục nguyên liệu.') : null}
        loading={isLoading}
        loadingDescription="Đang tải danh sách nguyên liệu..."
        loadingTitle="Đang tải nguyên liệu"
        onRetry={() => void refetch()}
        onRowClick={canWrite ? (ingredient) => navigate(`/catalog/ingredients/${ingredient.id}/edit`) : undefined}
        rowKey={(ingredient) => ingredient.id}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
