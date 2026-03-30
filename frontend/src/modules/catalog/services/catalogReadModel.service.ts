import type { Product, ProductAvailability, ProductPrice } from '../model/catalog.types'

export type PriceEffectiveState = 'ALL' | 'CURRENT' | 'UPCOMING' | 'EXPIRED'

const dateFormatter = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' })

export function normalizeText(value: string | null | undefined) {
  return (value ?? '').trim().toLowerCase()
}

export function matchesSearch(values: Array<string | number | null | undefined>, search: string) {
  const query = normalizeText(search)

  if (!query) {
    return true
  }

  return values.some((value) => normalizeText(String(value ?? '')).includes(query))
}

export function buildProductMap(products: Product[]) {
  return new Map(products.map((product) => [product.id, product] as const))
}

export function formatProductLabel(product: Product | undefined, fallbackProductId: number) {
  if (!product) {
    return `#${fallbackProductId}`
  }

  return `${product.code} · ${product.name}`
}

export function getPriceEffectiveState(price: ProductPrice, referenceDate = new Date()): Exclude<PriceEffectiveState, 'ALL'> {
  const effectiveFrom = price.effectiveFrom ? new Date(price.effectiveFrom) : null
  const effectiveTo = price.effectiveTo ? new Date(price.effectiveTo) : null

  if (effectiveFrom && effectiveFrom.getTime() > referenceDate.getTime()) {
    return 'UPCOMING'
  }

  if (effectiveTo && effectiveTo.getTime() < referenceDate.getTime()) {
    return 'EXPIRED'
  }

  return 'CURRENT'
}

export function formatDateLabel(value: string | null) {
  if (!value) {
    return 'Open-ended'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return dateFormatter.format(date)
}

export function formatDateRange(start: string | null, end: string | null) {
  return `${formatDateLabel(start)} → ${formatDateLabel(end)}`
}

export function formatScopeLabel(scopeType: ProductPrice['scopeType'], scopeId: number | null) {
  return scopeId ? `${scopeType} #${scopeId}` : scopeType
}

export function formatCurrencyAmount(amount: number, currencyCode: string) {
  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currencyCode,
      maximumFractionDigits: 0,
    }).format(amount)
  } catch {
    return `${amount.toLocaleString('vi-VN')} ${currencyCode}`
  }
}

export function sortAvailabilityRows(rows: ProductAvailability[], selectedOutletId: number | null) {
  if (!selectedOutletId) {
    return [...rows].sort((left, right) => left.outletId - right.outletId)
  }

  return [...rows].sort((left, right) => {
    if (left.outletId === selectedOutletId && right.outletId !== selectedOutletId) {
      return -1
    }

    if (right.outletId === selectedOutletId && left.outletId !== selectedOutletId) {
      return 1
    }

    return left.outletId - right.outletId
  })
}
