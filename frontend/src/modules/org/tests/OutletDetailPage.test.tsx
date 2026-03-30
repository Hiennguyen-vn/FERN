import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { OutletDetailPage } from '../routes/OutletDetailPage'

const mocks = vi.hoisted(() => ({
  useOutlet: vi.fn(),
  useRegion: vi.fn(),
}))

vi.mock('../hooks/useOrg', () => ({
  useOutlet: mocks.useOutlet,
  useRegion: mocks.useRegion,
  useOutlets: vi.fn(),
  useRegions: vi.fn(),
}))

describe('OutletDetailPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.outletRead],
        scopeRoots: {
          system: false,
          regions: [1],
          outlets: [101],
        },
      },
    })

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
  })

  it('renders outlet detail and linked region context', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/org/outlets/:outletId" element={<OutletDetailPage />} />
      </Routes>,
      { route: '/org/outlets/101' },
    )

    expect(await screen.findByText('District 1 Flagship')).toBeInTheDocument()
    expect(screen.getByText('Region context')).toBeInTheDocument()
    expect(screen.getAllByText(/VN-SOUTH · Southern Region/)[0]).toBeInTheDocument()
  })

  it('handles invalid route params', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/org/outlets/:outletId" element={<OutletDetailPage />} />
      </Routes>,
      { route: '/org/outlets/abc' },
    )

    expect(await screen.findByText('Thiếu outletId hợp lệ')).toBeInTheDocument()
  })
})
