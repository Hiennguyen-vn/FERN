import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RegionalDashboardPage } from '../routes/RegionalDashboardPage'

const mocks = vi.hoisted(() => ({
  useOutletRevenueTodayStats: vi.fn(),
  useRegionalOutlets: vi.fn(),
  useRegionalRegion: vi.fn(),
}))

vi.mock('../../reports/hooks/useOutletRevenueTodayStats', () => ({
  useOutletRevenueTodayStats: mocks.useOutletRevenueTodayStats,
}))

vi.mock('../hooks/useRegionalOps', () => ({
  useRegionalOutlets: mocks.useRegionalOutlets,
  useRegionalRegion: mocks.useRegionalRegion,
}))

describe('RegionalDashboardPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('renders dashboard summary and current regional context', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.regionRead, permissionConstants.org.outletRead],
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [101, 102],
        },
      },
      user: {
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [101, 102],
        },
      },
    })
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
    mocks.useRegionalOutlets.mockReturnValue({
      error: null,
      isLoading: false,
      refresh: vi.fn(),
      rows: [
        {
          id: 101,
          regionId: 1,
          code: 'OUT-101',
          name: 'Central Plaza',
          status: 'ACTIVE',
          address: '1 Nguyen Hue',
          phone: '0901',
          email: 'central@fern.local',
          openedAt: '2023-01-01',
          closedAt: null,
          createdAt: '2023-01-01T00:00:00Z',
          updatedAt: '2026-03-30T08:00:00Z',
        },
        {
          id: 102,
          regionId: 1,
          code: 'OUT-102',
          name: 'Saigon Center',
          status: 'CLOSED',
          address: '2 Le Loi',
          phone: null,
          email: null,
          openedAt: '2022-01-01',
          closedAt: '2025-12-31',
          createdAt: '2022-01-01T00:00:00Z',
          updatedAt: '2026-03-30T08:00:00Z',
        },
      ],
    })
    mocks.useOutletRevenueTodayStats.mockReturnValue({
      isLoading: false,
      outletStats: [
        {
          outletId: 101,
          sessionId: 9001,
          sessionStatus: 'OPEN',
          currencyCode: 'VND',
          totalOrders: 4,
          completed: 3,
          open: 1,
          cancelled: 0,
          totalRevenue: 125.5,
          cashCollected: 40,
          nonCashCollected: 85.5,
          isLoading: false,
        },
      ],
    })

    renderWithProviders(<RegionalDashboardPage />)

    expect(screen.getByRole('heading', { name: 'Regional Ops' })).toBeInTheDocument()
    expect(screen.getByText('Current regional context')).toBeInTheDocument()
    expect(screen.getByText('Open outlet summary')).toBeInTheDocument()
    expect(screen.getAllByText('Central Plaza')).toHaveLength(2)
  })

  it('shows explicit region-context message when scope does not resolve to a region', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.regionRead],
        scopeRoots: {
          system: true,
          regions: [],
          outlets: [],
        },
      },
      user: {
        scopeRoots: {
          system: true,
          regions: [],
          outlets: [],
        },
      },
    })
    mocks.useRegionalRegion.mockReturnValue({ data: null, error: null, isLoading: false, refetch: vi.fn() })
    mocks.useRegionalOutlets.mockReturnValue({ error: null, isLoading: false, refresh: vi.fn(), rows: [] })
    mocks.useOutletRevenueTodayStats.mockReturnValue({ isLoading: false, outletStats: [] })

    renderWithProviders(<RegionalDashboardPage />)

    expect(screen.getByText('Region context required')).toBeInTheDocument()
    expect(screen.getByText('Regional Ops chỉ query dữ liệu khi đã có region context rõ ràng từ app shell hoặc scope hiện tại.')).toBeInTheDocument()
  })
})
