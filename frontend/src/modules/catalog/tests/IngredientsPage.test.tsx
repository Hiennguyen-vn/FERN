import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { IngredientsPage } from '../routes/IngredientsPage'

const mocks = vi.hoisted(() => ({
  useIngredients: vi.fn(),
  useIngredientCategories: vi.fn(),
}))

vi.mock('../hooks/useIngredients', () => ({
  useIngredients: mocks.useIngredients,
}))

vi.mock('../hooks/useProducts', () => ({
  useIngredientCategories: mocks.useIngredientCategories,
}))

describe('IngredientsPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.catalog.ingredientRead],
      },
    })
    mocks.useIngredientCategories.mockReturnValue({ data: [], error: null })
  })

  it('renders loading state for the ingredient list', () => {
    mocks.useIngredients.mockReturnValue({
      data: [],
      error: null,
      isLoading: true,
      refetch: vi.fn(),
    })

    renderWithProviders(<IngredientsPage />)

    expect(screen.getByText('Đang tải nguyên liệu')).toBeInTheDocument()
  })

  it('renders empty state when there are no ingredients', () => {
    mocks.useIngredients.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<IngredientsPage />)

    expect(screen.getByText('Danh mục nguyên liệu đang trống')).toBeInTheDocument()
  })

  it('blocks the page when ingredient read permission is missing', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useIngredients.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<IngredientsPage />)

    expect(screen.getByText('Bạn cần quyền catalog.ingredient.read để xem danh mục nguyên liệu.')).toBeInTheDocument()
  })
})
