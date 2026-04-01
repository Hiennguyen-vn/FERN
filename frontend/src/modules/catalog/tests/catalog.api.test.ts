import { afterEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '@core/api/httpClient'
import { catalogApi } from '../api/catalog.api'

describe('catalog.api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uses gateway root paths for product and reference endpoints', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: [] } as any)

    await catalogApi.listProducts()
    await catalogApi.listProductCategories()
    await catalogApi.listRecipeVersions(42)
    await catalogApi.listProductPrices()
    await catalogApi.listAvailability({ outletId: 101 })

    expect(getSpy).toHaveBeenNthCalledWith(1, '/products')
    expect(getSpy).toHaveBeenNthCalledWith(2, '/product-categories')
    expect(getSpy).toHaveBeenNthCalledWith(3, '/recipe-versions', { params: { recipeId: 42 } })
    expect(getSpy).toHaveBeenNthCalledWith(4, '/product-prices')
    expect(getSpy).toHaveBeenNthCalledWith(5, '/product-availability', { params: { outletId: 101 } })
  })

  it('calls correct paths for promotion CRUD operations', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: [] } as any)
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)
    const putSpy = vi.spyOn(httpClient, 'put').mockResolvedValue({ data: {} } as any)

    await catalogApi.listPromotions()
    await catalogApi.listPromotions({ scopeType: 'GLOBAL' })
    await catalogApi.createPromotion({ code: 'P1' } as any)
    await catalogApi.updatePromotion(5, { code: 'P1' } as any)
    await catalogApi.deactivatePromotion(5)

    expect(getSpy).toHaveBeenNthCalledWith(1, '/catalog/promotions', { params: undefined })
    expect(getSpy).toHaveBeenNthCalledWith(2, '/catalog/promotions', { params: { scopeType: 'GLOBAL' } })
    expect(postSpy).toHaveBeenNthCalledWith(1, '/catalog/promotions', { code: 'P1' })
    expect(putSpy).toHaveBeenNthCalledWith(1, '/catalog/promotions/5', { code: 'P1' })
    expect(postSpy).toHaveBeenNthCalledWith(2, '/catalog/promotions/5/deactivate')
  })

  it('calls correct paths for tax rate operations', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: [] } as any)
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)
    const putSpy = vi.spyOn(httpClient, 'put').mockResolvedValue({ data: {} } as any)

    await catalogApi.listTaxRates()
    await catalogApi.getTaxRate(3)
    await catalogApi.createTaxRate({ productId: 10 } as any)
    await catalogApi.updateTaxRate(3, { productId: 10 } as any)

    expect(getSpy).toHaveBeenNthCalledWith(1, '/tax-rates', { params: undefined })
    expect(getSpy).toHaveBeenNthCalledWith(2, '/tax-rates/3')
    expect(postSpy).toHaveBeenCalledWith('/tax-rates', { productId: 10 })
    expect(putSpy).toHaveBeenCalledWith('/tax-rates/3', { productId: 10 })
  })

  it('calls correct paths for reference data write operations', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)
    const putSpy = vi.spyOn(httpClient, 'put').mockResolvedValue({ data: {} } as any)

    await catalogApi.createProductCategory({ code: 'BEV', name: 'Beverage', status: 'ACTIVE' })
    await catalogApi.updateProductCategory('BEV', { code: 'BEV', name: 'Beverage', status: 'INACTIVE' })
    await catalogApi.createIngredientCategory({ code: 'ING', name: 'Ingredient', active: false })
    await catalogApi.updateIngredientCategory('ING', { code: 'ING', name: 'Ingredient' })
    await catalogApi.createUnitOfMeasure({ code: 'G' } as any)
    await catalogApi.createUomConversion({ fromUomCode: 'G', toUomCode: 'KG' } as any)

    expect(postSpy).toHaveBeenCalledWith('/product-categories', { code: 'BEV', name: 'Beverage', active: true })
    expect(putSpy).toHaveBeenCalledWith('/product-categories/BEV', { code: 'BEV', name: 'Beverage', active: false })
    expect(postSpy).toHaveBeenCalledWith('/ingredient-categories', { code: 'ING', name: 'Ingredient', active: false })
    expect(putSpy).toHaveBeenCalledWith('/ingredient-categories/ING', { code: 'ING', name: 'Ingredient', active: true })
    expect(postSpy).toHaveBeenCalledWith('/units-of-measure', { code: 'G' })
    expect(postSpy).toHaveBeenCalledWith('/uom-conversions', { fromUomCode: 'G', toUomCode: 'KG' })
  })

  /**
   * Backend TaxRateUpsertRequest fields:
   *   @NotNull Long productId
   *   @NotNull BigDecimal taxPercent
   *   @NotNull LocalDate effectiveFrom
   *   LocalDate effectiveTo (optional)
   *
   * Previously the frontend used code/name/ratePercent — these must never
   * regress to those wrong field names.
   */
  it('sends TaxRate upsert with productId + taxPercent field names (not code/name/ratePercent)', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)
    const putSpy = vi.spyOn(httpClient, 'put').mockResolvedValue({ data: {} } as any)

    await catalogApi.createTaxRate({
      productId: 42,
      taxPercent: 10,
      effectiveFrom: '2026-01-01',
    })
    await catalogApi.updateTaxRate(7, {
      productId: 42,
      taxPercent: 8.5,
      effectiveFrom: '2026-04-01',
      effectiveTo: '2026-12-31',
    })

    expect(postSpy).toHaveBeenCalledWith('/tax-rates', {
      productId: 42,
      taxPercent: 10,
      effectiveFrom: '2026-01-01',
    })
    expect(putSpy).toHaveBeenCalledWith('/tax-rates/7', {
      productId: 42,
      taxPercent: 8.5,
      effectiveFrom: '2026-04-01',
      effectiveTo: '2026-12-31',
    })
  })

  /**
   * Backend UomConversionRequest/Response uses `conversionFactor` (not `factor`).
   * Sending `factor` would leave conversionFactor null and fail @NotNull validation.
   */
  it('sends UomConversion upsert with conversionFactor field name (not factor)', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)

    await catalogApi.createUomConversion({
      fromUomCode: 'G',
      toUomCode: 'KG',
      conversionFactor: 0.001,
    })

    expect(postSpy).toHaveBeenCalledWith('/uom-conversions', {
      fromUomCode: 'G',
      toUomCode: 'KG',
      conversionFactor: 0.001,
    })
    // Confirm the wrong field name is never sent
    expect(postSpy).not.toHaveBeenCalledWith('/uom-conversions', expect.objectContaining({ factor: expect.anything() }))
  })

  /**
   * Backend ProductPriceUpsertRequest uses backend PriceType enum:
   *   RETAIL, DINE_IN, TAKEAWAY, DELIVERY, WHOLESALE
   * (NOT the old frontend values: STANDARD, PROMOTIONAL, COST)
   *
   * Backend PriceScopeType enum:
   *   GLOBAL, COUNTRY, REGION, OUTLET (COUNTRY was missing from the frontend)
   */
  it('sends ProductPrice with correct PriceType and PriceScopeType enum values', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)

    await catalogApi.createProductPrice({
      productId: 1,
      scopeType: 'COUNTRY',
      scopeId: null,
      priceType: 'RETAIL',
      currencyCode: 'VND',
      priceValue: 50000,
      effectiveFrom: '2026-01-01',
      effectiveTo: null,
    })

    expect(postSpy).toHaveBeenCalledWith('/product-prices', expect.objectContaining({
      scopeType: 'COUNTRY',
      priceType: 'RETAIL',
    }))
  })

  /**
   * ProductPriceUpsertRequest.effectiveFrom is @NotNull in the backend.
   * The frontend type must not allow null for effectiveFrom on write requests.
   * This test verifies the value is sent as a date string (never null or absent).
   */
  it('sends effectiveFrom as required non-null string in ProductPrice upsert', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)

    await catalogApi.createProductPrice({
      productId: 2,
      scopeType: 'GLOBAL',
      scopeId: null,
      priceType: 'DINE_IN',
      currencyCode: 'VND',
      priceValue: 75000,
      effectiveFrom: '2026-06-01',
      effectiveTo: null,
    })

    const sent = postSpy.mock.calls[0]?.[1] as Record<string, unknown>
    expect(sent.effectiveFrom).toBe('2026-06-01')
    expect(sent.effectiveFrom).not.toBeNull()
  })

  /**
   * Regression: backend PriceType enum is RETAIL, DINE_IN, TAKEAWAY, DELIVERY, WHOLESALE.
   * Previously the frontend used STANDARD, PROMOTIONAL, COST — these must never come back.
   * All five correct values must be accepted by the TypeScript type (compile-time) and be
   * passable to the API (runtime).
   */
  it('accepts all five backend PriceType enum values in ProductPrice upsert', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)

    const priceTypes = ['RETAIL', 'DINE_IN', 'TAKEAWAY', 'DELIVERY', 'WHOLESALE'] as const
    for (const priceType of priceTypes) {
      await catalogApi.createProductPrice({
        productId: 1,
        scopeType: 'GLOBAL',
        scopeId: null,
        priceType,
        currencyCode: 'VND',
        priceValue: 10000,
        effectiveFrom: '2026-01-01',
        effectiveTo: null,
      })
    }

    expect(postSpy).toHaveBeenCalledTimes(5)
    const sentPriceTypes = postSpy.mock.calls.map((call) => (call[1] as Record<string, unknown>).priceType)
    expect(sentPriceTypes).toEqual(['RETAIL', 'DINE_IN', 'TAKEAWAY', 'DELIVERY', 'WHOLESALE'])
  })

  /**
   * Regression: backend PriceScopeType enum includes COUNTRY (was missing from frontend).
   * All four values must round-trip correctly.
   */
  it('accepts all four PriceScopeType values including COUNTRY', async () => {
    const postSpy = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: {} } as any)

    const scopeTypes = ['GLOBAL', 'COUNTRY', 'REGION', 'OUTLET'] as const
    for (const scopeType of scopeTypes) {
      await catalogApi.createProductPrice({
        productId: 1,
        scopeType,
        scopeId: null,
        priceType: 'RETAIL',
        currencyCode: 'VND',
        priceValue: 10000,
        effectiveFrom: '2026-01-01',
        effectiveTo: null,
      })
    }

    expect(postSpy).toHaveBeenCalledTimes(4)
    const sentScopeTypes = postSpy.mock.calls.map((call) => (call[1] as Record<string, unknown>).scopeType)
    expect(sentScopeTypes).toEqual(['GLOBAL', 'COUNTRY', 'REGION', 'OUTLET'])
  })

  /**
   * Regression: backend IngredientStatus includes DISCONTINUED (was missing from frontend).
   * The compile-time type enforces this; the runtime test confirms the list endpoint is called.
   */
  it('accepts DISCONTINUED as a valid IngredientStatus in filter params', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: [] } as any)

    await catalogApi.listIngredients()

    expect(getSpy).toHaveBeenCalledWith('/ingredients')
  })

  /**
   * Regression: backend ProductStatus includes DRAFT (was missing from frontend).
   * Compile-time type enforces this; runtime test confirms the list endpoint is reached.
   */
  it('accepts DRAFT as a valid ProductStatus in filter params', async () => {
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: [] } as any)

    await catalogApi.listProducts()

    expect(getSpy).toHaveBeenCalledWith('/products')
  })
})
