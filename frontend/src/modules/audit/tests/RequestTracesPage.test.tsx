import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { RequestTracesPage } from '../routes/RequestTracesPage'

const mocks = vi.hoisted(() => ({
  useRequestTraces: vi.fn(),
}))

vi.mock('../hooks/useAudit', () => ({
  useRequestTraces: mocks.useRequestTraces,
}))

describe('RequestTracesPage', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.audit.read],
      },
    })
  })

  it('renders request traces with correlation and status information', () => {
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

    renderWithProviders(<RequestTracesPage />)

    expect(screen.getByText('GET /audit/events')).toBeInTheDocument()
    expect(screen.getByText('500')).toBeInTheDocument()
    expect(screen.getByText('corr-trace')).toBeInTheDocument()
  })

  it('shows error state when request traces fail to load', () => {
    mocks.useRequestTraces.mockReturnValue({
      data: [],
      error: new Error('Trace backend unavailable'),
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<RequestTracesPage />)

    expect(screen.getByText('Unable to load data')).toBeInTheDocument()
    expect(screen.getByText('Trace backend unavailable')).toBeInTheDocument()
  })

  it('shows permission denied without audit.read', () => {
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: [] } })
    mocks.useRequestTraces.mockReturnValue({
      data: [],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    renderWithProviders(<RequestTracesPage />)

    expect(screen.getByText('Bạn cần audit.read để xem request traces.')).toBeInTheDocument()
  })
})
