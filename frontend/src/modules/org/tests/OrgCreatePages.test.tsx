import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { OutletCreatePage, RegionCreatePage } from '../routes/orgRoutes.bundle'

const mocks = vi.hoisted(() => {
  const createRegionMutateAsync = vi.fn()
  const createOutletMutateAsync = vi.fn()
  return {
    createRegionMutateAsync,
    createOutletMutateAsync,
    useCreateRegion: vi.fn(),
    useCreateOutlet: vi.fn(),
  }
})

vi.mock('../hooks/useOrg', () => ({
  useCreateRegion: mocks.useCreateRegion,
  useCreateOutlet: mocks.useCreateOutlet,
}))

function OrgCreateRoutesHarness() {
  return (
    <Routes>
      <Route path="/org/regions/new" element={<RegionCreatePage />} />
      <Route path="/org/outlets/new" element={<OutletCreatePage />} />
      <Route path="/org/regions" element={<div>Regions landing</div>} />
      <Route path="/org/outlets" element={<div>Outlets landing</div>} />
    </Routes>
  )
}

describe('Org create pages', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.org.regionWrite,
          permissionConstants.org.outletWrite,
        ],
      },
    })

    mocks.createRegionMutateAsync.mockReset()
    mocks.createOutletMutateAsync.mockReset()
    mocks.createRegionMutateAsync.mockResolvedValue({ id: 10 })
    mocks.createOutletMutateAsync.mockResolvedValue({ id: 110 })

    mocks.useCreateRegion.mockReturnValue({
      mutateAsync: mocks.createRegionMutateAsync,
      error: null,
      isPending: false,
    })
    mocks.useCreateOutlet.mockReturnValue({
      mutateAsync: mocks.createOutletMutateAsync,
      error: null,
      isPending: false,
    })
  })

  it('renders region and outlet create workspaces with command stages', async () => {
    const regionView = renderWithProviders(<OrgCreateRoutesHarness />, { route: '/org/regions/new' })

    expect(await screen.findByRole('heading', { name: 'Create Region' })).toBeInTheDocument()
    expect(screen.getByText('New region record')).toBeInTheDocument()
    expect(screen.getByText('Define a new hierarchy node')).toBeInTheDocument()

    regionView.unmount()

    renderWithProviders(<OrgCreateRoutesHarness />, { route: '/org/outlets/new' })

    expect(await screen.findByRole('heading', { name: 'Create Outlet' })).toBeInTheDocument()
    expect(screen.getByText('New outlet record')).toBeInTheDocument()
    expect(screen.getByText('Define a new operating branch')).toBeInTheDocument()
  })

  it('submits region creation payload', async () => {
    renderWithProviders(<OrgCreateRoutesHarness />, { route: '/org/regions/new' })

    fireEvent.change(await screen.findByLabelText('Code *'), { target: { value: 'VN-CENTRAL' } })
    fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'Central Region' } })
    fireEvent.change(screen.getByLabelText('Currency code *'), { target: { value: 'usd' } })
    fireEvent.change(screen.getByLabelText('Timezone *'), { target: { value: 'UTC' } })
    fireEvent.change(screen.getByLabelText('Parent region ID'), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText('Tax code'), { target: { value: 'TAX-CEN' } })

    fireEvent.click(screen.getByRole('button', { name: 'Create region' }))

    await waitFor(() => {
      expect(mocks.createRegionMutateAsync).toHaveBeenCalledWith({
        code: 'VN-CENTRAL',
        parentRegionId: 2,
        currencyCode: 'USD',
        name: 'Central Region',
        taxCode: 'TAX-CEN',
        timezoneName: 'UTC',
      })
    })
  })

  it('submits outlet creation payload', async () => {
    renderWithProviders(<OrgCreateRoutesHarness />, { route: '/org/outlets/new' })

    fireEvent.change(await screen.findByLabelText('Region ID *'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('Code *'), { target: { value: 'DN-001' } })
    fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'Da Nang Riverside' } })
    fireEvent.change(screen.getByLabelText('Status *'), { target: { value: 'ACTIVE' } })
    fireEvent.change(screen.getByLabelText('Address'), { target: { value: '12 Bach Dang' } })
    fireEvent.change(screen.getByLabelText('Phone'), { target: { value: '0236111222' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'danang@fern.local' } })
    fireEvent.change(screen.getByLabelText('Opened at'), { target: { value: '2026-04-01' } })

    fireEvent.click(screen.getByRole('button', { name: 'Create outlet' }))

    await waitFor(() => {
      expect(mocks.createOutletMutateAsync).toHaveBeenCalledWith({
        regionId: 3,
        code: 'DN-001',
        name: 'Da Nang Riverside',
        status: 'ACTIVE',
        address: '12 Bach Dang',
        phone: '0236111222',
        email: 'danang@fern.local',
        openedAt: '2026-04-01',
        closedAt: null,
      })
    })
  })
})
