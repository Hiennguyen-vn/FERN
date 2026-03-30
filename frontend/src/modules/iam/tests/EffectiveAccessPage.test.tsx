import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { EffectiveAccessPage } from '../routes/EffectiveAccessPage'

const mocks = vi.hoisted(() => ({
  useIamUser: vi.fn(),
  useEffectiveAccess: vi.fn(),
  useIamPermissions: vi.fn(),
  useIamPermissionOverrides: vi.fn(),
}))

vi.mock('../hooks/useIam', () => ({
  useIamUser: mocks.useIamUser,
  useEffectiveAccess: mocks.useEffectiveAccess,
  useIamPermissions: mocks.useIamPermissions,
  useIamPermissionOverrides: mocks.useIamPermissionOverrides,
}))

describe('EffectiveAccessPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.iam.userRead,
          permissionConstants.iam.permissionRead,
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
    mocks.useEffectiveAccess.mockReturnValue({
      data: {
        userId: 1,
        roles: ['bootstrap_admin'],
        grantedPermissions: ['iam.user.read', 'audit.read'],
        deniedPermissions: ['org.region.read'],
        effectivePermissions: ['iam.user.read', 'audit.read'],
        scopeRoots: { system: true, regions: [1], outlets: [] },
        sources: {
          'iam.user.read': ['ROLE:bootstrap_admin'],
          'audit.read': ['DIRECT_GRANT'],
          'org.region.read': ['DIRECT_DENY'],
        },
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useIamPermissions.mockReturnValue({
      data: [
        { code: 'iam.user.read', name: 'Read users', description: 'Can read users' },
        { code: 'audit.read', name: 'Read audit', description: 'Can read audit' },
        { code: 'org.region.read', name: 'Read region', description: 'Can read regions' },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useIamPermissionOverrides.mockReturnValue({
      data: {
        userId: 1,
        overrides: [{ permissionCode: 'audit.read', overrideMode: 'GRANT', reason: 'Need audit review', expiresAt: null }],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders effective permission explanation rows', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/iam/effective-access/:userId" element={<EffectiveAccessPage />} />
      </Routes>,
      { route: '/iam/effective-access/1' },
    )

    expect(await screen.findByText('bootstrap-admin')).toBeInTheDocument()
    expect(screen.getByText('Inherited from role bootstrap_admin')).toBeInTheDocument()
    expect(screen.getByText('Direct grant override')).toBeInTheDocument()
    expect(screen.getByText('Reason: Need audit review')).toBeInTheDocument()
  })

  it('blocks the page without iam.user.read', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(
      <Routes>
        <Route path="/iam/effective-access/:userId" element={<EffectiveAccessPage />} />
      </Routes>,
      { route: '/iam/effective-access/1' },
    )

    expect(screen.getByText('Bạn cần quyền iam.user.read để xem effective access.')).toBeInTheDocument()
  })
})
