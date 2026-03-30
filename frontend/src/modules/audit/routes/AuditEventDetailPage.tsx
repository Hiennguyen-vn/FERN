import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  AuditMetaBlock,
  Button,
  Card,
  EmptyState,
  EntityHeader,
  ErrorState,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAuditEvent } from '../hooks/useAudit'
import { getAuditErrorMessage } from '../services/auditError.service'
import { formatAuditDate, formatAuditJson, formatAuditScope } from '../services/auditReadModel.service'
import { canReadAudit, canReadAuditDetails } from '../services/auditUiPolicy.service'

export function AuditEventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>()
  const principal = usePrincipal()
  const canOpen = canReadAudit(principal)
  const canReadDetails = canReadAuditDetails(principal)
  const eventQuery = useAuditEvent(eventId ?? null, { enabled: canOpen })

  usePageTitle(eventQuery.data ? `${eventQuery.data.eventType} — Audit Event` : 'Audit Event Detail')

  if (!canOpen) {
    return (
      <DashboardLayout title="Audit Event Detail" description="Read-first audit detail view for investigation.">
        <PermissionDeniedInline message="Bạn cần audit.read để mở audit event detail." />
      </DashboardLayout>
    )
  }

  if (!eventId) {
    return (
      <DashboardLayout title="Audit Event Detail" description="Read-first audit detail view for investigation.">
        <EmptyState description="URL không chứa eventId hợp lệ." title="Missing eventId" />
      </DashboardLayout>
    )
  }

  if (eventQuery.isLoading) {
    return (
      <DashboardLayout title="Audit Event Detail" description="Read-first audit detail view for investigation.">
        <EmptyState description="Đang tải audit event detail..." title="Đang tải audit event" />
      </DashboardLayout>
    )
  }

  if (eventQuery.error) {
    return (
      <DashboardLayout
        title="Audit Event Detail"
        description="Read-first audit detail view for investigation."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/audit/events">Back to audit events</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Retry"
          message={getAuditErrorMessage(eventQuery.error, 'Không thể tải audit event detail.')}
          onAction={() => void eventQuery.refetch()}
          title="Không thể tải audit event"
        />
      </DashboardLayout>
    )
  }

  if (!eventQuery.data) {
    return (
      <DashboardLayout
        title="Audit Event Detail"
        description="Read-first audit detail view for investigation."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/audit/events">Back to audit events</Link>
          </Button>
        }
      >
        <EmptyState description="Audit event này không tồn tại hoặc nằm ngoài scope hiện tại." title="Audit event not found" />
      </DashboardLayout>
    )
  }

  const auditEvent = eventQuery.data

  return (
    <DashboardLayout
      title="Audit Event Detail"
      description="Investigation-oriented detail view with metadata, scope, summaries, and masked payload awareness."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/audit/events">Back to audit events</Link>
        </Button>
      }
    >
      <ReadonlyBanner
        message={
          auditEvent.detailMasked || !canReadDetails
            ? 'Một phần payload hoặc change detail đang bị masked theo permission hiện tại.'
            : 'Audit event detail là read-only và được trình bày để phục vụ điều tra, không hỗ trợ mutation.'
        }
      />

      <EntityHeader
        eyebrow="Audit / Event"
        metadata={
          <>
            <span>Occurred: {formatAuditDate(auditEvent.occurredAt)}</span>
            <span>Ingested: {formatAuditDate(auditEvent.ingestedAt)}</span>
            <span>Correlation: {auditEvent.correlationId ?? 'N/A'}</span>
          </>
        }
        status={<StatusBadge status={auditEvent.outcome ?? 'UNKNOWN'} />}
        title={auditEvent.eventType}
      />

      <AuditMetaBlock
        items={[
          { label: 'Source event', value: auditEvent.sourceEventId },
          { label: 'Source service', value: auditEvent.sourceService },
          { label: 'Module', value: auditEvent.module },
          { label: 'Action', value: auditEvent.action ?? 'N/A' },
          { label: 'Resource', value: `${auditEvent.resourceType ?? 'N/A'} ${auditEvent.resourceId ?? ''}`.trim() },
          { label: 'Scope', value: formatAuditScope(auditEvent.regionId, auditEvent.outletId) },
          { label: 'User ID', value: auditEvent.userId ?? 'N/A' },
          { label: 'Idempotency key', value: auditEvent.idempotencyKey ?? 'N/A' },
        ]}
        title="Event metadata"
      />

      <Card title="Detail summary">
        <p className="muted-text">{auditEvent.detailSummary ?? 'No detail summary from backend.'}</p>
      </Card>

      <Card title="Old value">
        <pre className="json-block">{formatAuditJson(auditEvent.oldValue)}</pre>
      </Card>

      <Card title="New value">
        <pre className="json-block">{formatAuditJson(auditEvent.newValue)}</pre>
      </Card>

      <Card title="Payload">
        <pre className="json-block">{formatAuditJson(auditEvent.payload)}</pre>
      </Card>
    </DashboardLayout>
  )
}
