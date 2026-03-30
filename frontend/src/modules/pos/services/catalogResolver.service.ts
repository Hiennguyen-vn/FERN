import type {
  CartDraft,
  PosCatalogFilters,
  Product,
  ProductAvailability,
  ProductPrice,
  ResolvedMenuItem,
} from '../model/pos.types'

function isPriceEffective(price: ProductPrice, businessDate: string) {
  const from = price.effectiveFrom ?? '0000-01-01'
  const to = price.effectiveTo ?? '9999-12-31'

  return from <= businessDate && to >= businessDate
}

function matchesScope(price: ProductPrice, filters: PosCatalogFilters) {
  switch (price.scopeType) {
    case 'OUTLET':
      return price.scopeId === filters.outletId
    case 'REGION':
      return filters.regionId !== undefined && price.scopeId === filters.regionId
    case 'COUNTRY':
      return false
    case 'GLOBAL':
    default:
      return true
  }
}

function scopeRank(scopeType: ProductPrice['scopeType']) {
  switch (scopeType) {
    case 'OUTLET':
      return 0
    case 'REGION':
      return 1
    case 'COUNTRY':
      return 2
    case 'GLOBAL':
    default:
      return 3
  }
}

function priceTypeRank(priceType: ProductPrice['priceType'], orderType: PosCatalogFilters['orderType']) {
  if (priceType === orderType) {
    return 0
  }

  if (priceType === 'RETAIL') {
    return 1
  }

  return 99
}

function sortCandidates(left: ProductPrice, right: ProductPrice, filters: PosCatalogFilters) {
  const typeDelta = priceTypeRank(left.priceType, filters.orderType) - priceTypeRank(right.priceType, filters.orderType)
  if (typeDelta !== 0) {
    return typeDelta
  }

  const scopeDelta = scopeRank(left.scopeType) - scopeRank(right.scopeType)
  if (scopeDelta !== 0) {
    return scopeDelta
  }

  const fromLeft = left.effectiveFrom ?? ''
  const fromRight = right.effectiveFrom ?? ''
  if (fromLeft !== fromRight) {
    return fromLeft > fromRight ? -1 : 1
  }

  return right.id - left.id
}

export function resolveMenuItems(
  prices: ProductPrice[],
  products: Product[],
  availability: ProductAvailability[],
  filters: PosCatalogFilters,
) {
  const availableIds = new Set(
    availability
      .filter((item) => item.outletId === filters.outletId && item.available)
      .map((item) => item.productId),
  )

  return products
    .filter((product) => product.status === 'ACTIVE' && availableIds.has(product.id))
    .map<ResolvedMenuItem | null>((product) => {
      const selectedPrice = prices
        .filter(
          (price) =>
            price.productId === product.id &&
            isPriceEffective(price, filters.businessDate) &&
            matchesScope(price, filters) &&
            (price.priceType === filters.orderType || price.priceType === 'RETAIL'),
        )
        .sort((left, right) => sortCandidates(left, right, filters))[0]

      if (!selectedPrice) {
        return null
      }

      return {
        productId: product.id,
        code: product.code,
        name: product.name,
        categoryCode: product.categoryCode,
        description: product.description,
        imageUrl: product.imageUrl,
        currencyCode: selectedPrice.currencyCode,
        priceValue: selectedPrice.priceValue,
        priceType: selectedPrice.priceType,
        scopeType: selectedPrice.scopeType,
      }
    })
    .filter((item): item is ResolvedMenuItem => item !== null)
    .sort((left, right) => {
      const categoryCompare = left.categoryCode.localeCompare(right.categoryCode)
      if (categoryCompare !== 0) {
        return categoryCompare
      }

      return left.name.localeCompare(right.name)
    })
}

export function calculateCartEstimatedTotal(draft: CartDraft) {
  return draft.items.reduce((total, item) => total + Number(item.qty || '0') * Number(item.unitPrice || '0'), 0)
}
