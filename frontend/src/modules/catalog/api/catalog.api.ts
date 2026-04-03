import { gatewayClient } from '@core/api/gatewayClient'
import type {
  Category,
  CategoryUpsertRequest,
  Ingredient,
  IngredientUpsertRequest,
  Product,
  ProductAvailability,
  ProductAvailabilityUpsertRequest,
  ProductPrice,
  ProductPriceUpsertRequest,
  ProductUpsertRequest,
  Promotion,
  PromotionUpsertRequest,
  Recipe,
  RecipeUpsertRequest,
  RecipeVersion,
  RecipeVersionUpsertRequest,
  TaxRate,
  TaxRateUpsertRequest,
  UnitOfMeasure,
  UomConversion,
  UomConversionUpsertRequest,
  UnitOfMeasureUpsertRequest,
} from '../model/catalog.types'

const PRODUCT_BASE = '/products'
const PRODUCT_CATEGORY_BASE = '/product-categories'
const INGREDIENT_CATEGORY_BASE = '/ingredient-categories'
const UNIT_OF_MEASURE_BASE = '/units-of-measure'
const UOM_CONVERSION_BASE = '/uom-conversions'
const INGREDIENT_BASE = '/ingredients'
const RECIPE_BASE = '/recipes'
const RECIPE_VERSION_BASE = '/recipe-versions'
const PRODUCT_PRICE_BASE = '/product-prices'
const PRODUCT_AVAILABILITY_BASE = '/product-availability'
const TAX_RATE_BASE = '/tax-rates'
const PROMOTION_BASE = '/catalog/promotions'

function normalizeCategoryPayload(body: CategoryUpsertRequest) {
  const { status, active, ...rest } = body

  return {
    ...rest,
    active: active ?? (status ? status === 'ACTIVE' : true),
  }
}

