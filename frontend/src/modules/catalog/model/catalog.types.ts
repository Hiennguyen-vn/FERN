// ─── Enums (mirror backend domain enums exactly) ─────────────────────────────
// Backend reference: com.fern.catalogservice.domain.*
export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED'
export type IngredientStatus = 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED'
export type RecipeVersionStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
// Backend PriceScopeType: GLOBAL, COUNTRY, REGION, OUTLET
export type PriceScopeType = 'GLOBAL' | 'COUNTRY' | 'REGION' | 'OUTLET'
// Backend PriceType: RETAIL, DINE_IN, TAKEAWAY, DELIVERY, WHOLESALE
export type PriceType = 'RETAIL' | 'DINE_IN' | 'TAKEAWAY' | 'DELIVERY' | 'WHOLESALE'
export type PromotionStatus = 'ACTIVE' | 'INACTIVE' | 'EXPIRED'
export type PromotionType = 'PERCENT_DISCOUNT' | 'FIXED_DISCOUNT' | 'BUY_X_GET_Y' | 'FREE_ITEM'

// ─── Reference data ───────────────────────────────────────────────────────────
export interface Category {
  code: string
  name: string
  description?: string | null
  active?: boolean | null
}

export interface UnitOfMeasure {
  code: string
  name: string
  symbol: string
}

/**
 * Backend UomConversionResponse: fromUomCode, toUomCode, conversionFactor (BigDecimal)
 * Note: the response field is conversionFactor, not factor.
 */
export interface UomConversion {
  fromUomCode: string
  toUomCode: string
  conversionFactor: number
}

// ─── Product ──────────────────────────────────────────────────────────────────
export interface Product {
  id: number
  code: string
  name: string
  categoryCode: string | null
  status: ProductStatus
  imageUrl: string | null
  description: string | null
}

export interface ProductUpsertRequest {
  code: string
  name: string
  categoryCode: string | null
  status: ProductStatus
  imageUrl: string | null
  description: string | null
}

// ─── Ingredient ───────────────────────────────────────────────────────────────
export interface Ingredient {
  id: number
  code: string
  name: string
  categoryCode: string | null
  baseUomCode: string
  minStockLevel: number | null
  maxStockLevel: number | null
  status: IngredientStatus
}

export interface IngredientUpsertRequest {
  code: string
  name: string
  categoryCode: string | null
  baseUomCode: string
  minStockLevel: number | null
  maxStockLevel: number | null
  status: IngredientStatus
}

// ─── Recipe ───────────────────────────────────────────────────────────────────
export interface Recipe {
  id: number
  productId: number
  recipeCode: string
  description: string | null
}

export interface RecipeUpsertRequest {
  productId: number
  recipeCode: string
  description: string | null
}

export interface RecipeVersionIngredient {
  id: number
  recipeVersionId: number
  ingredientId: number
  ingredientCode: string
  ingredientName: string
  qty: number
  uomCode: string
}

export interface RecipeVersion {
  id: number
  recipeId: number
  versionNo: string
  yieldQty: number
  yieldUomCode: string
  status: RecipeVersionStatus
  effectiveFrom: string | null
  effectiveTo: string | null
  ingredients: RecipeVersionIngredient[]
}

export interface RecipeVersionUpsertRequest {
  recipeId: number
  versionNo: string
  yieldQty: number
  yieldUomCode: string
  status: RecipeVersionStatus
  effectiveFrom: string | null
  effectiveTo: string | null
  ingredients: {
    ingredientId: number
    qty: number
    uomCode: string
  }[]
}

// ─── Pricing ─────────────────────────────────────────────────────────────────
export interface ProductPrice {
  id: number
  productId: number
  scopeType: PriceScopeType
  scopeId: number | null
  priceType: PriceType
  currencyCode: string
  priceValue: number
  effectiveFrom: string | null
  effectiveTo: string | null
}

export interface ProductPriceUpsertRequest {
  productId: number
  scopeType: PriceScopeType
  scopeId: number | null
  priceType: PriceType
  currencyCode: string
  priceValue: number
  effectiveFrom: string | null
  effectiveTo: string | null
}

// ─── Availability ─────────────────────────────────────────────────────────────
export interface ProductAvailability {
  productId: number
  outletId: number
  available: boolean
}

export interface ProductAvailabilityUpsertRequest {
  productId: number
  outletId: number
  available: boolean
}

// ─── Tax rates ────────────────────────────────────────────────────────────────
/**
 * Backend TaxRateResponse fields:
 *   Long id, Long productId, BigDecimal taxPercent,
 *   LocalDate effectiveFrom, LocalDate effectiveTo
 *
 * Note: tax rates in this system are per-product effective-date entries,
 * not global named rates. There is no code/name/ratePercent/countryCode.
 */
export interface TaxRate {
  id: number
  productId: number
  taxPercent: number
  effectiveFrom: string
  effectiveTo: string | null
}

/**
 * Backend TaxRateUpsertRequest:
 *   @NotNull Long productId
 *   @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal taxPercent
 *   @NotNull LocalDate effectiveFrom
 *   LocalDate effectiveTo (optional)
 */
export interface TaxRateUpsertRequest {
  productId: number
  taxPercent: number
  effectiveFrom: string
  effectiveTo?: string | null
}

// ─── Promotions ───────────────────────────────────────────────────────────────
export interface Promotion {
  id: number
  code: string
  name: string
  description: string | null
  promotionType: PromotionType | string
  discountPercent: number | null
  discountAmount: number | null
  scopeType: PriceScopeType | string
  scopeId: number | null
  minOrderAmount: number | null
  maxUsageTotal: number | null
  effectiveFrom: string
  effectiveTo: string | null
  status: PromotionStatus | string
  createdByUserId: number | null
  updatedByUserId: number | null
  createdAt: string
  updatedAt: string
}

export interface PromotionUpsertRequest {
  code: string
  name: string
  description?: string | null
  promotionType: PromotionType | string
  discountPercent?: number | null
  discountAmount?: number | null
  scopeType: PriceScopeType | string
  scopeId?: number | null
  minOrderAmount?: number | null
  maxUsageTotal?: number | null
  effectiveFrom: string
  effectiveTo?: string | null
}

// ─── Reference data write requests ───────────────────────────────────────────
export interface CategoryUpsertRequest {
  code: string
  name: string
  description?: string | null
  active?: boolean
  status?: 'ACTIVE' | 'INACTIVE'
}

export interface UnitOfMeasureUpsertRequest {
  code: string
  name: string
  symbol: string
}

/**
 * Backend UomConversionRequest: fromUomCode @NotBlank, toUomCode @NotBlank,
 *   conversionFactor @NotNull @DecimalMin("0.00000001")
 */
export interface UomConversionUpsertRequest {
  fromUomCode: string
  toUomCode: string
  conversionFactor: number
}
