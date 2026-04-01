import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RegionsPage } from '../routes/RegionsPage'

const mocks = vi.hoisted(() => ({
  useRegionList: vi.fn(),
}))

vi.mock('../hooks/useOrg', () => ({
  useRegionList: mocks.useRegionList,
  useRegion: vi.fn(),
  useRegions: vi.fn(),
  useOutlet: vi.fn(),
  useOutlets: vi.fn(),
}))

describe('RegionsPage', () => {
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
  })

  it('renders scoped regions', async () => {
    renderWithProviders(<RegionsPage />)

    expect(await screen.findByText('Southern Region')).toBeInTheDocument()
    expect(screen.getByText('VN-SOUTH')).toBeInTheDocument()
  })

  it('shows empty guidance when there are no scoped or recent regions', async () => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.org.regionRead],
        scopeRoots: {
          system: false,
          regions: [],
          outlets: [],
        },
      },
    })
    mocks.useRegionList.mockReturnValue({
      data: { items: [], page: 0, size: 50, hasMore: false },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    })

    renderWithProviders(<RegionsPage />)

    expect(await screen.findByText('No matching regions')).toBeInTheDocument()
    expect(screen.getByText('Không có region nào khớp bộ lọc hiện tại hoặc scope hiện tại.')).toBeInTheDocument()
  })

  it('shows permission denied when principal lacks org.region.read', () => {
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

    renderWithProviders(<RegionsPage />)

    expect(screen.getByText('Bạn cần quyền org.region.read để mở danh sách region.')).toBeInTheDocument()
  })
})
