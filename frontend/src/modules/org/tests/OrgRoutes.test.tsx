import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  OutletDetailPage,
  OutletsPage,
  RegionDetailPage,
  RegionsPage,
} from '../routes/orgRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useRegionList: vi.fn(),
  useRegion: vi.fn(),
  useRegions: vi.fn(),
  useOutletList: vi.fn(),
  useOutlet: vi.fn(),
  useOutlets: vi.fn(),
}))

vi.mock('../hooks/useOrg', () => ({
  useRegionList: mocks.useRegionList,
  useRegion: mocks.useRegion,
  useRegions: mocks.useRegions,
  useOutletList: mocks.useOutletList,
  useOutlet: mocks.useOutlet,
  useOutlets: mocks.useOutlets,
}))

function OrgRoutesHarness() {
  return (
    <Routes>
      <Route path="/org" element={<LazyRouteBoundary moduleName="Org" label="Loading org workspace" />}>
        <Route path="regions" element={<RegionsPage />} />
        <Route path="regions/:regionId" element={<RegionDetailPage />} />
        <Route path="outlets" element={<OutletsPage />} />
        <Route path="outlets/:outletId" element={<OutletDetailPage />} />
      </Route>
    </Routes>
  )
}

describe('Org route group', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.org.regionRead,
          permissionConstants.org.outletRead,
        ],
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [101],
        },
      },
    })

    mocks.useRegionList.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            code: 'VN-SOUTH',
            parentRegionId: null,
            currencyCode: 'VND',
            name: 'Southern Region',
            taxCode: 'TAX-SOUTH',
            timezoneName: 'Asia/Ho_Chi_Minh',
            createdAt: '2026-03-01T08:00:00Z',
            updatedAt: '2026-03-10T08:00:00Z',
          },
        ],
        page: 0,
        size: 50,
        hasMore: false,
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    })
    mocks.useRegions.mockReturnValue({ rows: [], isLoading: false, error: null, refresh: vi.fn() })
    mocks.useRegion.mockImplementation((regionId: number) => ({
      data:
        regionId === 1
          ? {
              id: 1,
              code: 'VN-SOUTH',
              parentRegionId: null,
              currencyCode: 'VND',
              name: 'Southern Region',
              taxCode: 'TAX-SOUTH',
              timezoneName: 'Asia/Ho_Chi_Minh',
              createdAt: '2026-03-01T08:00:00Z',
              updatedAt: '2026-03-10T08:00:00Z',
            }
          : undefined,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }))
    mocks.useOutletList.mockReturnValue({
      data: {
        items: [
          {
            id: 101,
            regionId: 1,
            code: 'OUT-101',
            name: 'District 1 Flagship',
            status: 'ACTIVE',
            address: '1 Nguyen Hue',
            phone: '0901000101',
            email: 'd1@fern.local',
            openedAt: '2025-01-10',
            closedAt: null,
            createdAt: '2025-01-01T08:00:00Z',
            updatedAt: '2026-03-10T08:00:00Z',
          },
        ],
        page: 0,
        size: 50,
        hasMore: false,
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    })
    mocks.useOutlets.mockReturnValue({ rows: [], isLoading: false, error: null, refresh: vi.fn() })
    mocks.useOutlet.mockImplementation((outletId: number) => ({
      data:
        outletId === 101
          ? {
              id: 101,
              regionId: 1,
              code: 'OUT-101',
              name: 'District 1 Flagship',
              status: 'ACTIVE',
              address: '1 Nguyen Hue',
              phone: '0901000101',
              email: 'd1@fern.local',
              openedAt: '2025-01-10',
              closedAt: null,
              createdAt: '2025-01-01T08:00:00Z',
              updatedAt: '2026-03-10T08:00:00Z',
            }
          : undefined,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }))
  })

  it.each([
    ['/org/regions', 'Regions'],
    ['/org/regions/1', 'Region Detail'],
    ['/org/outlets', 'Outlets'],
    ['/org/outlets/101', 'Outlet Detail'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<OrgRoutesHarness />, { route })

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
