import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RegionDetailPage } from '../routes/RegionDetailPage'

const mocks = vi.hoisted(() => ({
  useRegion: vi.fn(),
}))

vi.mock('../hooks/useOrg', () => ({
  useRegion: mocks.useRegion,
  useRegions: vi.fn(),
  useOutlet: vi.fn(),
  useOutlets: vi.fn(),
}))

describe('RegionDetailPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.regionRead],
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [],
        },
      },
    })

    mocks.useRegion.mockImplementation((regionId: number) => ({
      data:
        regionId === 1
          ? {
              id: 1,
              code: 'VN-SOUTH',
              parentRegionId: 2,
              currencyCode: 'VND',
              name: 'Southern Region',
              taxCode: 'TAX-SOUTH',
              timezoneName: 'Asia/Ho_Chi_Minh',
              createdAt: '2026-03-01T08:00:00Z',
              updatedAt: '2026-03-10T08:00:00Z',
            }
          : regionId === 2
            ? {
                id: 2,
                code: 'VN',
                parentRegionId: null,
                currencyCode: 'VND',
                name: 'Vietnam',
                taxCode: null,
                timezoneName: 'Asia/Ho_Chi_Minh',
                createdAt: '2026-02-01T08:00:00Z',
                updatedAt: '2026-03-01T08:00:00Z',
              }
            : undefined,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    }))
  })

  it('renders detail sections and parent region context', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/org/regions/:regionId" element={<RegionDetailPage />} />
      </Routes>,
      { route: '/org/regions/1' },
    )

    expect(await screen.findByText('Southern Region')).toBeInTheDocument()
    expect(screen.getByText('Hierarchy context')).toBeInTheDocument()
    expect(screen.getAllByText(/VN · Vietnam/)[0]).toBeInTheDocument()
  })

  it('handles invalid route params', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/org/regions/:regionId" element={<RegionDetailPage />} />
      </Routes>,
      { route: '/org/regions/abc' },
    )

    expect(await screen.findByText('Thiếu regionId hợp lệ')).toBeInTheDocument()
  })
})
