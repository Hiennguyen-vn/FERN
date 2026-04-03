import { useState } from 'react'
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
  useCancelWasteRecord,
  useCreateWasteRecord,
  usePostWasteRecord,
} from '../hooks/useInventoryCommands'
import type { WasteRecord } from '../model/inventory.types'
import { canCreateWasteRecords } from '../services/inventoryPermission.service'

interface FormState {
  regionId: string
  outletId: string
  ingredientId: string
  qty: string
  businessDate: string
  reason: string
  note: string
}

export function WasteRecordCreatePage() {
  usePageTitle('Waste Record — Inventory')
  const principal = usePrincipal()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canCreate = canCreateWasteRecords(principal)
  const [result, setResult] = useState<WasteRecord | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>({
    regionId: selectedRegionId ? String(selectedRegionId) : '',
    outletId: selectedOutletId ? String(selectedOutletId) : '',
    ingredientId: '',
    qty: '',
    businessDate: new Date().toISOString().slice(0, 10),
    reason: '',
    note: '',
  })
  const createMutation = useCreateWasteRecord()
  const postMutation = usePostWasteRecord()
  const cancelMutation = useCancelWasteRecord()

  if (!canCreate) {
    return (
      <DashboardLayout title="Waste Record" description="Create, post, and cancel waste records.">
        <PermissionDeniedInline message="Bạn cần quyền inventory.waste.write để mở waste workflow." />
      </DashboardLayout>
    )
  }

  async function handleCreate() {
    setError(null)
    const regionId = parsePositiveInt(form.regionId)
    const outletId = parsePositiveInt(form.outletId)
    const ingredientId = parsePositiveInt(form.ingredientId)
    const qty = Number(form.qty)

    if (!regionId || !outletId || !ingredientId || !Number.isFinite(qty) || qty <= 0 || !form.reason.trim() || !form.businessDate) {
      setError('Region ID, Outlet ID, Ingredient ID, qty, business date và reason là bắt buộc.')
      return
    }

    const created = await createMutation.mutateAsync({
      regionId,
      outletId,
      ingredientId,
      qty,
      businessDate: form.businessDate,
      reason: form.reason.trim(),
      note: form.note.trim() || null,
    })
    setResult(created)
  }

  return (
    <DashboardLayout
      title="Waste Record"
      description="Waste record workflow được publish trực tiếp theo backend public API."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/inventory/transactions">Back to transactions</Link>
        </Button>
      }
    >
      {error ? <p className="error-text">{error}</p> : null}
      {createMutation.error || postMutation.error || cancelMutation.error ? (
        <p className="error-text">
          {createMutation.error instanceof Error
            ? createMutation.error.message
            : postMutation.error instanceof Error
              ? postMutation.error.message
              : cancelMutation.error instanceof Error
                ? cancelMutation.error.message
                : 'Không thể xử lý waste record.'}
        </p>
      ) : null}

      <FormSection title="Create waste record" description="Tạo waste record ở trạng thái DRAFT.">
        <div className="field-grid">
          <Input label="Region ID *" onChange={(event) => setForm((prev) => ({ ...prev, regionId: event.target.value }))} type="number" value={form.regionId} />
          <Input label="Outlet ID *" onChange={(event) => setForm((prev) => ({ ...prev, outletId: event.target.value }))} type="number" value={form.outletId} />
          <Input label="Ingredient ID *" onChange={(event) => setForm((prev) => ({ ...prev, ingredientId: event.target.value }))} type="number" value={form.ingredientId} />
          <Input label="Qty *" onChange={(event) => setForm((prev) => ({ ...prev, qty: event.target.value }))} type="number" value={form.qty} />
          <Input label="Business date *" onChange={(event) => setForm((prev) => ({ ...prev, businessDate: event.target.value }))} type="date" value={form.businessDate} />
          <Input label="Reason *" onChange={(event) => setForm((prev) => ({ ...prev, reason: event.target.value }))} value={form.reason} />
          <Input label="Note" onChange={(event) => setForm((prev) => ({ ...prev, note: event.target.value }))} value={form.note} />
        </div>
        <FormActions
          primaryAction={
            <Button loading={createMutation.isPending} onClick={() => void handleCreate()} type="button">
              Create waste record
            </Button>
          }
        />
      </FormSection>

      {result ? (
        <Card title={`Waste record #${result.id}`}>
          <div className="meta-grid">
            <span>Status: {result.status}</span>
            <span>Ingredient: #{result.ingredientId}</span>
            <span>Qty: {result.qty}</span>
            <span>Reason: {result.reason}</span>
            <span>Posted at: {result.postedAt ?? 'Not posted'}</span>
          </div>
          <FormActions
            primaryAction={
              result.status === 'DRAFT' ? (
                <Button
                  loading={postMutation.isPending}
                  onClick={() =>
                    void postMutation.mutateAsync(result.id).then((updated) => setResult(updated))
                  }
                  type="button"
                >
                  Post waste record
                </Button>
              ) : null
            }
            secondaryAction={
              result.status === 'DRAFT' ? (
                <Button
                  loading={cancelMutation.isPending}
                  onClick={() =>
                    void cancelMutation.mutateAsync(result.id).then((updated) => setResult(updated))
                  }
                  type="button"
                  variant="secondary"
                >
                  Cancel waste record
                </Button>
              ) : null
            }
          />
        </Card>
      ) : null}
    </DashboardLayout>
  )
}
