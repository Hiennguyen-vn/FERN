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

    await catalogApi.createProductCategory({ code: 'BEV' } as any)
    await catalogApi.updateProductCategory('BEV', { code: 'BEV' } as any)
    await catalogApi.createIngredientCategory({ code: 'ING' } as any)
    await catalogApi.updateIngredientCategory('ING', { code: 'ING' } as any)
    await catalogApi.createUnitOfMeasure({ code: 'G' } as any)
    await catalogApi.createUomConversion({ fromUomCode: 'G', toUomCode: 'KG' } as any)

    expect(postSpy).toHaveBeenCalledWith('/product-categories', { code: 'BEV' })
    expect(putSpy).toHaveBeenCalledWith('/product-categories/BEV', { code: 'BEV' })
    expect(postSpy).toHaveBeenCalledWith('/ingredient-categories', { code: 'ING' })
    expect(putSpy).toHaveBeenCalledWith('/ingredient-categories/ING', { code: 'ING' })
    expect(postSpy).toHaveBeenCalledWith('/units-of-measure', { code: 'G' })
    expect(postSpy).toHaveBeenCalledWith('/uom-conversions', { fromUomCode: 'G', toUomCode: 'KG' })
  })
})
