import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LazyRouteBoundary } from '@app/router/LazyRouteBoundary'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  AuditEventDetailPage,
  AuditEventsPage,
  RequestTraceDetailPage,
  RequestTracesPage,
  SecurityEventsPage,
} from '../routes/auditRoutes.bundle'

const mocks = vi.hoisted(() => ({
  useAuditEvent: vi.fn(),
  useAuditEvents: vi.fn(),
  useRequestTrace: vi.fn(),
  useRequestTraces: vi.fn(),
  useSecurityEvents: vi.fn(),
}))

vi.mock('../hooks/useAudit', () => ({
  useAuditEvent: mocks.useAuditEvent,
  useAuditEvents: mocks.useAuditEvents,
  useRequestTrace: mocks.useRequestTrace,
  useRequestTraces: mocks.useRequestTraces,
  useSecurityEvents: mocks.useSecurityEvents,
}))

function AuditRoutesHarness() {
  return (
    <Routes>
      <Route path="/audit" element={<LazyRouteBoundary moduleName="Audit" label="Loading audit console" />}>
        <Route path="events" element={<AuditEventsPage />} />
        <Route path="events/:eventId" element={<AuditEventDetailPage />} />
        <Route path="security-events" element={<SecurityEventsPage />} />
        <Route path="request-traces" element={<RequestTracesPage />} />
        <Route path="request-traces/:traceId" element={<RequestTraceDetailPage />} />
      </Route>
    </Routes>
  )
}

describe('Audit route group', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read, permissionConstants.audit.detailRead],
      },
    })

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
    mocks.useSecurityEvents.mockReturnValue({
      data: [
        {
          correlationId: 'corr-sec',
          detailSummary: 'Login failed',
          eventType: 'auth.login.failed',
          failureReason: 'Bad credentials',
          id: 'sec-1',
          ingestedAt: '2026-03-30T08:10:05Z',
          module: 'iam',
          occurredAt: '2026-03-30T08:10:00Z',
          outcome: 'FAILURE',
          sourceEventId: 'src-sec-1',
          sourceService: 'iam-service',
          userId: 44,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useRequestTraces.mockReturnValue({
      data: [
        {
          correlationId: 'corr-trace',
          detailSummary: 'Gateway request trace',
          durationMs: 245,
          endpoint: '/audit/events',
          eventType: 'request.trace',
          id: 'trace-1',
          ingestedAt: '2026-03-30T08:20:01Z',
          method: 'GET',
          module: 'audit',
          occurredAt: '2026-03-30T08:20:00Z',
          outletId: 101,
          regionId: 1,
          requestId: 'req-1',
          sourceEventId: 'src-trace-1',
          sourceService: 'api-gateway',
          statusCode: 500,
          userId: 12,
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useRequestTrace.mockReturnValue({
      data: {
        correlationId: 'corr-trace',
        detailMasked: false,
        detailSummary: 'Gateway request trace',
        durationMs: 245,
        endpoint: '/audit/events',
        eventType: 'request.trace',
        id: 'trace-1',
        idempotencyKey: 'idem-trace',
        ingestedAt: '2026-03-30T08:20:01Z',
        method: 'GET',
        module: 'audit',
        occurredAt: '2026-03-30T08:20:00Z',
        outletId: 101,
        payload: { path: '/audit/events' },
        regionId: 1,
        requestId: 'req-1',
        sourceEventId: 'src-trace-1',
        sourceService: 'api-gateway',
        statusCode: 500,
        userId: 12,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
  })

  it.each([
    ['/audit/events', 'Audit Events'],
    ['/audit/events/evt-1', 'Audit Event Detail'],
    ['/audit/security-events', 'Security Events'],
    ['/audit/request-traces', 'Request Traces'],
    ['/audit/request-traces/trace-1', 'Request Trace Detail'],
  ])('resolves %s', async (route, heading) => {
    renderWithProviders(<AuditRoutesHarness />, { route })

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})
