import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { ProductsPage } from '../routes/ProductsPage'

const mocks = vi.hoisted(() => ({
  useProducts: vi.fn(),
  useProductCategories: vi.fn(),
}))

vi.mock('../hooks/useProducts', () => ({
  useProducts: mocks.useProducts,
  useProductCategories: mocks.useProductCategories,
}))

describe('ProductsPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.catalog.productRead],
      },
    })
    mocks.useProductCategories.mockReturnValue({ data: [], error: null })
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

    expect(screen.getByRole('heading', { name: 'Sản phẩm' })).toBeInTheDocument()
    expect(screen.getByText('Iced Coffee')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Tìm theo mã, tên hoặc mô tả'), 'tea')

    expect(screen.getByText('Không có sản phẩm khớp bộ lọc')).toBeInTheDocument()
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
    expect(screen.getByText('Bạn cần quyền catalog.product.read để xem danh sách sản phẩm.')).toBeInTheDocument()
  })
})
