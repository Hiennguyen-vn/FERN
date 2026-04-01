import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { UsersPage } from '../routes/UsersPage'

const mocks = vi.hoisted(() => ({
  useIamUsers: vi.fn(),
}))

vi.mock('../hooks/useIam', () => ({
  useIamUsers: mocks.useIamUsers,
}))

describe('UsersPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        userId: 1,
        permissions: [permissionConstants.iam.userRead],
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
  })

  it('renders browsable users from the real list query', () => {
    renderWithProviders(<UsersPage />)

    expect(screen.getByText('bootstrap-admin')).toBeInTheDocument()
    expect(screen.getByText('Bootstrap Admin')).toBeInTheDocument()
  })

  it('shows permission denied without iam.user.read', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })

    renderWithProviders(<UsersPage />)

    expect(screen.getByText('Bạn cần quyền iam.user.read để xem user directory.')).toBeInTheDocument()
  })
})
