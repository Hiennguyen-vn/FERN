import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'

const getOutletMock = vi.fn()

vi.mock('@modules/org/api/org.api', () => ({
  orgApi: {
    getOutlet: (...args: unknown[]) => getOutletMock(...args),
  },
}))

describe('AppShell', () => {
  beforeEach(() => {
    resetTestStores()
    getOutletMock.mockReset()
  })

  it('resolves region context automatically for outlet-only users', async () => {
    setAuthenticatedSession({
      principal: {
        username: 'demo-cashier',
        scopeRoots: { system: false, regions: [], outlets: [3] },
        accessibleScope: { system: false, regions: [], outlets: [3] },
      },
      user: {
        username: 'demo-cashier',
        scopeRoots: { system: false, regions: [], outlets: [3] },
      },
    })
    getOutletMock.mockResolvedValue({ id: 3, regionId: 2, code: 'DIST1', name: 'District 1', status: 'ACTIVE' })

    renderWithProviders(
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<div>Shell child</div>} />
        </Route>
      </Routes>,
    )

    expect(screen.getByRole('combobox', { name: 'Outlet' })).toHaveValue('3')

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: 'Region' })).toHaveValue('2')
    })
  })

  it('keeps the currently selected region available in the shell selector', () => {
    setAuthenticatedSession({
      principal: {
        username: 'regional-user',
        scopeRoots: { system: false, regions: [5], outlets: [9] },
        accessibleScope: { system: false, regions: [5], outlets: [9] },
      },
      user: {
        username: 'regional-user',
        scopeRoots: { system: false, regions: [5], outlets: [9] },
      },
    })
    getOutletMock.mockResolvedValue({ id: 9, regionId: 5, code: 'OUT9', name: 'Outlet 9', status: 'ACTIVE' })

    renderWithProviders(
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<div>Shell child</div>} />
        </Route>
      </Routes>,
    )

    expect(screen.getByRole('combobox', { name: 'Region' })).toHaveValue('5')
    expect(screen.getByRole('option', { name: 'Region #5' })).toBeInTheDocument()
  })
})
