import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { SecurityEventsPage } from '../routes/SecurityEventsPage'

const mocks = vi.hoisted(() => ({
  useSecurityEvents: vi.fn(),
}))

vi.mock('../hooks/useAudit', () => ({
  useSecurityEvents: mocks.useSecurityEvents,
}))

describe('SecurityEventsPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read],
      },
    })
  })

  it('shows loading state while security events are loading', () => {
    mocks.useSecurityEvents.mockReturnValue({
      data: [],
      error: null,
      isLoading: true,
      refetch: vi.fn(),
    })

    renderWithProviders(<SecurityEventsPage />)

    expect(screen.getByText('Đang tải security events')).toBeInTheDocument()
  })

  it('shows empty state when no security events match the current filter', () => {
    mocks.useSecurityEvents.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<SecurityEventsPage />)

    expect(screen.getByText('No security events')).toBeInTheDocument()
  })

  it('shows error state when security events fail to load', () => {
    mocks.useSecurityEvents.mockReturnValue({
      data: [],
      error: new Error('Security event store unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<SecurityEventsPage />)

    expect(screen.getByText('Unable to load data')).toBeInTheDocument()
    expect(screen.getByText('Security event store unavailable')).toBeInTheDocument()
  })
})
