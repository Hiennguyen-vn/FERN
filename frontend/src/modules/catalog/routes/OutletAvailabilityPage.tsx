import { useEffect, useMemo, useState } from 'react'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Card,
  DataTable,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAvailability } from '../hooks/usePricing'
import { useProducts } from '../hooks/useProducts'
import type { ProductAvailability } from '../model/catalog.types'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import { canReadPrices, canReadProducts } from '../services/catalogPermission.service'
import {
  buildProductMap,
  formatProductLabel,
  matchesSearch,
  sortAvailabilityRows,
} from '../services/catalogReadModel.service'

const availabilityOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'Available', value: 'AVAILABLE' },
  { label: 'Unavailable', value: 'UNAVAILABLE' },
]

export function AvailabilityPage() {
  usePageTitle('Outlet Availability — Catalog')
  const principal = usePrincipal()
  const { selectedOutletId } = useScopeContext()
  const canViewPrices = canReadPrices(principal)
  const canViewProductLabels = canReadProducts(principal)
  const [productSearch, setProductSearch] = useState('')
  const [availabilityFilter, setAvailabilityFilter] = useState<'ALL' | 'AVAILABLE' | 'UNAVAILABLE'>('ALL')
  const [outletFilter, setOutletFilter] = useState(() => (selectedOutletId ? String(selectedOutletId) : ''))

  useEffect(() => {
    if (!outletFilter && selectedOutletId) {
      setOutletFilter(String(selectedOutletId))
    }
  }, [outletFilter, selectedOutletId])

  const outletId = outletFilter.trim() ? Number(outletFilter) : undefined
  const filters = useMemo(
    () => ({
      outletId: Number.isFinite(outletId) && outletId && outletId > 0 ? outletId : undefined,
    }),
    [outletId],
  )

  const availabilityQuery = useAvailability(filters, { enabled: canViewPrices })
  const productsQuery = useProducts({ enabled: canViewPrices && canViewProductLabels })
  const productMap = useMemo(() => buildProductMap(productsQuery.data ?? []), [productsQuery.data])

  const filteredRows = useMemo(() => {
    return sortAvailabilityRows(availabilityQuery.data ?? [], selectedOutletId).filter((row) => {
      const product = productMap.get(row.productId)
      const matchesAvailability =
        availabilityFilter === 'ALL' ||
        (availabilityFilter === 'AVAILABLE' && row.available) ||
        (availabilityFilter === 'UNAVAILABLE' && !row.available)
      const matchesProduct = matchesSearch(
        [row.productId, product?.code, product?.name],
        productSearch,
      )

      return matchesAvailability && matchesProduct
    })
  }, [availabilityFilter, availabilityQuery.data, productMap, productSearch, selectedOutletId])

  const columns = useMemo<Array<DataTableColumn<ProductAvailability>>>(
    () => [
      {
        key: 'product',
        header: 'Sản phẩm',
        render: (row) => formatProductLabel(productMap.get(row.productId), row.productId),
      },
      {
        key: 'outlet',
        header: 'Outlet',
        render: (row) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <span>Outlet #{row.outletId}</span>
            {row.outletId === selectedOutletId ? <span className="badge badge-warning">Current outlet</span> : null}
          </div>
        ),
      },
      {
        key: 'availability',
        header: 'Availability',
        render: (row) =>
          row.available ? (
            <span className="badge badge-success">Available</span>
          ) : (
            <span className="badge badge-danger">Unavailable</span>
          ),
      },
    ],
    [productMap, selectedOutletId],
  )

  if (!canViewPrices) {
    return (
      <DashboardLayout title="Outlet availability" description="Browse outlet availability read model trong Catalog">
        <PermissionDeniedInline message="Bạn cần quyền catalog.price.read để xem outlet availability." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout title="Outlet availability" description="Browse outlet availability cho từng sản phẩm theo read model.">
      <ReadonlyBanner message="Availability đang publish theo read-first UX. Hệ thống không giả lập availability offline hay write-back ở bước này." />

      {!selectedOutletId ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Chưa chọn outlet ở app shell. Trang vẫn cho phép browse cross-outlet bằng dữ liệu read model hiện có.
        </div>
      ) : null}

      {!canViewProductLabels ? (
        <PermissionDeniedInline message="Bạn không có quyền catalog.product.read nên cột sản phẩm sẽ hiển thị productId thay vì tên sản phẩm." />
      ) : productsQuery.error ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Không thể tải metadata sản phẩm. Availability vẫn hiển thị nhưng tên sản phẩm sẽ fallback sang productId.
        </div>
      ) : null}

      <Card title="Bộ lọc availability">
        <div className="field-grid">
          <Input
            label="Outlet ID"
            onChange={(event) => setOutletFilter(event.target.value)}
            placeholder="VD: 101"
            type="number"
            value={outletFilter}
          />
          <Input
            label="Tìm theo product ID, mã hoặc tên"
            onChange={(event) => setProductSearch(event.target.value)}
            placeholder="VD: 1001 hoặc COFFEE"
            value={productSearch}
          />
          <Select
            label="Availability"
            onChange={(event) => setAvailabilityFilter(event.target.value as 'ALL' | 'AVAILABLE' | 'UNAVAILABLE')}
            options={availabilityOptions}
            value={availabilityFilter}
          />
        </div>
        <div className="meta-grid">
          <span>Tổng dòng availability: {availabilityQuery.data?.length ?? 0}</span>
          <span>Kết quả sau lọc: {filteredRows.length}</span>
          <span>Outlet mặc định: {selectedOutletId ? `#${selectedOutletId}` : 'Không có'}</span>
        </div>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription={
          productSearch.trim() || availabilityFilter !== 'ALL' || outletFilter.trim()
            ? 'Không có dòng availability nào khớp bộ lọc hiện tại.'
            : 'Catalog chưa có dữ liệu availability để hiển thị.'
        }
        emptyTitle={
          productSearch.trim() || availabilityFilter !== 'ALL' || outletFilter.trim()
            ? 'Không có availability khớp bộ lọc'
            : 'Availability list đang trống'
        }
        error={
          availabilityQuery.error
            ? getCatalogErrorMessage(availabilityQuery.error, 'Không thể tải outlet availability.')
            : null
        }
        loading={availabilityQuery.isLoading}
        loadingDescription="Đang tải availability rows từ gateway..."
        loadingTitle="Đang tải availability"
        onRetry={() => void availabilityQuery.refetch()}
        rowKey={(row) => `${row.productId}-${row.outletId}`}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
