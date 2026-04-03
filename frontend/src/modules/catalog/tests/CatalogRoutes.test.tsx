import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  AvailabilityPage,
  IngredientsPage,
  PricingPage,
  ProductDetailPage,
  ProductsPage,
  RecipesPage,
} from '../routes/catalogRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useProducts: vi.fn(),
  useProduct: vi.fn(),
  useDeactivateProduct: vi.fn(),
  useProductCategories: vi.fn(),
  useIngredientCategories: vi.fn(),
  useIngredients: vi.fn(),
  useRecipes: vi.fn(),
  useRecipeVersions: vi.fn(),
  useProductPrices: vi.fn(),
  useAvailability: vi.fn(),
}))

vi.mock('../hooks/useProducts', () => ({
  useProducts: mocks.useProducts,
  useProduct: mocks.useProduct,
  useDeactivateProduct: mocks.useDeactivateProduct,
  useProductCategories: mocks.useProductCategories,
  useIngredientCategories: mocks.useIngredientCategories,
}))

vi.mock('../hooks/useIngredients', () => ({
  useIngredients: mocks.useIngredients,
}))

vi.mock('../hooks/useRecipes', () => ({
  useRecipes: mocks.useRecipes,
  useRecipeVersions: mocks.useRecipeVersions,
}))

vi.mock('../hooks/usePricing', () => ({
  useProductPrices: mocks.useProductPrices,
  useAvailability: mocks.useAvailability,
}))

function CatalogRoutesHarness() {
  return (
    <Routes>
      <Route path="/catalog" element={<LazyRouteBoundary moduleName="Catalog" label="Loading catalog" />}>
        <Route path="products" element={<ProductsPage />} />
        <Route path="products/:productId" element={<ProductDetailPage />} />
        <Route path="ingredients" element={<IngredientsPage />} />
        <Route path="recipes" element={<RecipesPage />} />
        <Route path="pricing" element={<PricingPage />} />
        <Route path="availability" element={<AvailabilityPage />} />
      </Route>
    </Routes>
  )
}

describe('Catalog route group', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.catalog.productRead,
          permissionConstants.catalog.ingredientRead,
          permissionConstants.catalog.recipeRead,
          permissionConstants.catalog.priceRead,
        ],
      },
    })

    mocks.useProducts.mockReturnValue({
      data: [{ id: 1, code: 'CF-001', name: 'Iced Coffee', categoryCode: 'BEVERAGE', status: 'ACTIVE', imageUrl: null, description: 'Cold brew' }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useProduct.mockReturnValue({
      data: { id: 1, code: 'CF-001', name: 'Iced Coffee', categoryCode: 'BEVERAGE', status: 'ACTIVE', imageUrl: null, description: 'Cold brew' },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useDeactivateProduct.mockReturnValue({
      mutate: vi.fn(),
      mutateAsync: vi.fn(),
      isPending: false,
      error: null,
    })
    mocks.useProductCategories.mockReturnValue({ data: [], error: null })
    mocks.useIngredientCategories.mockReturnValue({ data: [], error: null })
    mocks.useIngredients.mockReturnValue({ data: [], error: null, isLoading: false, refetch: vi.fn() })
    mocks.useRecipes.mockReturnValue({ data: [{ id: 10, productId: 1, recipeCode: 'REC-COFFEE', description: 'Base drink' }], error: null, isLoading: false, refetch: vi.fn() })
    mocks.useRecipeVersions.mockReturnValue({ data: [], error: null, isLoading: false, refetch: vi.fn() })
    mocks.useProductPrices.mockReturnValue({ data: [], error: null, isLoading: false, refetch: vi.fn() })
    mocks.useAvailability.mockReturnValue({ data: [], error: null, isLoading: false, refetch: vi.fn() })
  })

  it.each([
    ['/catalog/products', 'Product Master Catalog'],
    ['/catalog/products/1', 'Product Profile'],
    ['/catalog/ingredients', 'Nguyên liệu'],
    ['/catalog/recipes', 'Công thức'],
    ['/catalog/pricing', 'Bảng giá'],
    ['/catalog/availability', 'Outlet availability'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<CatalogRoutesHarness />, { route })

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
