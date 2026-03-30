import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { OutletsPage } from '../routes/OutletsPage'

const mocks = vi.hoisted(() => ({
  useOutlet: vi.fn(),
  useOutlets: vi.fn(),
  useRegion: vi.fn(),
  useRegions: vi.fn(),
}))

vi.mock('../hooks/useOrg', () => ({
  useOutlet: mocks.useOutlet,
  useOutlets: mocks.useOutlets,
  useRegion: mocks.useRegion,
  useRegions: mocks.useRegions,
}))

describe('OutletsPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.outletRead],
        scopeRoots: {
          system: false,
          regions: [],
          outlets: [101, 202],
        },
      },
    })

    mocks.useOutlet.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    })
    mocks.useOutlets.mockReturnValue({
      rows: [
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
        {
          id: 202,
          regionId: 2,
          code: 'OUT-202',
          name: 'Hanoi Center',
          status: 'SUSPENDED',
          address: '99 Ba Trieu',
          phone: '0902000202',
          email: 'hn@fern.local',
          openedAt: '2024-06-01',
          closedAt: null,
          createdAt: '2024-05-20T08:00:00Z',
          updatedAt: '2026-03-12T08:00:00Z',
        },
      ],
      isLoading: false,
      error: null,
      refresh: vi.fn(),
    })
    mocks.useRegions.mockReturnValue({
      rows: [
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
        {
          id: 2,
          code: 'VN-NORTH',
          parentRegionId: null,
          currencyCode: 'VND',
          name: 'Northern Region',
          taxCode: 'TAX-NORTH',
          timezoneName: 'Asia/Ho_Chi_Minh',
          createdAt: '2026-03-01T08:00:00Z',
          updatedAt: '2026-03-10T08:00:00Z',
        },
      ],
      isLoading: false,
      error: null,
      refresh: vi.fn(),
    })
  })

  it('renders outlets and supports search/status/region filters', async () => {
    const user = userEvent.setup()

    renderWithProviders(<OutletsPage />)

    expect(await screen.findByText('District 1 Flagship')).toBeInTheDocument()
    expect(screen.getByText('Hanoi Center')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search outlets'), 'Hanoi')
    expect(screen.queryByText('District 1 Flagship')).not.toBeInTheDocument()
    expect(screen.getByText('Hanoi Center')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Search outlets'))
    await user.selectOptions(screen.getByLabelText('Status filter'), 'ACTIVE')
    expect(screen.getByText('District 1 Flagship')).toBeInTheDocument()
    expect(screen.queryByText('Hanoi Center')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Status filter'), 'ALL')
    await user.selectOptions(screen.getByLabelText('Region filter'), '2')
    expect(screen.queryByText('District 1 Flagship')).not.toBeInTheDocument()
    expect(screen.getByText('Hanoi Center')).toBeInTheDocument()
  })

  it('shows permission denied without org.outlet.read', () => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [],
        scopeRoots: {
          system: false,
          regions: [],
          outlets: [],
        },
      },
    })

    renderWithProviders(<OutletsPage />)

    expect(screen.getByText('Bạn cần quyền org.outlet.read để mở danh sách outlet.')).toBeInTheDocument()
  })
})
