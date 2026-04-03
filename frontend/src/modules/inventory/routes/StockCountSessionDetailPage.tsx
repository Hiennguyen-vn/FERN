import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { Button, Card, FormActions, Input, PermissionDeniedInline } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import {
  useCancelStockCountSession,
  usePostStockCountSession,
  useStartStockCountSession,
  useUpdateStockCountLines,
} from '../hooks/useInventoryCommands'
import { useStockCountSessionDetail } from '../hooks/useStockCountSessionDetail'
import type { StockCountLine, StockCountSession } from '../model/inventory.types'
import {
  canCreateStockCountSessions,
  canPostStockCountSessions,
  canReadStockBalances,
} from '../services/inventoryPermission.service'

interface LineDraft {
  ingredientId: number
  actualQty: string
  note: string
}

function buildLineDrafts(lines: StockCountLine[]): LineDraft[] {
  return lines.map((line) => ({
    ingredientId: line.ingredientId,
    actualQty: line.actualQty != null ? String(line.actualQty) : '',
    note: line.note ?? '',
  }))
}

export function StockCountSessionDetailPage() {
  const { sessionId: sessionIdParam } = useParams<{ sessionId: string }>()
  const sessionId = parsePositiveInt(sessionIdParam ?? '')
  const principal = usePrincipal()
  const canRead = canReadStockBalances(principal)
  const canMutate = canCreateStockCountSessions(principal)
  const canPost = canPostStockCountSessions(principal)

  const query = useStockCountSessionDetail(sessionId)
  const [session, setSession] = useState<StockCountSession | null>(null)
  const [lineDrafts, setLineDrafts] = useState<LineDraft[]>([])

  const startMutation = useStartStockCountSession()
  const postMutation = usePostStockCountSession()
  const cancelMutation = useCancelStockCountSession()
  const updateLinesMutation = useUpdateStockCountLines(sessionId ?? 0)

  usePageTitle(session ? `Stock count #${session.id}` : 'Stock count session')

  useEffect(() => {
    if (query.data) {
      setSession(query.data)
      setLineDrafts(buildLineDrafts(query.data.lines))
    }
  }, [query.data])

  if (!sessionId) {
    return (
      <DashboardLayout title="Stock count session" description="Invalid session id.">
        <p className="error-text">Session id không hợp lệ.</p>
        <Button asChild variant="secondary">
          <Link to="/inventory/stock-count-sessions">Back to list</Link>
        </Button>
      </DashboardLayout>
    )
  }

  if (!canRead) {
    return (
      <DashboardLayout title="Stock count session" description="Chi tiết phiên kiểm kê.">
        <PermissionDeniedInline message="Bạn cần quyền inventory.balance.read để xem phiên kiểm kê." />
      </DashboardLayout>
    )
  }

  if (query.isLoading) {
    return (
      <DashboardLayout title="Stock count session" description="Đang tải…">
        <p className="muted-text">Loading…</p>
      </DashboardLayout>
    )
  }

  if (query.error || !session) {
    return (
      <DashboardLayout title="Stock count session" description="Không tải được phiên.">
        <p className="error-text">
          {query.error instanceof Error ? query.error.message : 'Không tìm thấy hoặc không có quyền.'}
        </p>
        <Button asChild variant="secondary">
          <Link to="/inventory/stock-count-sessions">Back to list</Link>
        </Button>
      </DashboardLayout>
    )
  }

  async function handleSaveLines() {
    const updated = await updateLinesMutation.mutateAsync({
      lines: lineDrafts.map((line) => ({
        ingredientId: line.ingredientId,
        actualQty: Number(line.actualQty),
        note: line.note.trim() || null,
      })),
    })
    setSession(updated)
    setLineDrafts(buildLineDrafts(updated.lines))
  }

  return (
    <DashboardLayout
      title={`Stock count #${session.id}`}
      description="Theo trạng thái backend: DRAFT → COUNTING → POSTED hoặc CANCELLED."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/inventory/stock-count-sessions">All sessions</Link>
        </Button>
      }
    >
      {startMutation.error || updateLinesMutation.error || postMutation.error || cancelMutation.error ? (
        <p className="error-text">
          {startMutation.error instanceof Error
            ? startMutation.error.message
            : updateLinesMutation.error instanceof Error
              ? updateLinesMutation.error.message
              : postMutation.error instanceof Error
                ? postMutation.error.message
                : cancelMutation.error instanceof Error
                  ? cancelMutation.error.message
                  : 'Không thể cập nhật session.'}
        </p>
      ) : null}

      <Card title={`Session #${session.id}`}>
        <div className="meta-grid">
          <span>Status: {session.status}</span>
          <span>Count date: {session.countDate}</span>
          <span>Started at: {session.startedAt ?? 'Not started'}</span>
          <span>Posted at: {session.postedAt ?? 'Not posted'}</span>
        </div>

        {canMutate ? (
          <FormActions
            primaryAction={
              session.status === 'DRAFT' ? (
                <Button
                  loading={startMutation.isPending}
                  onClick={() =>
                    void startMutation.mutateAsync(session.id).then((updated) => {
                      setSession(updated)
                      setLineDrafts(buildLineDrafts(updated.lines))
                    })
                  }
                  type="button"
                >
                  Start count session
                </Button>
              ) : session.status === 'COUNTING' ? (
                <Button loading={updateLinesMutation.isPending} onClick={() => void handleSaveLines()} type="button">
                  Save count lines
                </Button>
              ) : null
            }
            secondaryAction={
              session.status === 'DRAFT' || session.status === 'COUNTING' ? (
                <Button
                  loading={cancelMutation.isPending}
                  onClick={() =>
                    void cancelMutation.mutateAsync(session.id).then((updated) => setSession(updated))
                  }
                  type="button"
                  variant="secondary"
                >
                  Cancel session
                </Button>
              ) : null
            }
          />
        ) : null}

        {session.status === 'COUNTING' && canMutate ? (
          <div className="page-stack">
            <h3>Count lines</h3>
            <div className="field-grid">
              {lineDrafts.map((line, index) => (
                <Card key={line.ingredientId} title={`Ingredient #${line.ingredientId}`}>
                  <div className="field-grid">
                    <Input
                      label="Actual qty"
                      onChange={(event) =>
                        setLineDrafts((current) =>
                          current.map((item, currentIndex) =>
                            currentIndex === index ? { ...item, actualQty: event.target.value } : item,
                          ),
                        )
                      }
                      type="number"
                      value={line.actualQty}
                    />
                    <Input
                      label="Note"
                      onChange={(event) =>
                        setLineDrafts((current) =>
                          current.map((item, currentIndex) =>
                            currentIndex === index ? { ...item, note: event.target.value } : item,
                          ),
                        )
                      }
                      value={line.note}
                    />
                  </div>
                </Card>
              ))}
            </div>
            {canPost ? (
              <Button
                loading={postMutation.isPending}
                onClick={() =>
                  void postMutation.mutateAsync(session.id).then((updated) => setSession(updated))
                }
                type="button"
              >
                Post count session
              </Button>
            ) : null}
          </div>
        ) : null}

        {session.lines.length > 0 && !(session.status === 'COUNTING' && canMutate) ? (
          <div className="page-stack">
            <h3>Lines</h3>
            <div className="meta-grid meta-grid-compact">
              {session.lines.map((line) => (
                <div key={line.ingredientId}>
                  #{line.ingredientId}: system {line.systemQty}, actual {line.actualQty ?? '—'}, variance{' '}
                  {line.varianceQty ?? '—'}
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </Card>
    </DashboardLayout>
  )
}
