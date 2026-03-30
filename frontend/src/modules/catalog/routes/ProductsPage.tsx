import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Card, DataTable, Input, PermissionDeniedInline, ReadonlyBanner, Select } from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePrincipal } from '@core/auth/auth.selectors'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { ProductStatusBadge, STATUS_LABELS } from '../components/ProductStatusBadge'
import { useProductCategories, useProducts } from '../hooks/useProducts'
import type { Product, ProductStatus } from '../model/catalog.types'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import { canReadProducts } from '../services/catalogPermission.service'
import { matchesSearch } from '../services/catalogReadModel.service'

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: STATUS_LABELS.ACTIVE, value: 'ACTIVE' },
  { label: STATUS_LABELS.INACTIVE, value: 'INACTIVE' },
  { label: STATUS_LABELS.DISCONTINUED, value: 'DISCONTINUED' },
]

export function ProductsPage() {
  usePageTitle('Sản phẩm — Catalog')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canViewProducts = canReadProducts(principal)
  const { data: products = [], error, isLoading, refetch } = useProducts({ enabled: canViewProducts })
  const {
    data: categories = [],
    error: categoriesError,
  } = useProductCategories({ enabled: canViewProducts })
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<ProductStatus | 'ALL'>('ALL')
  const [categoryFilter, setCategoryFilter] = useState('ALL')

  const categoryOptions = useMemo<SelectOption[]>(() => {
    return [
      { label: 'Tất cả danh mục', value: 'ALL' },
      ...categories.map((category) => ({
        label: `${category.name} (${category.code})`,
        value: category.code,
      })),
    ]
  }, [categories])

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
        header: 'Mã',
        render: (product) => <span>{product.code}</span>,
      },
      {
        key: 'name',
        header: 'Sản phẩm',
        render: (product) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{product.name}</strong>
            <span className="muted-text">{product.description ?? 'Không có mô tả.'}</span>
          </div>
        ),
      },
      {
        key: 'category',
        header: 'Danh mục',
        render: (product) =>
          categoryLookup.get(product.categoryCode ?? '')?.name ?? product.categoryCode ?? 'Chưa phân loại',
      },
      {
        key: 'status',
        header: 'Trạng thái',
        render: (product) => <ProductStatusBadge status={product.status} />,
      },
    ],
    [categoryLookup],
  )

  const hasFilters = Boolean(search.trim()) || statusFilter !== 'ALL' || categoryFilter !== 'ALL'
  const emptyTitle = products.length === 0 ? 'Chưa có sản phẩm nào' : 'Không có sản phẩm khớp bộ lọc'
  const emptyDescription =
    products.length === 0
      ? 'Catalog chưa có bản ghi sản phẩm nào để hiển thị.'
      : 'Thử thay đổi từ khóa, trạng thái hoặc danh mục để mở rộng kết quả.'

  if (!canViewProducts) {
    return (
      <DashboardLayout title="Sản phẩm" description="Browse sản phẩm trong Catalog">
        <PermissionDeniedInline message="Bạn cần quyền catalog.product.read để xem danh sách sản phẩm." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout title="Sản phẩm" description="Browse và inspect danh mục sản phẩm đang được publish.">
      <ReadonlyBanner message="Catalog đang được publish ở chế độ browse/inspect only trong giai đoạn này." />

      <Card title="Bộ lọc sản phẩm">
        <div className="field-grid">
          <Input
            label="Tìm theo mã, tên hoặc mô tả"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="VD: CF-001 hoặc Iced Coffee"
            value={search}
          />
          <Select
            label="Trạng thái"
            onChange={(event) => setStatusFilter(event.target.value as ProductStatus | 'ALL')}
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
        <div className="meta-grid">
          <span>Tổng sản phẩm: {products.length}</span>
          <span>Kết quả sau lọc: {filteredRows.length}</span>
          <span>Chế độ: Read-first catalog browse</span>
        </div>
      </Card>

      {categoriesError ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Không thể tải metadata danh mục. Bảng vẫn hiển thị nhưng nhãn danh mục có thể dùng mã raw.
        </div>
      ) : null}

      <DataTable
        columns={columns}
        emptyDescription={hasFilters ? emptyDescription : 'Catalog chưa có sản phẩm nào để hiển thị.'}
        emptyTitle={hasFilters ? emptyTitle : 'Danh sách sản phẩm đang trống'}
        error={error ? getCatalogErrorMessage(error, 'Không thể tải danh sách sản phẩm.') : null}
        loading={isLoading}
        loadingDescription="Đang tải danh sách sản phẩm và metadata danh mục..."
        loadingTitle="Đang tải sản phẩm"
        onRetry={() => void refetch()}
        onRowClick={(product) => navigate(`/catalog/products/${product.id}`)}
        rowKey={(product) => product.id}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
