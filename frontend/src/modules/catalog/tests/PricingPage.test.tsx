import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PricingPage } from '../routes/PricingPage'

const mocks = vi.hoisted(() => ({
  useProductPrices: vi.fn(),
  useProducts: vi.fn(),
}))

vi.mock('../hooks/usePricing', () => ({
  useProductPrices: mocks.useProductPrices,
}))

vi.mock('../hooks/useProducts', () => ({
  useProducts: mocks.useProducts,
}))

describe('PricingPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.catalog.priceRead, permissionConstants.catalog.productRead],
      },
    })
    mocks.useProducts.mockReturnValue({
      data: [{ id: 1, code: 'CF-001', name: 'Iced Coffee', categoryCode: 'BEVERAGE', status: 'ACTIVE', imageUrl: null, description: null }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders loading and error states for pricing', () => {
    mocks.useProductPrices.mockReturnValueOnce({
      data: [],
      error: null,
      isLoading: true,
      refetch: vi.fn(),
    })

    const loadingRender = renderWithProviders(<PricingPage />)
    expect(screen.getByText('Đang tải bảng giá')).toBeInTheDocument()
    loadingRender.unmount()

    mocks.useProductPrices.mockReturnValueOnce({
      data: [],
      error: new Error('Pricing read model unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<PricingPage />)
    expect(screen.getByText('Pricing read model unavailable')).toBeInTheDocument()
  })

  it('filters rows by effective state', async () => {
    const user = userEvent.setup()
    mocks.useProductPrices.mockReturnValue({
      data: [
        {
          id: 1,
          productId: 1,
          scopeType: 'GLOBAL',
          scopeId: null,
          priceType: 'STANDARD',
          currencyCode: 'VND',
          priceValue: 50000,
          effectiveFrom: '2024-01-01T00:00:00.000Z',
          effectiveTo: null,
        },
        {
          id: 2,
          productId: 1,
          scopeType: 'OUTLET',
          scopeId: 101,
          priceType: 'PROMOTIONAL',
          currencyCode: 'VND',
          priceValue: 45000,
          effectiveFrom: '2099-01-01T00:00:00.000Z',
          effectiveTo: null,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<PricingPage />)

    expect(screen.getByText('UPCOMING')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Effective state'), 'CURRENT')

    expect(screen.queryByText('UPCOMING')).not.toBeInTheDocument()
    expect(screen.getByText('CURRENT')).toBeInTheDocument()
  })

  it('blocks the page when price read permission is missing', () => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [],
      },
    })
    mocks.useProductPrices.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<PricingPage />)

    expect(screen.getByText('Bạn cần quyền catalog.price.read để xem bảng giá.')).toBeInTheDocument()
  })
})
