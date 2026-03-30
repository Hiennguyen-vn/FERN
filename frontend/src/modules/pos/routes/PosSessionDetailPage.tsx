import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  ConfirmActionDialog,
  CurrencyInput,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormActions,
  FormSection,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
  Textarea,
} from '@design-system/index'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'
import { formatDateTime, formatMoney } from '@shared/formatters'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useClosePosSession, usePosSession, useReconcilePosSession } from '../hooks/usePosSession'
import {
  canCloseSessionAction,
  canReadSessions,
  canReconcileSessionAction,
} from '../services/posUiPolicy.service'
import { canCloseSession, canReconcileSession, isSessionReadonly } from '../services/sessionUiPolicy.service'

export function PosSessionDetailPage() {
  const params = useParams<{ sessionId: string }>()
  const sessionId = params.sessionId ? Number(params.sessionId) : null
  const principal = usePrincipal()
  const isOnline = useNetworkStatus()
  const [countedCashAmount, setCountedCashAmount] = useState('')
  const [note, setNote] = useState('')
  const [closeDialogOpen, setCloseDialogOpen] = useState(false)

  const canReadSessionsPermission = canReadSessions(principal)
  const canClosePermission = canCloseSessionAction(principal)
  const canReconcilePermission = canReconcileSessionAction(principal)
  const sessionQuery = usePosSession(sessionId, canReadSessionsPermission)
  const closeMutation = useClosePosSession()
  const reconcileMutation = useReconcilePosSession()

  usePageTitle(sessionQuery.data ? `Session ${sessionQuery.data.sessionCode}` : 'Session Detail')

  if (!canReadSessionsPermission) {
    return (
      <section className="page-stack">
        <PermissionDeniedInline
          message="Cần quyền `pos.session.read` để xem cash summary, audit metadata và lifecycle của session."
          title="Không thể mở session detail"
        />
      </section>
    )
  }

  const session = sessionQuery.data

  if (sessionQuery.isLoading) {
    return (
      <section className="page-stack">
        <Card title="Loading session">
          <p className="muted-text">Loading POS session detail...</p>
        </Card>
      </section>
    )
  }

  if (sessionQuery.error) {
    return (
      <section className="page-stack">
        <ErrorState
          actionLabel="Retry"
          message={sessionQuery.error instanceof Error ? sessionQuery.error.message : 'Failed to load session'}
          onAction={() => void sessionQuery.refetch()}
          title="Không thể tải session"
        />
      </section>
    )
  }

  if (!session) {
    return (
      <section className="page-stack">
        <EmptyState
          description="Session này không tồn tại, hoặc không còn nằm trong phạm vi hiện tại."
          title="Session not found"
        />
      </section>
    )
  }

  return (
    <section className="page-stack">
      <EntityHeader
        actions={
          <>
            {canClosePermission && canCloseSession(session, isOnline) ? (
              <Button loading={closeMutation.isPending} onClick={() => setCloseDialogOpen(true)} size="sm" variant="danger">
                Close session
              </Button>
            ) : null}
          </>
        }
        eyebrow="POS Session"
        metadata={
          <>
            <span>Outlet #{session.outletId}</span>
            <span>Region #{session.regionId}</span>
            <span>Business date: {session.businessDate}</span>
            <span>Opened: {formatDateTime(session.openedAt)}</span>
            <span>Closed: {formatDateTime(session.closedAt)}</span>
            <span>Reconciled: {formatDateTime(session.reconciledAt)}</span>
          </>
        }
        status={<StatusBadge status={session.status} />}
        title={session.sessionCode}
      />

      {!isOnline ? (
        <ReadonlyBanner message="POS đang offline. Close và reconcile session tạm thời bị khóa cho đến khi kết nối trở lại." />
      ) : null}

      {isSessionReadonly(session) ? (
        <ReadonlyBanner message="Session này đã được reconcile và hiện ở trạng thái chỉ đọc." />
      ) : null}

      <Card title="Cash summary">
        <div className="pos-summary-grid">
          <span>Expected cash: {formatMoney(session.expectedCashAmount, session.currencyCode)}</span>
          <span>Counted cash: {formatMoney(session.countedCashAmount, session.currencyCode)}</span>
          <span>Discrepancy: {formatMoney(session.discrepancyAmount, session.currencyCode)}</span>
          <span>Cashier user: #{session.cashierUserId ?? 'N/A'}</span>
          <span>Manager user: #{session.managerUserId ?? 'N/A'}</span>
          <span>Terminal: {session.terminalId ?? 'N/A'}</span>
        </div>
        {session.note ? <p className="muted-text">{session.note}</p> : null}
      </Card>

      {session.status.toUpperCase() === 'CLOSED' && !canReconcilePermission ? (
        <PermissionDeniedInline
          message="Session đã đóng nhưng tài khoản hiện tại không có quyền `pos.session.reconcile` để ghi nhận counted cash."
          title="Không thể reconcile session"
        />
      ) : null}

      {canReconcilePermission && canReconcileSession(session, isOnline) ? (
        <FormSection description="Backend sẽ tự tính expected cash và discrepancy từ successful CASH payments." title="Reconcile session">
          <div className="field-grid">
            <CurrencyInput
              label="Counted cash amount"
              min="0"
              onChange={(event) => setCountedCashAmount(event.target.value)}
              value={countedCashAmount}
            />
          </div>
          <Textarea
            label="Note"
            onChange={(event) => setNote(event.target.value)}
            placeholder="Optional reconciliation note"
            rows={3}
            value={note}
          />
          <FormActions
            primaryAction={
              <Button
                disabled={Number(countedCashAmount) < 0}
                loading={reconcileMutation.isPending}
                onClick={() =>
                  void reconcileMutation.mutateAsync({
                    sessionId: session.id,
                    payload: {
                      countedCashAmount: Number(countedCashAmount),
                      note: note || undefined,
                    },
                  })
                }
              >
                Reconcile session
              </Button>
            }
          />
          {reconcileMutation.error ? (
            <ErrorState
              message={reconcileMutation.error instanceof Error ? reconcileMutation.error.message : 'Failed to reconcile session'}
              title="Không thể reconcile session"
            />
          ) : null}
        </FormSection>
      ) : null}

      {closeMutation.error ? (
        <ErrorState
          message={closeMutation.error instanceof Error ? closeMutation.error.message : 'Failed to close session'}
          title="Không thể đóng session"
        />
      ) : null}

      <ConfirmActionDialog
        confirmLabel="Close session"
        danger
        description="Only open sessions can be closed, và backend sẽ chặn nếu còn order OPEN hoặc COMPLETING."
        onCancel={() => setCloseDialogOpen(false)}
        onConfirm={async () => {
          if (!session) {
            return
          }

          await closeMutation.mutateAsync(session.id)
          setCloseDialogOpen(false)
        }}
        open={closeDialogOpen}
        title="Close this session?"
      />
    </section>
  )
}
