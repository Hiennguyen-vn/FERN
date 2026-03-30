import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { AuditEventsPage } from '../routes/AuditEventsPage'

const mocks = vi.hoisted(() => ({
  useAuditEvents: vi.fn(),
}))

vi.mock('../hooks/useAudit', () => ({
  useAuditEvents: mocks.useAuditEvents,
}))

describe('AuditEventsPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read],
      },
    })
  })

  it('renders audit events and supports filtered empty state', async () => {
    const user = userEvent.setup()
    mocks.useAuditEvents.mockReturnValue({
      data: [
        {
          action: 'UPDATE',
          correlationId: 'corr-1',
          detailSummary: 'Stock adjustment applied',
          eventType: 'stock.adjusted',
          id: 'evt-1',
          ingestedAt: '2026-03-30T08:00:05Z',
          module: 'inventory',
          occurredAt: '2026-03-30T08:00:00Z',
          outletId: 101,
          outcome: 'SUCCESS',
          regionId: 1,
          resourceId: '501',
          resourceType: 'INVENTORY_ITEM',
          sourceEventId: 'src-1',
          sourceService: 'inventory-service',
          userId: 12,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<AuditEventsPage />)

    expect(screen.getByRole('heading', { name: 'Audit Events' })).toBeInTheDocument()
    expect(screen.getByText('stock.adjusted')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Quick search'), 'mismatch')
    await user.click(screen.getByRole('button', { name: 'Apply filters' }))

    expect(screen.getByText('No matching audit events')).toBeInTheDocument()
  })

  it('renders error state when audit events fail to load', () => {
    mocks.useAuditEvents.mockReturnValue({
      data: [],
      error: new Error('Audit service unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<AuditEventsPage />)

    expect(screen.getByText('Unable to load data')).toBeInTheDocument()
    expect(screen.getByText('Audit service unavailable')).toBeInTheDocument()
  })

  it('shows permission denied without audit.read', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useAuditEvents.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<AuditEventsPage />)

    expect(screen.getByText('Bạn cần audit.read để xem audit event trail.')).toBeInTheDocument()
  })
})
