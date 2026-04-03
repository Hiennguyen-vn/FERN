import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
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
  Badge,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRequestTrace } from '../hooks/useAudit'
import { getAuditErrorMessage } from '../services/auditError.service'
import {
  buildTraceTitle,
  formatAuditDate,
  formatAuditJson,
  formatAuditScope,
  getStatusCodePresentation,
} from '../services/auditReadModel.service'
import { canReadAudit, canReadAuditDetails } from '../services/auditUiPolicy.service'

export function RequestTraceDetailPage() {
  const { traceId } = useParams<{ traceId: string }>()
  const principal = usePrincipal()
  const canOpen = canReadAudit(principal)
  const canReadDetails = canReadAuditDetails(principal)
  const traceQuery = useRequestTrace(traceId ?? null, { enabled: canOpen })

  usePageTitle(traceQuery.data ? `${traceQuery.data.method} ${traceQuery.data.endpoint} — Request Trace` : 'Request Trace Detail')

  if (!canOpen) {
    return (
      <DashboardLayout title="Request Trace Detail" description="Traceability-focused request investigation detail.">
        <PermissionDeniedInline message="Bạn cần audit.read để mở request trace detail." />
      </DashboardLayout>
    )
  }

  if (!traceId) {
    return (
      <DashboardLayout title="Request Trace Detail" description="Traceability-focused request investigation detail.">
        <EmptyState description="URL không chứa traceId hợp lệ." title="Missing traceId" />
      </DashboardLayout>
    )
  }

  if (traceQuery.isLoading) {
    return (
      <DashboardLayout title="Request Trace Detail" description="Traceability-focused request investigation detail.">
        <EmptyState description="Đang tải request trace detail..." title="Đang tải request trace" />
      </DashboardLayout>
    )
  }

  if (traceQuery.error) {
    return (
      <DashboardLayout
        title="Request Trace Detail"
        description="Traceability-focused request investigation detail."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/audit/request-traces">Back to traces</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Retry"
          message={getAuditErrorMessage(traceQuery.error, 'Không thể tải request trace detail.')}
          onAction={() => void traceQuery.refetch()}
          title="Không thể tải request trace"
        />
      </DashboardLayout>
    )
  }

  if (!traceQuery.data) {
    return (
      <DashboardLayout
        title="Request Trace Detail"
        description="Traceability-focused request investigation detail."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/audit/request-traces">Back to traces</Link>
          </Button>
        }
      >
        <EmptyState description="Request trace này không tồn tại hoặc nằm ngoài scope hiện tại." title="Trace not found" />
      </DashboardLayout>
    )
  }

  const trace = traceQuery.data
  const statusCode = getStatusCodePresentation(trace.statusCode)

  return (
    <DashboardLayout
      title="Request Trace Detail"
      description="Deep-link request trace view with correlation IDs, latency, scope, and payload inspection."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/audit/request-traces">Back to traces</Link>
        </Button>
      }
    >
      <ReadonlyBanner
        message={
          trace.detailMasked || !canReadDetails
            ? 'Một phần trace payload đang bị masked theo permission hiện tại.'
            : 'Request trace detail là read-only và được tối ưu cho điều tra/correlation.'
        }
      />

      <EntityHeader
        eyebrow="Audit / Request Trace"
        metadata={
          <>
            <span>Occurred: {formatAuditDate(trace.occurredAt)}</span>
            <span>Correlation: {trace.correlationId ?? 'N/A'}</span>
            <span>Request ID: {trace.requestId ?? 'N/A'}</span>
          </>
        }
        status={<Badge tone={statusCode.tone}>{statusCode.label}</Badge>}
        title={buildTraceTitle(trace)}
      />

      <AuditMetaBlock
        items={[
          { label: 'Source event', value: trace.sourceEventId },
          { label: 'Source service', value: trace.sourceService },
          { label: 'Module', value: trace.module },
          { label: 'Method', value: trace.method },
          { label: 'Endpoint', value: trace.endpoint },
          { label: 'Duration', value: `${trace.durationMs ?? 0} ms` },
          { label: 'Scope', value: formatAuditScope(trace.regionId, trace.outletId) },
          { label: 'User ID', value: trace.userId ?? 'N/A' },
          { label: 'Idempotency key', value: trace.idempotencyKey ?? 'N/A' },
        ]}
        title="Trace metadata"
      />

      <Card title="Detail summary">
        <p className="muted-text">{trace.detailSummary ?? 'No detail summary from backend.'}</p>
      </Card>

      <Card title="Payload">
        <pre className="json-block">{formatAuditJson(trace.payload)}</pre>
      </Card>
    </DashboardLayout>
  )
}
