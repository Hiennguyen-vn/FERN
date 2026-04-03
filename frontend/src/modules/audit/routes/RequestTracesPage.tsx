import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Badge,
  Button,
  DataTable,
  FilterBar,
  FormActions,
  Input,
  PermissionDeniedInline,
  Select,
  SummaryCards,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRequestTraces } from '../hooks/useAudit'
import type { RequestTraceSummary } from '../model/audit.types'
import { getAuditErrorMessage } from '../services/auditError.service'
import {
  buildTraceSummaryCards,
  formatAuditDate,
  formatAuditScope,
  getStatusCodePresentation,
  matchesAuditSearch,
} from '../services/auditReadModel.service'
import { canReadAudit } from '../services/auditUiPolicy.service'

const methodOptions: SelectOption[] = [
  { label: 'All methods', value: 'ALL' },
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'PATCH', value: 'PATCH' },
  { label: 'DELETE', value: 'DELETE' },
]

interface TraceFilterDraft {
  correlationId: string
  endpoint: string
  limit: string
  method: string
  occurredFrom: string
  occurredTo: string
  outletId: string
  quickSearch: string
  regionId: string
  sourceService: string
  statusCode: string
}

const initialDraft: TraceFilterDraft = {
  correlationId: '',
  endpoint: '',
  limit: '100',
  method: 'ALL',
  occurredFrom: '',
  occurredTo: '',
  outletId: '',
  quickSearch: '',
  regionId: '',
  sourceService: '',
  statusCode: '',
}

