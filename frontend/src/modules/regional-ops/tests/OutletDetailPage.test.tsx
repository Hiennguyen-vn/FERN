import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { OutletDetailPage } from '../routes/OutletDetailPage'

const mocks = vi.hoisted(() => ({
  useRegionalOutlet: vi.fn(),
  useRegionalRegion: vi.fn(),
}))

vi.mock('../hooks/useRegionalOps', () => ({
  useRegionalOutlet: mocks.useRegionalOutlet,
  useRegionalRegion: mocks.useRegionalRegion,
}))

describe('OutletDetailPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
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
  })

  it('renders outlet oversight detail', async () => {
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

    renderWithProviders(
      <Routes>
        <Route path="/regional-ops/outlets/:outletId" element={<OutletDetailPage />} />
      </Routes>,
      { route: '/regional-ops/outlets/101' },
    )

    expect(await screen.findByText('Central Plaza')).toBeInTheDocument()
    expect(screen.getByText('Regional placement')).toBeInTheDocument()
    expect(screen.getByText('Operational posture')).toBeInTheDocument()
    expect(screen.getByText('Address and contact')).toBeInTheDocument()
  })

  it('shows scope mismatch banner when selected region differs from outlet region', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.regionRead, permissionConstants.org.outletRead],
        scopeRoots: {
          system: false,
          regions: [2],
          outlets: [101],
        },
      },
      user: {
        scopeRoots: {
          system: false,
          regions: [2],
          outlets: [101],
        },
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/regional-ops/outlets/:outletId" element={<OutletDetailPage />} />
      </Routes>,
      { route: '/regional-ops/outlets/101' },
    )

    expect(await screen.findByText('Outlet này thuộc Region #1, khác với region đang chọn (#2). Bạn đang xem deep-link detail theo regional perspective.')).toBeInTheDocument()
  })
})
