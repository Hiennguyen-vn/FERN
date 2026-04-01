import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  AssignmentsPage,
  EffectiveAccessPage,
  UserDetailPage,
  UsersPage,
} from '../routes/iamRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useIamUsers: vi.fn(),
  useIamUser: vi.fn(),
  useIamRoles: vi.fn(),
  useIamPermissions: vi.fn(),
  useIamPermissionOverrides: vi.fn(),
  useEffectiveAccess: vi.fn(),
}))

vi.mock('../hooks/useIam', () => ({
  useIamUsers: mocks.useIamUsers,
  useIamUser: mocks.useIamUser,
  useIamRoles: mocks.useIamRoles,
  useIamPermissions: mocks.useIamPermissions,
  useIamPermissionOverrides: mocks.useIamPermissionOverrides,
  useEffectiveAccess: mocks.useEffectiveAccess,
}))

function IamRoutesHarness() {
  return (
    <Routes>
      <Route path="/iam" element={<LazyRouteBoundary moduleName="IAM" label="Loading IAM console" />}>
        <Route path="users" element={<UsersPage />} />
        <Route path="users/:userId" element={<UserDetailPage />} />
        <Route path="assignments" element={<AssignmentsPage />} />
        <Route path="effective-access/:userId" element={<EffectiveAccessPage />} />
      </Route>
    </Routes>
  )
}

describe('IAM route group', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.iam.userRead,
          permissionConstants.iam.roleRead,
          permissionConstants.iam.permissionRead,
          permissionConstants.iam.permissionOverrideRead,
        ],
      },
    })
    mocks.useIamUsers.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            username: 'bootstrap-admin',
            fullName: 'Bootstrap Admin',
            email: 'admin@fern.local',
            phone: null,
            status: 'ACTIVE',
            roleCodes: ['bootstrap_admin'],
            scopeRoots: { system: true, regions: [], outlets: [] },
          },
        ],
        page: 0,
        size: 50,
        hasMore: false,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
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

  it.each([
    ['/iam/users', 'IAM Users'],
    ['/iam/users/1', 'IAM User Detail'],
    ['/iam/assignments', 'IAM Assignments'],
    ['/iam/effective-access/1', 'Effective Access'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<IamRoutesHarness />, { route })
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
