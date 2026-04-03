import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  OutletDetailPage,
  OutletSummaryPage,
  RegionalDashboardPage,
} from '../routes/regionalOpsRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useOutletRevenueTodayStats: vi.fn(),
  useRegionalOutlet: vi.fn(),
  useRegionalOutlets: vi.fn(),
  useRegionalRegion: vi.fn(),
}))

vi.mock('../../reports/hooks/useOutletRevenueTodayStats', () => ({
  useOutletRevenueTodayStats: mocks.useOutletRevenueTodayStats,
}))

vi.mock('../hooks/useRegionalOps', () => ({
  useRegionalOutlet: mocks.useRegionalOutlet,
  useRegionalOutlets: mocks.useRegionalOutlets,
  useRegionalRegion: mocks.useRegionalRegion,
}))

function RegionalOpsRoutesHarness() {
  return (
    <Routes>
      <Route path="/regional-ops" element={<LazyRouteBoundary moduleName="Regional Ops" label="Loading regional ops workspace" />}>
        <Route index element={<RegionalDashboardPage />} />
        <Route path="outlets" element={<OutletSummaryPage />} />
        <Route path="outlets/:outletId" element={<OutletDetailPage />} />
      </Route>
    </Routes>
  )
}

describe('Regional Ops route group', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
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
      data: undefined,
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
      ],
    })
    mocks.useRegionalOutlet.mockReturnValue({
      data: {
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
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useOutletRevenueTodayStats.mockReturnValue({
      isLoading: false,
      outletStats: [],
    })
  })

  it.each([
    ['/regional-ops', 'Regional Ops'],
    ['/regional-ops/outlets', 'Outlet Summary'],
    ['/regional-ops/outlets/101', 'Outlet Detail'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<RegionalOpsRoutesHarness />, { route })

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
