// ─── Enums (mirror backend domain enums) ─────────────────────────────────────
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED'
export type IngredientStatus = 'ACTIVE' | 'INACTIVE'
export type RecipeVersionStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
export type PriceScopeType = 'GLOBAL' | 'REGION' | 'OUTLET'
export type PriceType = 'STANDARD' | 'PROMOTIONAL' | 'COST'
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

export interface UomConversion {
  fromUomCode: string
  toUomCode: string
  factor: number
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
export interface TaxRate {
  id: number
  code: string
  name: string
  ratePercent: number
  countryCode: string | null
  regionId: number | null
  effectiveFrom: string | null
  effectiveTo: string | null
  status: string
}

export interface TaxRateUpsertRequest {
  code: string
  name: string
  ratePercent: number
  countryCode?: string | null
  regionId?: number | null
  effectiveFrom?: string | null
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
}

export interface UnitOfMeasureUpsertRequest {
  code: string
  name: string
  symbol: string
}

export interface UomConversionUpsertRequest {
  fromUomCode: string
  toUomCode: string
  factor: number
}
