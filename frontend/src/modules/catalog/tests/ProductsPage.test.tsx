import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { ProductsPage } from '../routes/ProductsPage'

const mocks = vi.hoisted(() => ({
  useIngredients: vi.fn(),
  useProducts: vi.fn(),
  useProductCategories: vi.fn(),
}))

vi.mock('../hooks/useProducts', () => ({
  useProducts: mocks.useProducts,
  useProductCategories: mocks.useProductCategories,
}))

vi.mock('../hooks/useIngredients', () => ({
  useIngredients: mocks.useIngredients,
}))

describe('ProductsPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.catalog.productRead, permissionConstants.catalog.ingredientRead],
      },
    })
    mocks.useProductCategories.mockReturnValue({ data: [], error: null })
    mocks.useIngredients.mockReturnValue({ data: [], error: null, isLoading: false, refetch: vi.fn() })
  })

  it('renders products and supports filtered empty state', async () => {
    const user = userEvent.setup()
    mocks.useProducts.mockReturnValue({
      data: [
        {
          id: 1,
          code: 'CF-001',
          name: 'Iced Coffee',
          categoryCode: 'BEVERAGE',
          status: 'ACTIVE',
          imageUrl: null,
          description: 'Cold brew with milk',
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<ProductsPage />)

    expect(screen.getByRole('heading', { name: 'Product Master Catalog' })).toBeInTheDocument()
    expect(screen.getByText('Iced Coffee')).toBeInTheDocument()

    await user.type(screen.getByPlaceholderText('Search by code or product name...'), 'tea')

    expect(screen.getByText('No products match the current filters')).toBeInTheDocument()
  })

  it('renders an error state when products fail to load', () => {
    mocks.useProducts.mockReturnValue({
      data: [],
      error: new Error('Catalog service unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<ProductsPage />)

    expect(screen.getByText('Unable to load data')).toBeInTheDocument()
    expect(screen.getByText('Catalog service unavailable')).toBeInTheDocument()
  })

  it('shows permission denied without product read permission', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useProducts.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<ProductsPage />)

    expect(screen.getByText('Permission denied')).toBeInTheDocument()
    expect(screen.getByText('You need catalog.product.read to open the product master catalog.')).toBeInTheDocument()
  })
})
