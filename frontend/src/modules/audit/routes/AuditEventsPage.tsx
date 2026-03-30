import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
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
import { useAuditEvents } from '../hooks/useAudit'
import type { AuditEventSummary } from '../model/audit.types'
import { getAuditErrorMessage } from '../services/auditError.service'
import {
  buildAuditEventSummaryCards,
  formatAuditDate,
  formatAuditScope,
  matchesAuditSearch,
} from '../services/auditReadModel.service'
import { canReadAudit } from '../services/auditUiPolicy.service'

const outcomeOptions: SelectOption[] = [
  { label: 'All outcomes', value: 'ALL' },
  { label: 'SUCCESS', value: 'SUCCESS' },
  { label: 'FAILURE', value: 'FAILURE' },
]

const limitOptions: SelectOption[] = [
  { label: '50 rows', value: '50' },
  { label: '100 rows', value: '100' },
  { label: '200 rows', value: '200' },
]

interface AuditFilterDraft {
  action: string
  correlationId: string
  limit: string
  module: string
  occurredFrom: string
  occurredTo: string
  outcome: string
  quickSearch: string
  resourceId: string
  sourceService: string
}

const initialDraft: AuditFilterDraft = {
  action: '',
  correlationId: '',
  limit: '100',
  module: '',
  occurredFrom: '',
  occurredTo: '',
  outcome: 'ALL',
  quickSearch: '',
  resourceId: '',
  sourceService: '',
}

function toInstant(value: string) {
  if (!value) {
    return undefined
  }

  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

export function AuditEventsPage() {
  usePageTitle('Audit Events')
  const principal = usePrincipal()
  const navigate = useNavigate()
  const canOpen = canReadAudit(principal)
  const [draft, setDraft] = useState<AuditFilterDraft>(initialDraft)
  const [filters, setFilters] = useState(initialDraft)

  const eventsQuery = useAuditEvents(
    {
      action: filters.action || undefined,
      correlationId: filters.correlationId || undefined,
      limit: Number(filters.limit) || 100,
      module: filters.module || undefined,
      occurredFrom: toInstant(filters.occurredFrom),
      occurredTo: toInstant(filters.occurredTo),
      outcome: filters.outcome === 'ALL' ? undefined : filters.outcome,
      resourceId: filters.resourceId || undefined,
      sourceService: filters.sourceService || undefined,
    },
    { enabled: canOpen },
  )

  const filteredRows = useMemo(() => {
    return (eventsQuery.data ?? []).filter((event) =>
      matchesAuditSearch(
        [
          event.sourceEventId,
          event.sourceService,
          event.module,
          event.eventType,
          event.action,
          event.resourceType,
          event.resourceId,
          event.correlationId,
          event.detailSummary,
        ],
        filters.quickSearch,
      ),
    )
  }, [eventsQuery.data, filters.quickSearch])

  const columns = useMemo<Array<DataTableColumn<AuditEventSummary>>>(
    () => [
      {
        key: 'occurredAt',
        header: 'Occurred',
        render: (event) => formatAuditDate(event.occurredAt),
      },
      {
        key: 'event',
        header: 'Event',
        render: (event) => (
          <div className="page-stack" style={{ gap: '0.25rem' }}>
            <strong>{event.eventType}</strong>
            <span className="muted-text">{event.sourceService} · {event.module}</span>
          </div>
        ),
      },
      {
        key: 'resource',
        header: 'Resource',
        render: (event) => `${event.resourceType ?? '—'} ${event.resourceId ? `#${event.resourceId}` : ''}`.trim(),
      },
      {
        key: 'outcome',
        header: 'Outcome',
        render: (event) => event.outcome ?? '—',
      },
      {
        key: 'correlationId',
        header: 'Correlation',
        render: (event) => event.correlationId ?? '—',
      },
      {
        key: 'scope',
        header: 'Scope',
        render: (event) => formatAuditScope(event.regionId, event.outletId),
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Audit Events" description="System audit trail for enterprise investigation and governance.">
        <PermissionDeniedInline message="Bạn cần audit.read để xem audit event trail." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Audit Events"
      description="Dense audit-event console với filter-heavy traceability và deep-link vào từng event detail."
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
          description="Backend filters run first, then quick search narrows the loaded result set for investigation."
          title="Audit filters"
        >
          <Input
            label="Quick search"
            onChange={(event) => setDraft((current) => ({ ...current, quickSearch: event.target.value }))}
            placeholder="event type, resource, correlation"
            value={draft.quickSearch}
          />
          <Input
            label="Module"
            onChange={(event) => setDraft((current) => ({ ...current, module: event.target.value }))}
            placeholder="inventory"
            value={draft.module}
          />
          <Input
            label="Action"
            onChange={(event) => setDraft((current) => ({ ...current, action: event.target.value }))}
            placeholder="UPDATE"
            value={draft.action}
          />
          <Input
            label="Resource ID"
            onChange={(event) => setDraft((current) => ({ ...current, resourceId: event.target.value }))}
            placeholder="501"
            value={draft.resourceId}
          />
          <Input
            label="Correlation ID"
            onChange={(event) => setDraft((current) => ({ ...current, correlationId: event.target.value }))}
            placeholder="corr-..."
            value={draft.correlationId}
          />
          <Input
            label="Source service"
            onChange={(event) => setDraft((current) => ({ ...current, sourceService: event.target.value }))}
            placeholder="inventory-service"
            value={draft.sourceService}
          />
          <Select
            label="Outcome"
            onChange={(event) => setDraft((current) => ({ ...current, outcome: event.target.value }))}
            options={outcomeOptions}
            value={draft.outcome}
          />
          <Select
            label="Limit"
            onChange={(event) => setDraft((current) => ({ ...current, limit: event.target.value }))}
            options={limitOptions}
            value={draft.limit}
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

      <SummaryCards items={buildAuditEventSummaryCards(filteredRows)} />

      <DataTable
        columns={columns}
        emptyDescription={
          filters.quickSearch.trim()
            ? 'Không có audit event nào khớp bộ lọc hoặc quick search hiện tại.'
            : 'Chưa có audit event nào trong phạm vi filter hiện tại.'
        }
        emptyTitle={filters.quickSearch.trim() ? 'No matching audit events' : 'No audit events'}
        error={eventsQuery.error ? getAuditErrorMessage(eventsQuery.error, 'Không thể tải audit events.') : null}
        loading={eventsQuery.isLoading}
        loadingDescription="Đang tải audit event trail..."
        loadingTitle="Đang tải audit events"
        onRetry={() => void eventsQuery.refetch()}
        onRowClick={(event) => navigate(`/audit/events/${event.id}`)}
        rowKey={(event) => event.id}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
