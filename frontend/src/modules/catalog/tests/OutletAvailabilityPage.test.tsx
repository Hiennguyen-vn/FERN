import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { AvailabilityPage } from '../routes/OutletAvailabilityPage'

const mocks = vi.hoisted(() => ({
  useAvailability: vi.fn(),
  useProducts: vi.fn(),
}))

vi.mock('../hooks/usePricing', () => ({
  useAvailability: mocks.useAvailability,
}))

vi.mock('../hooks/useProducts', () => ({
  useProducts: mocks.useProducts,
}))

describe('AvailabilityPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.catalog.priceRead],
        scopeRoots: {
          system: false,
          regions: [],
          outlets: [],
        },
      },
    })
    mocks.useProducts.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders non-blocking outlet context guidance and empty state', () => {
    mocks.useAvailability.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<AvailabilityPage />)

    expect(screen.getByText('Chưa chọn outlet ở app shell. Trang vẫn cho phép browse cross-outlet bằng dữ liệu read model hiện có.')).toBeInTheDocument()
    expect(screen.getByText('Availability list đang trống')).toBeInTheDocument()
  })

  it('renders page error when availability query fails', () => {
    mocks.useAvailability.mockReturnValue({
      data: [],
      error: new Error('Availability unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<AvailabilityPage />)

    expect(screen.getByText('Availability unavailable')).toBeInTheDocument()
  })

  it('blocks the page when price read permission is missing', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useAvailability.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<AvailabilityPage />)

    expect(screen.getByText('Bạn cần quyền catalog.price.read để xem outlet availability.')).toBeInTheDocument()
  })
})
