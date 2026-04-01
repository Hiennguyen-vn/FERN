import { useMemo, useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  DataTable,
  FilterBar,
  FormActions,
  Input,
  PermissionDeniedInline,
  Select,
  StatusBadge,
  SummaryCards,
  Button,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { maskedEmptyState } from '@shared/utils/tableHelpers'
import { useSecurityEvents } from '../hooks/useAudit'
import type { SecurityEventSummary } from '../model/audit.types'
import { getAuditErrorMessage } from '../services/auditError.service'
import {
  buildSecuritySummaryCards,
  formatAuditDate,
  matchesAuditSearch,
} from '../services/auditReadModel.service'
import { canReadAudit } from '../services/auditUiPolicy.service'

const outcomeOptions: SelectOption[] = [
  { label: 'All outcomes', value: 'ALL' },
  { label: 'SUCCESS', value: 'SUCCESS' },
  { label: 'FAILURE', value: 'FAILURE' },
]

interface SecurityFilterDraft {
  correlationId: string
  eventType: string
  limit: string
  module: string
  occurredFrom: string
  occurredTo: string
  outcome: string
  quickSearch: string
  sourceService: string
  userId: string
}

const initialDraft: SecurityFilterDraft = {
  correlationId: '',
  eventType: '',
  limit: '100',
  module: '',
  occurredFrom: '',
  occurredTo: '',
  outcome: 'ALL',
  quickSearch: '',
  sourceService: '',
  userId: '',
}

function toInstant(value: string) {
  if (!value) {
    return undefined
  }

  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

export function SecurityEventsPage() {
  usePageTitle('Security Events')
  const principal = usePrincipal()
  const canOpen = canReadAudit(principal)
  // Backend silently returns empty list for non-system users — not a 403.
  const hasSystemScope = principal?.scopeRoots.system === true
  const [draft, setDraft] = useState(initialDraft)
  const [filters, setFilters] = useState(initialDraft)

  const eventsQuery = useSecurityEvents(
    {
      correlationId: filters.correlationId || undefined,
      eventType: filters.eventType || undefined,
      limit: Number(filters.limit) || 100,
      module: filters.module || undefined,
      occurredFrom: toInstant(filters.occurredFrom),
      occurredTo: toInstant(filters.occurredTo),
      outcome: filters.outcome === 'ALL' ? undefined : filters.outcome,
      sourceService: filters.sourceService || undefined,
      userId: filters.userId ? Number(filters.userId) : undefined,
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
          event.outcome,
          event.failureReason,
          event.correlationId,
          event.detailSummary,
          event.userId,
        ],
        filters.quickSearch,
      ),
    )
  }, [eventsQuery.data, filters.quickSearch])

  const columns = useMemo<Array<DataTableColumn<SecurityEventSummary>>>(
    () => [
      {
        key: 'occurredAt',
        header: 'Occurred',
        render: (event) => formatAuditDate(event.occurredAt),
      },
      {
        key: 'eventType',
        header: 'Event',
        render: (event) => (
          <div className="page-stack" style={{ gap: '0.25rem' }}>
            <strong>{event.eventType}</strong>
            <span className="muted-text">{event.sourceService} · {event.module}</span>
          </div>
        ),
      },
      {
        key: 'userId',
        header: 'User',
        render: (event) => event.userId ?? 'N/A',
      },
      {
        key: 'outcome',
        header: 'Outcome',
        render: (event) => <StatusBadge status={event.outcome ?? 'UNKNOWN'} />,
      },
      {
        key: 'failureReason',
        header: 'Failure reason',
        render: (event) => event.failureReason ?? '—',
      },
      {
        key: 'correlationId',
        header: 'Correlation',
        render: (event) => event.correlationId ?? '—',
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Security Events" description="Investigation-focused security event console.">
        <PermissionDeniedInline message="Bạn cần audit.read để xem security events." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Security Events"
      description="Filter-heavy security event console for failed logins, auth incidents, and governance investigations."
    >
      {!hasSystemScope ? (
        <div className="inline-banner inline-banner-warning">
          Security events chỉ visible với system-scoped principals. Backend trả về danh sách rỗng (không phải 403) khi principal không có system scope.
        </div>
      ) : null}
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
          description="Backend filters run first; quick search then narrows the loaded result set. Requires system scope to receive any data."
          title="Security filters"
        >
          <Input
            label="Quick search"
            onChange={(event) => setDraft((current) => ({ ...current, quickSearch: event.target.value }))}
            placeholder="event, failure, correlation"
            value={draft.quickSearch}
          />
          <Input
            label="Event type"
            onChange={(event) => setDraft((current) => ({ ...current, eventType: event.target.value }))}
            placeholder="auth.login.failed"
            value={draft.eventType}
          />
          <Input
            label="Module"
            onChange={(event) => setDraft((current) => ({ ...current, module: event.target.value }))}
            placeholder="iam"
            value={draft.module}
          />
          <Input
            label="Source service"
            onChange={(event) => setDraft((current) => ({ ...current, sourceService: event.target.value }))}
            placeholder="iam-service"
            value={draft.sourceService}
          />
          <Input
            label="User ID"
            onChange={(event) => setDraft((current) => ({ ...current, userId: event.target.value }))}
            placeholder="44"
            type="number"
            value={draft.userId}
          />
          <Input
            label="Correlation ID"
            onChange={(event) => setDraft((current) => ({ ...current, correlationId: event.target.value }))}
            placeholder="corr-security"
            value={draft.correlationId}
          />
          <Select
            label="Outcome"
            onChange={(event) => setDraft((current) => ({ ...current, outcome: event.target.value }))}
            options={outcomeOptions}
            value={draft.outcome}
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
          <Input
            label="Limit"
            onChange={(event) => setDraft((current) => ({ ...current, limit: event.target.value }))}
            placeholder="100"
            type="number"
            value={draft.limit}
          />
        </FilterBar>
      </form>

      <SummaryCards items={buildSecuritySummaryCards(filteredRows)} />

      <DataTable
        columns={columns}
        {...maskedEmptyState(
          !hasSystemScope,
          'System scope required',
          'Backend trả về danh sách rỗng cho non-system principals. Cần system scope để xem security events.',
          'No security events',
          'Không có security event nào khớp bộ lọc hiện tại.',
        )}
        error={eventsQuery.error ? getAuditErrorMessage(eventsQuery.error, 'Không thể tải security events.') : null}
        loading={eventsQuery.isLoading}
        loadingDescription="Đang tải security event trail..."
        loadingTitle="Đang tải security events"
        onRetry={() => void eventsQuery.refetch()}
        rowKey={(event) => event.id}
        rows={filteredRows}
      />
    </DashboardLayout>
  )
}
