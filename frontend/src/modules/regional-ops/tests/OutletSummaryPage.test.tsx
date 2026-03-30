import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { OutletSummaryPage } from '../routes/OutletSummaryPage'

const mocks = vi.hoisted(() => ({
  useRegionalOutlets: vi.fn(),
  useRegionalRegion: vi.fn(),
}))

vi.mock('../hooks/useRegionalOps', () => ({
  useRegionalOutlets: mocks.useRegionalOutlets,
  useRegionalRegion: mocks.useRegionalRegion,
}))

describe('OutletSummaryPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.regionRead, permissionConstants.org.outletRead],
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [101],
        },
      },
      user: {
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [101],
        },
      },
    })
  })

  it('shows loading state while outlet summary is loading', () => {
    mocks.useRegionalRegion.mockReturnValue({ data: null, error: null, isLoading: true, refetch: vi.fn() })
    mocks.useRegionalOutlets.mockReturnValue({ error: null, isLoading: true, refresh: vi.fn(), rows: [] })

    renderWithProviders(<OutletSummaryPage />)

    expect(screen.getByText('Đang tải outlet summary')).toBeInTheDocument()
  })

  it('shows empty state when there are no outlets in the selected region', () => {
    mocks.useRegionalRegion.mockReturnValue({
      data: {
        id: 1,
        code: 'RG-01',
        currencyCode: 'VND',
        name: 'Ho Chi Minh Region',
        parentRegionId: null,
        taxCode: 'TAX-R1',
        timezoneName: 'Asia/Ho_Chi_Minh',
        createdAt: '2026-03-01T00:00:00Z',
        updatedAt: '2026-03-30T08:00:00Z',
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useRegionalOutlets.mockReturnValue({ error: null, isLoading: false, refresh: vi.fn(), rows: [] })

    renderWithProviders(<OutletSummaryPage />)

    expect(screen.getByText('No outlets in region')).toBeInTheDocument()
  })

  it('shows error state when outlet summary cannot load', () => {
    mocks.useRegionalRegion.mockReturnValue({ data: null, error: new Error('Region read failed'), isLoading: false, refetch: vi.fn() })
    mocks.useRegionalOutlets.mockReturnValue({ error: null, isLoading: false, refresh: vi.fn(), rows: [] })

    renderWithProviders(<OutletSummaryPage />)

    expect(screen.getByText('Không thể tải outlet summary')).toBeInTheDocument()
    expect(screen.getByText('Region read failed')).toBeInTheDocument()
  })
})
