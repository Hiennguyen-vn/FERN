import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  ConfirmActionDialog,
  DataTable,
  EmptyState,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  Pagination,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
  Textarea,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'
import { formatDateTime, formatMoney } from '@shared/formatters'
import { useRegion } from '@modules/org/hooks/useOrg'
import { useClosePosSession, useOpenPosSession, usePosSessions } from '../hooks/usePosSession'
import type { PosSession, PosSessionStatus } from '../model/pos.types'
import {
  canCloseSessionAction,
  canOpenSession,
  canReadSessions,
} from '../services/posUiPolicy.service'
import { getTodayBusinessDate } from '../services/posDate.service'
import { canCloseSession } from '../services/sessionUiPolicy.service'

export function PosSessionsPage() {
  usePageTitle('Session Control')

  const navigate = useNavigate()
  const principal = usePrincipal()
  const isOnline = useNetworkStatus()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const [businessDate, setBusinessDate] = useState(getTodayBusinessDate())
  const [statusFilter, setStatusFilter] = useState<PosSessionStatus | ''>('')
  const [openNote, setOpenNote] = useState('')
  const [terminalId, setTerminalId] = useState('')
  const [reusedSessionCode, setReusedSessionCode] = useState('')
  const [sessionToClose, setSessionToClose] = useState<PosSession | null>(null)
  const [page, setPage] = useState(0)
  const size = 20

  const canReadSessionsPermission = canReadSessions(principal)
  const canOpenSessionPermission = canOpenSession(principal, isOnline)
  const canCloseSessionPermission = canCloseSessionAction(principal)
  const sessionsQuery = usePosSessions(
    selectedOutletId && canReadSessionsPermission
      ? {
          outletId: selectedOutletId,
          businessDate: businessDate || undefined,
          status: statusFilter,
        }
      : null,
    canReadSessionsPermission,
  )
  const openSessionMutation = useOpenPosSession()
  const closeSessionMutation = useClosePosSession()
  const rows = sessionsQuery.data ?? []
  const effectiveRegionId = selectedRegionId ?? rows[0]?.regionId ?? null
  // Fetch region to get the correct currencyCode — never hardcode.
  const regionQuery = useRegion(effectiveRegionId ?? 0, { enabled: effectiveRegionId !== null })
  const currencyCode = regionQuery.data?.currencyCode ?? null
  const pagedRows = rows.slice(page * size, page * size + size)

  const columns: Array<DataTableColumn<PosSession>> = [
    { key: 'sessionCode', header: 'Session', render: (row) => row.sessionCode },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    { key: 'businessDate', header: 'Business date', render: (row) => row.businessDate },
    { key: 'openedAt', header: 'Opened', render: (row) => formatDateTime(row.openedAt) },
    { key: 'closedAt', header: 'Closed', render: (row) => formatDateTime(row.closedAt) },
    { key: 'expectedCashAmount', header: 'Expected cash', render: (row) => formatMoney(row.expectedCashAmount, row.currencyCode) },
    {
      key: 'actions',
      header: 'Actions',
      render: (row) => (
        <div className="table-actions" onClick={(event) => event.stopPropagation()}>
          <Button asChild size="sm" variant="secondary">
            <Link to={`/pos/sessions/${row.id}`}>Detail</Link>
          </Button>
          {canCloseSessionPermission && canCloseSession(row, isOnline) ? (
            <Button onClick={() => setSessionToClose(row)} size="sm" variant="danger">
              Close
            </Button>
          ) : null}
        </div>
      ),
    },
  ]

  if (!selectedOutletId) {
    return (
      <section className="page-stack">
        <ReadonlyBanner message="Chọn outlet trước khi quản lý POS sessions." />
        <EmptyState
          description="Session Control cần outlet hiện tại để lọc session và gửi workflow actions đúng scope."
          title="POS session context missing"
        />
      </section>
    )
  }

  if (!canReadSessionsPermission) {
    return (
      <section className="page-stack">
        <PermissionDeniedInline
          message="Cần quyền `pos.session.read` để xem danh sách session và điều hướng vào session detail."
          title="Không thể mở Session Control"
        />
      </section>
    )
  }

  return (
    <section className="page-stack">
      {!isOnline ? (
        <ReadonlyBanner message="POS đang offline. Open/close/reconcile session sẽ bị chặn cho đến khi kết nối trở lại." />
      ) : null}

      {reusedSessionCode ? (
        <div className="inline-banner inline-banner-success">
          Backend đã trả lại session đang mở: <strong>{reusedSessionCode}</strong>
        </div>
      ) : null}

      <FormSection description="Mở ca từ Session Control nếu cần giám sát rõ lifecycle tại outlet hiện tại." title="Open session">
        {!isOnline ? (
          <ReadonlyBanner message="Đang offline nên chưa thể mở session mới." />
        ) : !canOpenSessionPermission ? (
          <PermissionDeniedInline
            message="Cần quyền `pos.session.open` để mở ca làm việc mới tại outlet hiện tại."
            title="Không thể mở session"
          />
        ) : null}
        <div className="field-grid">
          <Input label="Region ID" readOnly value={effectiveRegionId ? String(effectiveRegionId) : ''} />
          <Input label="Outlet ID" readOnly value={String(selectedOutletId)} />
          <Input label="Business date" onChange={(event) => setBusinessDate(event.target.value)} type="date" value={businessDate} />
          <Input label="Currency" readOnly value={currencyCode ?? (regionQuery.isLoading ? 'Loading...' : '—')} />
          <div>
            <Input
              label="Terminal ID"
              onChange={(event) => setTerminalId(event.target.value.replace(/[^A-Za-z0-9_-]/g, ''))}
              placeholder="TILL-01"
              value={terminalId}
            />
            <p className="field-hint">
              Optional. Backend uses this for session affinity — same cashier+terminal replays the open session (X-Session-Existed). Max 64 chars, letters/numbers/_/- only.
            </p>
          </div>
        </div>
        <Textarea
          label="Note"
          onChange={(event) => setOpenNote(event.target.value)}
          placeholder="Optional opening note"
          rows={3}
          value={openNote}
        />
        <FormActions
          primaryAction={
            <Button
              disabled={
                !canOpenSessionPermission ||
                !currencyCode ||
                !effectiveRegionId ||
                !businessDate.trim()
              }
              loading={openSessionMutation.isPending || regionQuery.isLoading}
              onClick={async () => {
                // Backend: regionId @NotNull, outletId @NotNull, businessDate @NotNull, currencyCode @NotNull
                if (!currencyCode || !effectiveRegionId || !businessDate.trim()) return
                const result = await openSessionMutation.mutateAsync({
                  regionId: effectiveRegionId,
                  outletId: selectedOutletId,
                  businessDate,
                  currencyCode,
                  terminalId: terminalId || undefined,
                  note: openNote || undefined,
                })
                setReusedSessionCode(result.sessionExisted ? result.session.sessionCode : '')
                setOpenNote('')
                setTerminalId('')
              }}
            >
              Open session
            </Button>
          }
        />
        {openSessionMutation.error ? (
          <ErrorState
            message={openSessionMutation.error instanceof Error ? openSessionMutation.error.message : 'Failed to open session'}
            title="Không thể mở session"
          />
        ) : null}
      </FormSection>

      <FormSection description="Lọc theo business date và status để kiểm tra lifecycle của outlet hiện tại." title="Session filters">
        <div className="field-grid">
          <Input label="Outlet ID" readOnly value={String(selectedOutletId)} />
          <Input
            label="Business date"
            onChange={(event) => {
              setBusinessDate(event.target.value)
              setPage(0)
            }}
            type="date"
            value={businessDate}
          />
          <Select
            label="Status"
            onChange={(event) => {
              setStatusFilter(event.target.value as PosSessionStatus | '')
              setPage(0)
            }}
            options={[
              { label: 'Open', value: 'OPEN' },
              { label: 'Closed', value: 'CLOSED' },
              { label: 'Reconciled', value: 'RECONCILED' },
              { label: 'Cancelled', value: 'CANCELLED' },
            ]}
            placeholder="All statuses"
            value={statusFilter}
          />
        </div>
      </FormSection>

      <DataTable
        columns={columns}
        emptyDescription={
          statusFilter || businessDate
            ? 'Không có session nào khớp business date và status hiện tại.'
            : 'Outlet hiện tại chưa có POS session nào trong phạm vi đang xem.'
        }
        emptyTitle="No POS sessions"
        error={sessionsQuery.error instanceof Error ? sessionsQuery.error.message : null}
        errorTitle="Không thể tải session list"
        loading={sessionsQuery.isLoading}
        loadingDescription="Loading POS sessions for the selected outlet..."
        loadingTitle="Loading sessions"
        onRowClick={(row) => navigate(`/pos/sessions/${row.id}`)}
        onRetry={() => void sessionsQuery.refetch()}
        rowKey={(row) => row.id}
        rows={pagedRows}
      />
      <Pagination
        canNext={(page + 1) * size < rows.length}
        canPrevious={page > 0}
        currentPage={page}
        onNext={() => setPage((value) => value + 1)}
        onPrevious={() => setPage((value) => Math.max(0, value - 1))}
      />

      {closeSessionMutation.error ? (
        <ErrorState
          message={closeSessionMutation.error instanceof Error ? closeSessionMutation.error.message : 'Failed to close session'}
          title="Không thể đóng session"
        />
      ) : null}

      <ConfirmActionDialog
        confirmLabel="Close session"
        danger
        description="Close sẽ fail nếu outlet vẫn còn order OPEN hoặc COMPLETING."
        onCancel={() => setSessionToClose(null)}
        onConfirm={async () => {
          if (!sessionToClose) {
            return
          }

          await closeSessionMutation.mutateAsync(sessionToClose.id)
          setSessionToClose(null)
        }}
        open={sessionToClose !== null}
        title={`Close session ${sessionToClose?.sessionCode ?? ''}?`}
      />
    </section>
  )
}
