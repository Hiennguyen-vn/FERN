import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@core/api/gatewayClient', () => ({
  gatewayClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}))

import { gatewayClient } from '@core/api/gatewayClient'
import { auditApi } from '../api/audit.api'

describe('audit.api', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('listAuditEvents calls GET /audit/events with params and returns items', async () => {
    const items = [{ id: 'e1' } as any]
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: { items } } as any)

    const first = await auditApi.listAuditEvents()
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/events', { params: {} })
    expect(first).toEqual(items)

    vi.mocked(gatewayClient.get).mockResolvedValueOnce({ data: { items: [] } } as any)
    const filtered = await auditApi.listAuditEvents({ page: 1, size: 20 } as any)
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/events', { params: { page: 1, size: 20 } })
    expect(filtered).toEqual([])
  })

  it('getAuditEvent calls GET /audit/events/:id and returns data', async () => {
    const detail = { id: 'evt-1', action: 'READ' } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: detail } as any)

    const result = await auditApi.getAuditEvent('evt-1')
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/events/evt-1')
    expect(result).toEqual(detail)
  })

  it('listSecurityEvents calls GET /audit/security-events with params and returns items', async () => {
    const items = [{ id: 's1' } as any]
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: { items } } as any)

    const first = await auditApi.listSecurityEvents()
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/security-events', { params: {} })
    expect(first).toEqual(items)

    vi.mocked(gatewayClient.get).mockResolvedValueOnce({ data: { items: [] } } as any)
    await auditApi.listSecurityEvents({ outcome: 'FAILURE' } as any)
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/security-events', { params: { outcome: 'FAILURE' } })
  })

  it('getSecurityEvent calls GET /audit/security-events/:id and returns data', async () => {
    const detail = { id: 'sec-1' } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: detail } as any)

    const result = await auditApi.getSecurityEvent('sec-1')
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/security-events/sec-1')
    expect(result).toEqual(detail)
  })

  it('listRequestTraces calls GET /audit/request-traces with params and returns items', async () => {
    const items = [{ traceId: 't1' } as any]
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: { items } } as any)

    const first = await auditApi.listRequestTraces()
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/request-traces', { params: {} })
    expect(first).toEqual(items)

    vi.mocked(gatewayClient.get).mockResolvedValueOnce({ data: { items: [] } } as any)
    await auditApi.listRequestTraces({ correlationId: 'abc' } as any)
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/request-traces', { params: { correlationId: 'abc' } })
  })

  it('getRequestTrace calls GET /audit/request-traces/:id and returns data', async () => {
    const detail = { traceId: 'trace-1', spans: [] } as any
    vi.mocked(gatewayClient.get).mockResolvedValue({ data: detail } as any)

    const result = await auditApi.getRequestTrace('trace-1')
    expect(gatewayClient.get).toHaveBeenCalledWith('/audit/request-traces/trace-1')
    expect(result).toEqual(detail)
  })
})
