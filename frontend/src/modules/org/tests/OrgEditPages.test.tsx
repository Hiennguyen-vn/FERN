import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { OutletEditPage, RegionEditPage } from '../routes/orgRoutes.bundle'

const mocks = vi.hoisted(() => {
  const updateRegionMutateAsync = vi.fn()
  const updateOutletMutateAsync = vi.fn()
  return {
    updateRegionMutateAsync,
    updateOutletMutateAsync,
    useRegion: vi.fn(),
    useOutlet: vi.fn(),
    useUpdateRegion: vi.fn(),
    useUpdateOutlet: vi.fn(),
  }
})

vi.mock('../hooks/useOrg', () => ({
  useRegion: mocks.useRegion,
  useOutlet: mocks.useOutlet,
  useUpdateRegion: mocks.useUpdateRegion,
  useUpdateOutlet: mocks.useUpdateOutlet,
}))

function OrgEditRoutesHarness() {
  return (
    <Routes>
      <Route path="/org/regions/:regionId/edit" element={<RegionEditPage />} />
      <Route path="/org/outlets/:outletId/edit" element={<OutletEditPage />} />
      <Route path="/org/regions/:regionId" element={<div>Region detail landing</div>} />
      <Route path="/org/outlets/:outletId" element={<div>Outlet detail landing</div>} />
    </Routes>
  )
}

describe('Org edit pages', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.org.regionWrite,
          permissionConstants.org.outletWrite,
          permissionConstants.org.regionRead,
          permissionConstants.org.outletRead,
        ],
      },
    })

    mocks.updateRegionMutateAsync.mockReset()
    mocks.updateOutletMutateAsync.mockReset()
    mocks.updateRegionMutateAsync.mockResolvedValue({ id: 1 })
    mocks.updateOutletMutateAsync.mockResolvedValue({ id: 101 })

    mocks.useUpdateRegion.mockReturnValue({
      mutateAsync: mocks.updateRegionMutateAsync,
      error: null,
      isPending: false,
    })
    mocks.useUpdateOutlet.mockReturnValue({
      mutateAsync: mocks.updateOutletMutateAsync,
      error: null,
      isPending: false,
    })

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

  it('renders region and outlet edit workspaces with contextual command stages', async () => {
    const regionView = renderWithProviders(<OrgEditRoutesHarness />, { route: '/org/regions/1/edit' })

    expect(await screen.findByRole('heading', { name: 'Edit Region' })).toBeInTheDocument()
    expect(screen.getByText('Southern Region')).toBeInTheDocument()
    expect(screen.getByText('Adjust hierarchy settings')).toBeInTheDocument()

    regionView.unmount()

    renderWithProviders(<OrgEditRoutesHarness />, { route: '/org/outlets/101/edit' })

    expect(await screen.findByRole('heading', { name: 'Edit Outlet' })).toBeInTheDocument()
    expect(screen.getByText('District 1 Flagship')).toBeInTheDocument()
    expect(screen.getByText('Adjust outlet operations profile')).toBeInTheDocument()
  })

  it('submits updated region metadata', async () => {
    renderWithProviders(<OrgEditRoutesHarness />, { route: '/org/regions/1/edit' })

    fireEvent.change(await screen.findByLabelText('Name *'), { target: { value: 'Southern Region HQ' } })
    fireEvent.change(screen.getByLabelText('Currency code *'), { target: { value: 'usd' } })
    fireEvent.change(screen.getByLabelText('Timezone *'), { target: { value: 'UTC' } })
    fireEvent.change(screen.getByLabelText('Tax code'), { target: { value: 'TAX-HQ' } })

    fireEvent.click(screen.getByRole('button', { name: 'Save region' }))

    await waitFor(() => {
      expect(mocks.updateRegionMutateAsync).toHaveBeenCalledWith({
        parentRegionId: null,
        currencyCode: 'USD',
        name: 'Southern Region HQ',
        taxCode: 'TAX-HQ',
        timezoneName: 'UTC',
      })
    })
  })

  it('submits updated outlet metadata', async () => {
    renderWithProviders(<OrgEditRoutesHarness />, { route: '/org/outlets/101/edit' })

    fireEvent.change(await screen.findByLabelText('Name *'), { target: { value: 'District 1 Reserve' } })
    fireEvent.change(screen.getByLabelText('Region ID *'), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText('Phone'), { target: { value: '0901999999' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'reserve@fern.local' } })
    fireEvent.change(screen.getByLabelText('Status *'), { target: { value: 'INACTIVE' } })

    fireEvent.click(screen.getByRole('button', { name: 'Save outlet' }))

    await waitFor(() => {
      expect(mocks.updateOutletMutateAsync).toHaveBeenCalledWith({
        regionId: 2,
        name: 'District 1 Reserve',
        status: 'INACTIVE',
        address: '1 Nguyen Hue',
        phone: '0901999999',
        email: 'reserve@fern.local',
        openedAt: '2025-01-10',
        closedAt: null,
      })
    })
  })
})
