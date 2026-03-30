import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { AuditEventDetailPage } from '../routes/AuditEventDetailPage'

const mocks = vi.hoisted(() => ({
  useAuditEvent: vi.fn(),
}))

vi.mock('../hooks/useAudit', () => ({
  useAuditEvent: mocks.useAuditEvent,
}))

describe('AuditEventDetailPage', () => {
  beforeEach(() => {
    resetTestStores()
    mocks.useAuditEvent.mockReturnValue({
      data: {
        action: 'UPDATE',
        correlationId: 'corr-1',
        detailMasked: false,
        detailSummary: 'Stock adjustment applied',
        eventType: 'stock.adjusted',
        id: 'evt-1',
        idempotencyKey: 'idem-1',
        ingestedAt: '2026-03-30T08:00:05Z',
        module: 'inventory',
        newValue: { quantity: 12 },
        occurredAt: '2026-03-30T08:00:00Z',
        oldValue: { quantity: 10 },
        outletId: 101,
        outcome: 'SUCCESS',
        payload: { reason: 'damage' },
        regionId: 1,
        resourceId: '501',
        resourceType: 'INVENTORY_ITEM',
        sourceEventId: 'src-1',
        sourceService: 'inventory-service',
        userId: 12,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it('renders audit event detail and payload sections', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read, permissionConstants.audit.detailRead],
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/audit/events/:eventId" element={<AuditEventDetailPage />} />
      </Routes>,
      { route: '/audit/events/evt-1' },
    )

    expect(await screen.findByText('stock.adjusted')).toBeInTheDocument()
    expect(screen.getByText('Event metadata')).toBeInTheDocument()
    expect(screen.getByText('Old value')).toBeInTheDocument()
    expect(screen.getByText('New value')).toBeInTheDocument()
    expect(screen.getAllByText((_, node) => node?.textContent?.includes('"reason": "damage"') ?? false).length).toBeGreaterThan(0)
  })

  it('shows masked readonly messaging without audit.detail.read', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read],
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/audit/events/:eventId" element={<AuditEventDetailPage />} />
      </Routes>,
      { route: '/audit/events/evt-1' },
    )

    expect(await screen.findByText('Một phần payload hoặc change detail đang bị masked theo permission hiện tại.')).toBeInTheDocument()
  })
})
