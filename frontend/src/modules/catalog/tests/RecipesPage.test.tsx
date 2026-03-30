import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RecipesPage } from '../routes/RecipesPage'

const mocks = vi.hoisted(() => ({
  useRecipes: vi.fn(),
  useProducts: vi.fn(),
}))

vi.mock('../hooks/useRecipes', () => ({
  useRecipes: mocks.useRecipes,
}))

vi.mock('../hooks/useProducts', () => ({
  useProducts: mocks.useProducts,
}))

describe('RecipesPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.catalog.recipeRead],
      },
    })
    mocks.useRecipes.mockReturnValue({
      data: [{ id: 1, productId: 55, recipeCode: 'REC-55', description: 'Signature drink' }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useProducts.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('falls back to product id when product read permission is missing', () => {
    renderWithProviders(<RecipesPage />)

    expect(screen.getByText('#55')).toBeInTheDocument()
    expect(screen.getByText('Bạn không có quyền catalog.product.read nên cột sản phẩm sẽ hiển thị theo productId thay vì tên sản phẩm.')).toBeInTheDocument()
  })

  it('blocks the page when recipe read permission is missing', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(<RecipesPage />)

    expect(screen.getByText('Bạn cần quyền catalog.recipe.read để xem recipe list.')).toBeInTheDocument()
  })
})
