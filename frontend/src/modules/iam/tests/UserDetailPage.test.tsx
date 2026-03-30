import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { UserDetailPage } from '../routes/UserDetailPage'

const mocks = vi.hoisted(() => ({
  useIamUser: vi.fn(),
  useIamRoles: vi.fn(),
  useIamPermissionOverrides: vi.fn(),
}))

vi.mock('../hooks/useIam', () => ({
  useIamUser: mocks.useIamUser,
  useIamRoles: mocks.useIamRoles,
  useIamPermissionOverrides: mocks.useIamPermissionOverrides,
}))

describe('UserDetailPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.iam.userRead,
          permissionConstants.iam.roleRead,
          permissionConstants.iam.permissionOverrideRead,
        ],
      },
    })
    mocks.useIamUser.mockReturnValue({
      data: {
        id: 1,
        username: 'bootstrap-admin',
        fullName: 'Bootstrap Admin',
        email: 'admin@fern.local',
        phone: null,
        status: 'ACTIVE',
        roleCodes: ['bootstrap_admin'],
        scopeRoots: { system: true, regions: [], outlets: [] },
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useIamRoles.mockReturnValue({
      data: [{ id: 10, code: 'bootstrap_admin', name: 'Bootstrap Admin', description: 'Admin role', status: 'ACTIVE', permissionCodes: ['iam.user.read'] }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useIamPermissionOverrides.mockReturnValue({
      data: {
        userId: 1,
        overrides: [{ permissionCode: 'audit.read', overrideMode: 'GRANT', reason: 'Temporary access', expiresAt: null }],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders user detail with role metadata and overrides', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/iam/users/:userId" element={<UserDetailPage />} />
      </Routes>,
      { route: '/iam/users/1' },
    )

    expect(await screen.findByText('bootstrap-admin')).toBeInTheDocument()
    expect(screen.getAllByText('Bootstrap Admin').length).toBeGreaterThan(0)
    expect(screen.getByText('audit.read')).toBeInTheDocument()
  })

  it('shows page error when user fetch fails', async () => {
    mocks.useIamUser.mockReturnValue({
      data: undefined,
      error: new Error('User detail unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(
      <Routes>
        <Route path="/iam/users/:userId" element={<UserDetailPage />} />
      </Routes>,
      { route: '/iam/users/1' },
    )

    expect(await screen.findByText('User detail unavailable')).toBeInTheDocument()
  })
})
