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
})