function toInstant(value: string) {
  if (!value) {
    return undefined
  }

  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

export function RequestTracesPage() {
  usePageTitle('Request Traces')
  const principal = usePrincipal()
  const navigate = useNavigate()
  const canOpen = canReadAudit(principal)
  const [draft, setDraft] = useState(initialDraft)
  const [filters, setFilters] = useState(initialDraft)

  const tracesQuery = useRequestTraces(
    {
      correlationId: filters.correlationId || undefined,
      endpoint: filters.endpoint || undefined,
      limit: Number(filters.limit) || 100,
      method: filters.method === 'ALL' ? undefined : filters.method,
      occurredFrom: toInstant(filters.occurredFrom),
      occurredTo: toInstant(filters.occurredTo),
      outletId: filters.outletId ? Number(filters.outletId) : undefined,
      regionId: filters.regionId ? Number(filters.regionId) : undefined,
      sourceService: filters.sourceService || undefined,
      statusCode: filters.statusCode ? Number(filters.statusCode) : undefined,
    },
    { enabled: canOpen },
  )

  const filteredRows = useMemo(() => {
    return (tracesQuery.data ?? []).filter((trace) =>
      matchesAuditSearch(
        [
          trace.requestId,
          trace.endpoint,
          trace.method,
          trace.statusCode,
          trace.correlationId,
          trace.sourceService,
          trace.module,
          trace.detailSummary,
        ],
        filters.quickSearch,
      ),
    )
  }, [tracesQuery.data, filters.quickSearch])

  const columns = useMemo<Array<DataTableColumn<RequestTraceSummary>>>(
    () => [
      {
        key: 'occurredAt',
        header: 'Occurred',
        render: (trace) => formatAuditDate(trace.occurredAt),
      },
      {
        key: 'request',
        header: 'Request',
        render: (trace) => (
          <div className="page-stack" style={{ gap: '0.25rem' }}>
            <strong>{trace.method} {trace.endpoint}</strong>
            <span className="muted-text">{trace.requestId ?? 'No requestId'} · {trace.sourceService}</span>
          </div>
        ),
      },
      {
        key: 'status',
        header: 'Status',
        render: (trace) => {
          const presentation = getStatusCodePresentation(trace.statusCode)
          return <Badge tone={presentation.tone}>{presentation.label}</Badge>
        },
      },
      {
        key: 'duration',
        header: 'Duration',
        render: (trace) => `${trace.durationMs ?? 0} ms`,
      },
      {
        key: 'correlation',
        header: 'Correlation',
        render: (trace) => trace.correlationId ?? '—',
      },
      {
        key: 'scope',
        header: 'Scope',
        render: (trace) => formatAuditScope(trace.regionId, trace.outletId),
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Request Traces" description="Compact traceability console for request investigation.">
        <PermissionDeniedInline message="Bạn cần audit.read để xem request traces." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Request Traces"
      description="Investigation-focused request trace console with correlation identifiers, status codes, and latency context."
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          setFilters(draft)
        }}
      >
        <FilterBar
          actions={
            <FormActions
              primaryAction={<Button type="submit">Apply filters</Button>}
              secondaryAction={
                <Button
                  onClick={() => {
                    setDraft(initialDraft)
                    setFilters(initialDraft)
                  }}
                  type="button"
                  variant="secondary"
                >
                  Reset
                </Button>
              }
            />
          }
          description="Filter by endpoint, method, status, correlation, and scope to narrow down request investigations quickly."
          title="Trace filters"
        >
          <Input
            label="Quick search"
            onChange={(event) => setDraft((current) => ({ ...current, quickSearch: event.target.value }))}
            placeholder="requestId, endpoint, correlation"
            value={draft.quickSearch}
          />
          <Input
            label="Endpoint"
            onChange={(event) => setDraft((current) => ({ ...current, endpoint: event.target.value }))}
            placeholder="/audit/events"
            value={draft.endpoint}
          />
          <Select
            label="Method"
            onChange={(event) => setDraft((current) => ({ ...current, method: event.target.value }))}
            options={methodOptions}
            value={draft.method}
          />
          <Input
            label="Status code"
            onChange={(event) => setDraft((current) => ({ ...current, statusCode: event.target.value }))}
            placeholder="500"
            type="number"
            value={draft.statusCode}
          />
          <Input
            label="Correlation ID"
            onChange={(event) => setDraft((current) => ({ ...current, correlationId: event.target.value }))}
            placeholder="corr-trace"
            value={draft.correlationId}
          />
          <Input
            label="Source service"
            onChange={(event) => setDraft((current) => ({ ...current, sourceService: event.target.value }))}
            placeholder="api-gateway"
            value={draft.sourceService}
          />
          <Input
            label="Region ID"
            onChange={(event) => setDraft((current) => ({ ...current, regionId: event.target.value }))}
            placeholder="1"
            type="number"
            value={draft.regionId}
          />
          <Input
            label="Outlet ID"
            onChange={(event) => setDraft((current) => ({ ...current, outletId: event.target.value }))}
            placeholder="101"
            type="number"
            value={draft.outletId}
          />
          <Input
            label="Occurred from"
            onChange={(event) => setDraft((current) => ({ ...current, occurredFrom: event.target.value }))}
            type="datetime-local"
            value={draft.occurredFrom}
          />
          <Input
            label="Occurred to"
            onChange={(event) => setDraft((current) => ({ ...current, occurredTo: event.target.value }))}
            type="datetime-local"
            value={draft.occurredTo}
          />
        </FilterBar>
      </form>

      <SummaryCards items={buildTraceSummaryCards(filteredRows)} />

      <DataTable
        columns={columns}
        emptyDescription="Không có request trace nào khớp bộ lọc hiện tại."
        emptyTitle="No request traces"
        error={tracesQuery.error ? getAuditErrorMessage(tracesQuery.error, 'Không thể tải request traces.') : null}
        loading={tracesQuery.isLoading}
        loadingDescription="Đang tải request traces..."
        loadingTitle="Đang tải request traces"
        onRetry={() => void tracesQuery.refetch()}
        onRowClick={(trace) => navigate(`/audit/request-traces/${trace.id}`)}
        rowKey={(trace) => trace.id}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
