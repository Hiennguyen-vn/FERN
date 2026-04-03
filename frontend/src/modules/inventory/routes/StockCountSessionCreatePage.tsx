import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import {
  useCancelStockCountSession,
  useCreateStockCountSession,
  usePostStockCountSession,
  useStartStockCountSession,
  useUpdateStockCountLines,
} from '../hooks/useInventoryCommands'
import type { StockCountLine, StockCountSession } from '../model/inventory.types'
import {
  canCreateStockCountSessions,
  canPostStockCountSessions,
} from '../services/inventoryPermission.service'

interface FormState {
  regionId: string
  outletId: string
  countDate: string
  note: string
  ingredientIds: string
}

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

export function StockCountSessionCreatePage() {
  usePageTitle('Stock Count Session — Inventory')
  const principal = usePrincipal()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canCreate = canCreateStockCountSessions(principal)
  const canPost = canPostStockCountSessions(principal)
  const [result, setResult] = useState<StockCountSession | null>(null)
  const [lineDrafts, setLineDrafts] = useState<LineDraft[]>([])
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>({
    regionId: selectedRegionId ? String(selectedRegionId) : '',
    outletId: selectedOutletId ? String(selectedOutletId) : '',
    countDate: new Date().toISOString().slice(0, 10),
    note: '',
    ingredientIds: '',
  })
  const createMutation = useCreateStockCountSession()
  const startMutation = useStartStockCountSession()
  const postMutation = usePostStockCountSession()
  const cancelMutation = useCancelStockCountSession()
  const updateLinesMutation = useUpdateStockCountLines(result?.id ?? 0)

  const ingredientIds = useMemo(
    () =>
      form.ingredientIds
        .split(',')
        .map((value) => parsePositiveInt(value.trim()))
        .filter((value): value is number => Boolean(value)),
    [form.ingredientIds],
  )

  if (!canCreate) {
    return (
      <DashboardLayout title="Stock Count Session" description="Create, start, update, post, and cancel stock counts.">
        <PermissionDeniedInline message="Bạn cần quyền inventory.stock_count.write để mở stock count workflow." />
      </DashboardLayout>
    )
  }

  async function handleCreate() {
    setError(null)
    const regionId = parsePositiveInt(form.regionId)
    const outletId = parsePositiveInt(form.outletId)
    if (!regionId || !outletId || !form.countDate || ingredientIds.length === 0) {
      setError('Region ID, Outlet ID, count date và ít nhất một ingredient ID là bắt buộc.')
      return
    }

    const created = await createMutation.mutateAsync({
      regionId,
      outletId,
      countDate: form.countDate,
      note: form.note.trim() || null,
      ingredientIds,
    })
    setResult(created)
    setLineDrafts(buildLineDrafts(created.lines))
  }

  async function handleSaveLines() {
    if (!result) {
      return
    }
    const updated = await updateLinesMutation.mutateAsync({
      lines: lineDrafts.map((line) => ({
        ingredientId: line.ingredientId,
        actualQty: Number(line.actualQty),
        note: line.note.trim() || null,
      })),
    })
    setResult(updated)
    setLineDrafts(buildLineDrafts(updated.lines))
  }

  return (
    <DashboardLayout
      title="Stock Count Session"
      description="Stock count workflow được publish trực tiếp theo backend public API."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/inventory/stock-balances">Stock overview</Link>
          </Button>
          <Button asChild size="sm" variant="secondary">
            <Link to="/inventory/stock-count-sessions">All sessions</Link>
          </Button>
        </div>
      }
    >
      {error ? <p className="error-text">{error}</p> : null}
      {createMutation.error || startMutation.error || updateLinesMutation.error || postMutation.error || cancelMutation.error ? (
        <p className="error-text">
          {createMutation.error instanceof Error
            ? createMutation.error.message
            : startMutation.error instanceof Error
              ? startMutation.error.message
              : updateLinesMutation.error instanceof Error
                ? updateLinesMutation.error.message
                : postMutation.error instanceof Error
                  ? postMutation.error.message
                  : cancelMutation.error instanceof Error
                    ? cancelMutation.error.message
                    : 'Không thể xử lý stock count session.'}
        </p>
      ) : null}

      <FormSection title="Create stock count session" description="Ingredient IDs nhập dạng comma-separated, ví dụ: 101,102,103.">
        <div className="field-grid">
          <Input label="Region ID *" onChange={(event) => setForm((prev) => ({ ...prev, regionId: event.target.value }))} type="number" value={form.regionId} />
          <Input label="Outlet ID *" onChange={(event) => setForm((prev) => ({ ...prev, outletId: event.target.value }))} type="number" value={form.outletId} />
          <Input label="Count date *" onChange={(event) => setForm((prev) => ({ ...prev, countDate: event.target.value }))} type="date" value={form.countDate} />
          <Input label="Ingredient IDs *" onChange={(event) => setForm((prev) => ({ ...prev, ingredientIds: event.target.value }))} placeholder="101,102,103" value={form.ingredientIds} />
          <Input label="Note" onChange={(event) => setForm((prev) => ({ ...prev, note: event.target.value }))} value={form.note} />
        </div>
        <FormActions
          primaryAction={
            <Button loading={createMutation.isPending} onClick={() => void handleCreate()} type="button">
              Create count session
            </Button>
          }
        />
      </FormSection>

      {result ? (
        <Card title={`Stock count session #${result.id}`}>
          <div className="meta-grid">
            <span>Status: {result.status}</span>
            <span>Count date: {result.countDate}</span>
            <span>Started at: {result.startedAt ?? 'Not started'}</span>
            <span>Posted at: {result.postedAt ?? 'Not posted'}</span>
          </div>

          <FormActions
            primaryAction={
              result.status === 'DRAFT' ? (
                <Button
                  loading={startMutation.isPending}
                  onClick={() =>
                    void startMutation.mutateAsync(result.id).then((updated) => {
                      setResult(updated)
                      setLineDrafts(buildLineDrafts(updated.lines))
                    })
                  }
                  type="button"
                >
                  Start count session
                </Button>
              ) : result.status === 'COUNTING' ? (
                <Button
                  loading={updateLinesMutation.isPending}
                  onClick={() => void handleSaveLines()}
                  type="button"
                >
                  Save count lines
                </Button>
              ) : null
            }
            secondaryAction={
              result.status === 'DRAFT' || result.status === 'COUNTING' ? (
                <Button
                  loading={cancelMutation.isPending}
                  onClick={() =>
                    void cancelMutation.mutateAsync(result.id).then((updated) => setResult(updated))
                  }
                  type="button"
                  variant="secondary"
                >
                  Cancel session
                </Button>
              ) : null
            }
          />

          {result.status === 'COUNTING' ? (
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
                    void postMutation.mutateAsync(result.id).then((updated) => setResult(updated))
                  }
                  type="button"
                >
                  Post count session
                </Button>
              ) : null}
            </div>
          ) : null}
        </Card>
      ) : null}
    </DashboardLayout>
  )
}
