import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { AssignmentsPage } from '../routes/AssignmentsPage'

const mocks = vi.hoisted(() => ({
  useIamUser: vi.fn(),
  useIamRoles: vi.fn(),
  useIamPermissions: vi.fn(),
  useIamPermissionOverrides: vi.fn(),
  useEffectiveAccess: vi.fn(),
}))

vi.mock('../hooks/useIam', () => ({
  useIamUser: mocks.useIamUser,
  useIamRoles: mocks.useIamRoles,
  useIamPermissions: mocks.useIamPermissions,
  useIamPermissionOverrides: mocks.useIamPermissionOverrides,
  useEffectiveAccess: mocks.useEffectiveAccess,
}))

describe('AssignmentsPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        userId: 1,
        permissions: [
          permissionConstants.iam.userRead,
          permissionConstants.iam.roleRead,
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
    mocks.useIamRoles.mockReturnValue({
      data: [{ id: 10, code: 'bootstrap_admin', name: 'Bootstrap Admin', description: 'Admin role', status: 'ACTIVE', permissionCodes: ['iam.user.read'] }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useIamPermissions.mockReturnValue({
      data: [{ code: 'iam.user.read', name: 'Read users', description: 'Can read users' }],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useIamPermissionOverrides.mockReturnValue({
      data: { userId: 1, overrides: [] },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useEffectiveAccess.mockReturnValue({
      data: {
        userId: 1,
        roles: ['bootstrap_admin'],
        grantedPermissions: ['iam.user.read'],
        deniedPermissions: [],
        effectivePermissions: ['iam.user.read'],
        scopeRoots: { system: true, regions: [], outlets: [] },
        sources: { 'iam.user.read': ['ROLE:bootstrap_admin'] },
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders assignment snapshot with role and permission catalogs', () => {
    renderWithProviders(<AssignmentsPage />)

    expect(screen.getByText('Selected user assignment snapshot')).toBeInTheDocument()
    expect(screen.getByText('Role catalog')).toBeInTheDocument()
    expect(screen.getByText('Permission catalog')).toBeInTheDocument()
  })

  it('blocks the page without IAM read permissions', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(<AssignmentsPage />)

    expect(screen.getByText('Bạn không có IAM read permission cần thiết để mở assignments console.')).toBeInTheDocument()
  })
})
