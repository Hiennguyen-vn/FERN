import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RequestTraceDetailPage } from '../routes/RequestTraceDetailPage'

const mocks = vi.hoisted(() => ({
  useRequestTrace: vi.fn(),
}))

vi.mock('../hooks/useAudit', () => ({
  useRequestTrace: mocks.useRequestTrace,
}))

describe('RequestTraceDetailPage', () => {
  beforeEach(() => {
    resetTestStores()
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

  it('renders request trace detail with metadata and payload', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read, permissionConstants.audit.detailRead],
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/audit/request-traces/:traceId" element={<RequestTraceDetailPage />} />
      </Routes>,
      { route: '/audit/request-traces/trace-1' },
    )

    expect(await screen.findByText('GET /audit/events')).toBeInTheDocument()
    expect(screen.getByText('Trace metadata')).toBeInTheDocument()
    expect(screen.getByText('Payload')).toBeInTheDocument()
    expect(screen.getAllByText((_, node) => node?.textContent?.includes('"path": "/audit/events"') ?? false).length).toBeGreaterThan(0)
  })

  it('shows masked readonly messaging without audit.detail.read', async () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read],
      },
    })

    renderWithProviders(
      <Routes>
        <Route path="/audit/request-traces/:traceId" element={<RequestTraceDetailPage />} />
      </Routes>,
      { route: '/audit/request-traces/trace-1' },
    )

    expect(await screen.findByText('Một phần trace payload đang bị masked theo permission hiện tại.')).toBeInTheDocument()
  })
})
