import { httpClient } from '@core/api/httpClient'
import type {
  Category,
  Ingredient,
  IngredientUpsertRequest,
  Product,
  ProductAvailability,
  ProductAvailabilityUpsertRequest,
  ProductPrice,
  ProductPriceUpsertRequest,
  ProductUpsertRequest,
  Recipe,
  RecipeUpsertRequest,
  RecipeVersion,
  RecipeVersionUpsertRequest,
  UnitOfMeasure,
  UomConversion,
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

// ─── Products ─────────────────────────────────────────────────────────────────
export const catalogApi = {
  // Products
  listProducts: () =>
    httpClient.get<Product[]>(PRODUCT_BASE).then((r) => r.data),
  getProduct: (id: number) =>
    httpClient.get<Product>(`${PRODUCT_BASE}/${id}`).then((r) => r.data),
  createProduct: (body: ProductUpsertRequest) =>
    httpClient.post<Product>(PRODUCT_BASE, body).then((r) => r.data),
  updateProduct: (id: number, body: ProductUpsertRequest) =>
    httpClient.put<Product>(`${PRODUCT_BASE}/${id}`, body).then((r) => r.data),

  // Categories
  listProductCategories: () =>
    httpClient.get<Category[]>(PRODUCT_CATEGORY_BASE).then((r) => r.data),
  listIngredientCategories: () =>
    httpClient.get<Category[]>(INGREDIENT_CATEGORY_BASE).then((r) => r.data),

  // Units of measure
  listUnitsOfMeasure: () =>
    httpClient.get<UnitOfMeasure[]>(UNIT_OF_MEASURE_BASE).then((r) => r.data),
  listUomConversions: () =>
    httpClient.get<UomConversion[]>(UOM_CONVERSION_BASE).then((r) => r.data),

  // Ingredients
  listIngredients: () =>
    httpClient.get<Ingredient[]>(INGREDIENT_BASE).then((r) => r.data),
  getIngredient: (id: number) =>
    httpClient.get<Ingredient>(`${INGREDIENT_BASE}/${id}`).then((r) => r.data),
  createIngredient: (body: IngredientUpsertRequest) =>
    httpClient.post<Ingredient>(INGREDIENT_BASE, body).then((r) => r.data),
  updateIngredient: (id: number, body: IngredientUpsertRequest) =>
    httpClient.put<Ingredient>(`${INGREDIENT_BASE}/${id}`, body).then((r) => r.data),

  // Recipes
  listRecipes: () =>
    httpClient.get<Recipe[]>(RECIPE_BASE).then((r) => r.data),
  getRecipe: (id: number) =>
    httpClient.get<Recipe>(`${RECIPE_BASE}/${id}`).then((r) => r.data),
  createRecipe: (body: RecipeUpsertRequest) =>
    httpClient.post<Recipe>(RECIPE_BASE, body).then((r) => r.data),

  // Recipe versions
  listRecipeVersions: (recipeId: number) =>
    httpClient.get<RecipeVersion[]>(RECIPE_VERSION_BASE, { params: { recipeId } }).then((r) => r.data),
  getRecipeVersion: (id: number) =>
    httpClient.get<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}`).then((r) => r.data),
  createRecipeVersion: (body: RecipeVersionUpsertRequest) =>
    httpClient.post<RecipeVersion>(RECIPE_VERSION_BASE, body).then((r) => r.data),
  updateRecipeVersion: (id: number, body: RecipeVersionUpsertRequest) =>
    httpClient.put<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}`, body).then((r) => r.data),
  activateRecipeVersion: (id: number) =>
    httpClient.post<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}/activate`).then((r) => r.data),
  archiveRecipeVersion: (id: number) =>
    httpClient.post<RecipeVersion>(`${RECIPE_VERSION_BASE}/${id}/archive`).then((r) => r.data),

  // Pricing
  listProductPrices: () =>
    httpClient.get<ProductPrice[]>(PRODUCT_PRICE_BASE).then((r) => r.data),
  createProductPrice: (body: ProductPriceUpsertRequest) =>
    httpClient.post<ProductPrice>(PRODUCT_PRICE_BASE, body).then((r) => r.data),
  updateProductPrice: (id: number, body: ProductPriceUpsertRequest) =>
    httpClient.put<ProductPrice>(`${PRODUCT_PRICE_BASE}/${id}`, body).then((r) => r.data),

  // Availability
  listAvailability: (params?: { productId?: number; outletId?: number }) =>
    httpClient.get<ProductAvailability[]>(PRODUCT_AVAILABILITY_BASE, { params }).then((r) => r.data),
  upsertAvailability: (body: ProductAvailabilityUpsertRequest) =>
    httpClient.put<ProductAvailability>(PRODUCT_AVAILABILITY_BASE, body).then((r) => r.data),
}
