import { useMemo, useState } from 'react'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
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
import { useProductPrices } from '../hooks/usePricing'
import { useProducts } from '../hooks/useProducts'
import type { ProductPrice, PriceScopeType, PriceType } from '../model/catalog.types'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import { canReadPrices, canReadProducts } from '../services/catalogPermission.service'
import {
  buildProductMap,
  formatCurrencyAmount,
  formatDateRange,
  formatProductLabel,
  formatScopeLabel,
  getPriceEffectiveState,
  matchesSearch,
  type PriceEffectiveState,
} from '../services/catalogReadModel.service'

// Backend PriceScopeType: GLOBAL, COUNTRY, REGION, OUTLET
const scopeOptions: SelectOption[] = [
  { label: 'Tất cả scope', value: 'ALL' },
  { label: 'Global', value: 'GLOBAL' },
  { label: 'Country', value: 'COUNTRY' },
  { label: 'Region', value: 'REGION' },
  { label: 'Outlet', value: 'OUTLET' },
]

// Backend PriceType: RETAIL, DINE_IN, TAKEAWAY, DELIVERY, WHOLESALE
const priceTypeOptions: SelectOption[] = [
  { label: 'Tất cả loại giá', value: 'ALL' },
  { label: 'Retail', value: 'RETAIL' },
  { label: 'Dine-in', value: 'DINE_IN' },
  { label: 'Takeaway', value: 'TAKEAWAY' },
  { label: 'Delivery', value: 'DELIVERY' },
  { label: 'Wholesale', value: 'WHOLESALE' },
]

const effectiveStateOptions: SelectOption[] = [
  { label: 'Tất cả hiệu lực', value: 'ALL' },
  { label: 'Current', value: 'CURRENT' },
  { label: 'Upcoming', value: 'UPCOMING' },
  { label: 'Expired', value: 'EXPIRED' },
]

const effectiveStateBadgeClass: Record<Exclude<PriceEffectiveState, 'ALL'>, string> = {
  CURRENT: 'badge badge-success',
  UPCOMING: 'badge badge-warning',
  EXPIRED: 'badge badge-neutral',
}

export function PricingPage() {
  usePageTitle('Bảng giá — Catalog')
  const principal = usePrincipal()
  const canViewPrices = canReadPrices(principal)
  const canViewProductLabels = canReadProducts(principal)
  const pricesQuery = useProductPrices({ enabled: canViewPrices })
  const productsQuery = useProducts({ enabled: canViewPrices && canViewProductLabels })
  const [search, setSearch] = useState('')
  const [scopeFilter, setScopeFilter] = useState<PriceScopeType | 'ALL'>('ALL')
  const [priceTypeFilter, setPriceTypeFilter] = useState<PriceType | 'ALL'>('ALL')
  const [effectiveFilter, setEffectiveFilter] = useState<PriceEffectiveState>('ALL')

  const productMap = useMemo(() => buildProductMap(productsQuery.data ?? []), [productsQuery.data])
  const filteredRows = useMemo(() => {
    return (pricesQuery.data ?? []).filter((price) => {
      const effectiveState = getPriceEffectiveState(price)
      const product = productMap.get(price.productId)
      const matchesScope = scopeFilter === 'ALL' || price.scopeType === scopeFilter
      const matchesPriceType = priceTypeFilter === 'ALL' || price.priceType === priceTypeFilter
      const matchesEffective = effectiveFilter === 'ALL' || effectiveState === effectiveFilter
      const matchesQuery = matchesSearch(
        [price.productId, product?.code, product?.name, price.currencyCode, price.scopeId],
        search,
      )

      return matchesScope && matchesPriceType && matchesEffective && matchesQuery
    })
  }, [effectiveFilter, priceTypeFilter, pricesQuery.data, productMap, scopeFilter, search])

  const columns = useMemo<Array<DataTableColumn<ProductPrice>>>(
    () => [
      {
        key: 'product',
        header: 'Sản phẩm',
        render: (price) => formatProductLabel(productMap.get(price.productId), price.productId),
      },
      {
        key: 'scope',
        header: 'Scope',
        render: (price) => formatScopeLabel(price.scopeType, price.scopeId),
      },
      {
        key: 'priceType',
        header: 'Loại giá',
        render: (price) => price.priceType,
      },
      {
        key: 'value',
        header: 'Giá trị',
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
          return <span className={effectiveStateBadgeClass[state]}>{state}</span>
        },
      },
    ],
    [productMap],
  )

  if (!canViewPrices) {
    return (
      <DashboardLayout title="Bảng giá" description="Browse pricing read model trong Catalog">
        <PermissionDeniedInline message="Bạn cần quyền catalog.price.read để xem bảng giá." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout title="Bảng giá" description="Browse toàn bộ product pricing read model với filter client-side.">
      <ReadonlyBanner message="Pricing đang publish theo read-first UX. Giá được load từ list endpoint rồi filter phía client theo effective state." />

      {!canViewProductLabels ? (
        <PermissionDeniedInline message="Bạn không có quyền catalog.product.read nên bảng giá sẽ hiển thị fallback productId thay vì tên sản phẩm." />
      ) : productsQuery.error ? (
        <div className="inline-banner inline-banner-warning" role="status">
          Không thể tải metadata sản phẩm. Bảng giá vẫn hiển thị nhưng nhãn sản phẩm sẽ fallback sang productId.
        </div>
      ) : null}

      <Card title="Bộ lọc bảng giá">
        <div className="field-grid">
          <Input
            label="Tìm theo product ID, mã hoặc tên"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="VD: 1001 hoặc COFFEE"
            value={search}
          />
          <Select
            label="Scope type"
            onChange={(event) => setScopeFilter(event.target.value as PriceScopeType | 'ALL')}
            options={scopeOptions}
            value={scopeFilter}
          />
          <Select
            label="Price type"
            onChange={(event) => setPriceTypeFilter(event.target.value as PriceType | 'ALL')}
            options={priceTypeOptions}
            value={priceTypeFilter}
          />
          <Select
            label="Effective state"
            onChange={(event) => setEffectiveFilter(event.target.value as PriceEffectiveState)}
            options={effectiveStateOptions}
            value={effectiveFilter}
          />
        </div>
        <div className="meta-grid">
          <span>Đã tải: {pricesQuery.data?.length ?? 0} dòng giá</span>
          <span>Kết quả sau lọc: {filteredRows.length}</span>
          <span>Tìm kiếm áp dụng trên dữ liệu đã tải</span>
        </div>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription={
          search.trim() || scopeFilter !== 'ALL' || priceTypeFilter !== 'ALL' || effectiveFilter !== 'ALL'
            ? 'Không có dòng giá nào khớp bộ lọc hiện tại.'
            : 'Catalog chưa có dòng giá nào để hiển thị.'
        }
        emptyTitle={
          search.trim() || scopeFilter !== 'ALL' || priceTypeFilter !== 'ALL' || effectiveFilter !== 'ALL'
            ? 'Không có dòng giá khớp bộ lọc'
            : 'Bảng giá đang trống'
        }
        error={pricesQuery.error ? getCatalogErrorMessage(pricesQuery.error, 'Không thể tải bảng giá.') : null}
        loading={pricesQuery.isLoading}
        loadingDescription="Đang tải toàn bộ price list từ gateway..."
        loadingTitle="Đang tải bảng giá"
        onRetry={() => void pricesQuery.refetch()}
        rowKey={(price) => price.id}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
