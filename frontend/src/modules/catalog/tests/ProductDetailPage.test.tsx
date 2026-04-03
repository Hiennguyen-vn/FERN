import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { ProductDetailPage } from '../routes/ProductDetailPage'

const mocks = vi.hoisted(() => ({
  useProduct: vi.fn(),
  useDeactivateProduct: vi.fn(),
  useRecipes: vi.fn(),
  useRecipeVersions: vi.fn(),
  useProductPrices: vi.fn(),
  useAvailability: vi.fn(),
}))

vi.mock('../hooks/useProducts', () => ({
  useProduct: mocks.useProduct,
  useDeactivateProduct: mocks.useDeactivateProduct,
}))

vi.mock('../hooks/useRecipes', () => ({
  useRecipes: mocks.useRecipes,
  useRecipeVersions: mocks.useRecipeVersions,
}))

vi.mock('../hooks/usePricing', () => ({
  useProductPrices: mocks.useProductPrices,
  useAvailability: mocks.useAvailability,
}))

describe('ProductDetailPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.catalog.productRead,
          permissionConstants.catalog.recipeRead,
        ],
      },
    })
    mocks.useProduct.mockReturnValue({
      data: {
        id: 1,
        code: 'CF-001',
        name: 'Iced Coffee',
        categoryCode: 'BEVERAGE',
        status: 'ACTIVE',
        imageUrl: null,
        description: 'Cold brew with milk',
      },
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
    mocks.useRecipes.mockReturnValue({
      data: [{ id: 10, productId: 1, recipeCode: 'REC-COFFEE', description: 'Base drink' }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useRecipeVersions.mockReturnValue({
      data: [{ id: 100, recipeId: 10, versionNo: 'v1', yieldQty: 1, yieldUomCode: 'CUP', status: 'ACTIVE', effectiveFrom: null, effectiveTo: null, ingredients: [] }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useProductPrices.mockReturnValue({ data: [], error: null, isLoading: false, refetch: vi.fn() })
    mocks.useAvailability.mockReturnValue({ data: [], error: null, isLoading: false, refetch: vi.fn() })
  })

  it('renders product detail and recipe snapshot while hiding pricing sections without price permission', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/catalog/products/:productId" element={<ProductDetailPage />} />
      </Routes>,
      { route: '/catalog/products/1' },
    )

    expect(await screen.findByRole('heading', { name: 'Product Profile' })).toBeInTheDocument()
    expect(screen.getByText('Iced Coffee')).toBeInTheDocument()
    expect(screen.getAllByText(/REC-COFFEE/).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Permission denied')).toHaveLength(2)
    expect(screen.getByText('You do not have catalog.price.read, so pricing detail is hidden on this product.')).toBeInTheDocument()
  })

  it('renders a page error when the product query fails', async () => {
    mocks.useProduct.mockReturnValue({
      data: undefined,
      error: new Error('Product detail unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(
      <Routes>
        <Route path="/catalog/products/:productId" element={<ProductDetailPage />} />
      </Routes>,
      { route: '/catalog/products/1' },
    )

    expect(await screen.findByText('Product detail unavailable')).toBeInTheDocument()
  })

  it('blocks the page when product read permission is missing', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(
      <Routes>
        <Route path="/catalog/products/:productId" element={<ProductDetailPage />} />
      </Routes>,
      { route: '/catalog/products/1' },
    )

    expect(screen.getByText('You need catalog.product.read to inspect product detail.')).toBeInTheDocument()
  })
})