// ─── Products ─────────────────────────────────────────────────────────────────
export const catalogApi = {
  // Products
  listProducts: () =>
    gatewayClient.get<Product[]>(PRODUCT_BASE).then((r) => r.data),
  getProduct: (id: number) =>
    gatewayClient.get<Product>(`${PRODUCT_BASE}/${id}`).then((r) => r.data),
  createProduct: (body: ProductUpsertRequest) =>
    gatewayClient.post<Product>(PRODUCT_BASE, body).then((r) => r.data),
  updateProduct: (id: number, body: ProductUpsertRequest) =>
    gatewayClient.put<Product>(`${PRODUCT_BASE}/${id}`, body).then((r) => r.data),
  deactivateProduct: (id: number) =>
    gatewayClient.post<Product>(`${PRODUCT_BASE}/${id}/deactivate`).then((r) => r.data),

  // Categories
  listProductCategories: () =>
    gatewayClient.get<Category[]>(PRODUCT_CATEGORY_BASE).then((r) => r.data),
  createProductCategory: (body: CategoryUpsertRequest) =>
    gatewayClient.post<Category>(PRODUCT_CATEGORY_BASE, normalizeCategoryPayload(body)).then((r) => r.data),
  updateProductCategory: (code: string, body: CategoryUpsertRequest) =>
    gatewayClient.put<Category>(`${PRODUCT_CATEGORY_BASE}/${code}`, normalizeCategoryPayload(body)).then((r) => r.data),
  listIngredientCategories: () =>
    gatewayClient.get<Category[]>(INGREDIENT_CATEGORY_BASE).then((r) => r.data),
  createIngredientCategory: (body: CategoryUpsertRequest) =>
    gatewayClient.post<Category>(INGREDIENT_CATEGORY_BASE, normalizeCategoryPayload(body)).then((r) => r.data),
  updateIngredientCategory: (code: string, body: CategoryUpsertRequest) =>
    gatewayClient.put<Category>(`${INGREDIENT_CATEGORY_BASE}/${code}`, normalizeCategoryPayload(body)).then((r) => r.data),

  // Units of measure
  listUnitsOfMeasure: () =>
    gatewayClient.get<UnitOfMeasure[]>(UNIT_OF_MEASURE_BASE).then((r) => r.data),
  createUnitOfMeasure: (body: UnitOfMeasureUpsertRequest) =>
    gatewayClient.post<UnitOfMeasure>(UNIT_OF_MEASURE_BASE, body).then((r) => r.data),
  listUomConversions: () =>
    gatewayClient.get<UomConversion[]>(UOM_CONVERSION_BASE).then((r) => r.data),
  createUomConversion: (body: UomConversionUpsertRequest) =>
    gatewayClient.post<UomConversion>(UOM_CONVERSION_BASE, body).then((r) => r.data),

  // Ingredients
  listIngredients: () =>
    gatewayClient.get<Ingredient[]>(INGREDIENT_BASE).then((r) => r.data),
  getIngredient: (id: number) =>
    gatewayClient.get<Ingredient>(`${INGREDIENT_BASE}/${id}`).then((r) => r.data),
  createIngredient: (body: IngredientUpsertRequest) =>
    gatewayClient.post<Ingredient>(INGREDIENT_BASE, body).then((r) => r.data),
  updateIngredient: (id: number, body: IngredientUpsertRequest) =>
    gatewayClient.put<Ingredient>(`${INGREDIENT_BASE}/${id}`, body).then((r) => r.data),

  // Recipes
  listRecipes: () =>
    gatewayClient.get<Recipe[]>(RECIPE_BASE).then((r) => r.data),
  getRecipe: (id: number) =>
    gatewayClient.get<Recipe>(`${RECIPE_BASE}/${id}`).then((r) => r.data),
  createRecipe: (body: RecipeUpsertRequest) =>
    gatewayClient.post<Recipe>(RECIPE_BASE, body).then((r) => r.data),

  // Recipe versions
  listRecipeVersions: (recipeId: number) =>
    gatewayClient.get<RecipeVersion[]>(RECIPE_VERSION_BASE, { params: { recipeId } }).then((r) => r.data),
  getRecipeVersion: (id: number) =>
    gatewayClient.get<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}`).then((r) => r.data),
  createRecipeVersion: (body: RecipeVersionUpsertRequest) =>
    gatewayClient.post<RecipeVersion>(RECIPE_VERSION_BASE, body).then((r) => r.data),
  updateRecipeVersion: (id: number, body: RecipeVersionUpsertRequest) =>
    gatewayClient.put<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}`, body).then((r) => r.data),
  activateRecipeVersion: (id: number) =>
    gatewayClient.post<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}/activate`).then((r) => r.data),
  archiveRecipeVersion: (id: number) =>
    gatewayClient.post<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}/archive`).then((r) => r.data),

  // Pricing
  listProductPrices: () =>
    gatewayClient.get<ProductPrice[]>(PRODUCT_PRICE_BASE).then((r) => r.data),
  createProductPrice: (body: ProductPriceUpsertRequest) =>
    gatewayClient.post<ProductPrice>(PRODUCT_PRICE_BASE, body).then((r) => r.data),
  updateProductPrice: (id: number, body: ProductPriceUpsertRequest) =>
    gatewayClient.put<ProductPrice>(`${PRODUCT_PRICE_BASE}/${id}`, body).then((r) => r.data),

  // Availability
  listAvailability: (params?: { productId?: number; outletId?: number }) =>
    gatewayClient.get<ProductAvailability[]>(PRODUCT_AVAILABILITY_BASE, { params }).then((r) => r.data),
  upsertAvailability: (body: ProductAvailabilityUpsertRequest) =>
    gatewayClient.put<ProductAvailability>(PRODUCT_AVAILABILITY_BASE, body).then((r) => r.data),

  // Tax rates
  listTaxRates: (params?: { countryCode?: string; regionId?: number }) =>
    gatewayClient.get<TaxRate[]>(TAX_RATE_BASE, { params }).then((r) => r.data),
  getTaxRate: (id: number) =>
    gatewayClient.get<TaxRate>(`${TAX_RATE_BASE}/${id}`).then((r) => r.data),
  createTaxRate: (body: TaxRateUpsertRequest) =>
    gatewayClient.post<TaxRate>(TAX_RATE_BASE, body).then((r) => r.data),
  updateTaxRate: (id: number, body: TaxRateUpsertRequest) =>
    gatewayClient.put<TaxRate>(`${TAX_RATE_BASE}/${id}`, body).then((r) => r.data),

  // Promotions
  listPromotions: (params?: { scopeType?: string; scopeId?: number }) =>
    gatewayClient.get<Promotion[]>(PROMOTION_BASE, { params }).then((r) => r.data),
  createPromotion: (body: PromotionUpsertRequest) =>
    gatewayClient.post<Promotion>(PROMOTION_BASE, body).then((r) => r.data),
  updatePromotion: (id: number, body: PromotionUpsertRequest) =>
    gatewayClient.put<Promotion>(`${PROMOTION_BASE}/${id}`, body).then((r) => r.data),
  deactivatePromotion: (id: number) =>
    gatewayClient.post<Promotion>(`${PROMOTION_BASE}/${id}/deactivate`).then((r) => r.data),
}
