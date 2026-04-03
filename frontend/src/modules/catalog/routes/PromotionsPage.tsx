import { useMemo, useState } from 'react'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  DataTable,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  Select,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import {
  useCreatePromotion,
  useDeactivatePromotion,
  usePromotions,
  useUpdatePromotion,
} from '../hooks/usePromotions'
import type {
  PriceScopeType,
  Promotion,
  PromotionType,
  PromotionUpsertRequest,
} from '../model/catalog.types'
import { getCatalogErrorMessage } from '../services/catalogError.service'
import {
  canReadPromotions,
  canWritePromotions,
} from '../services/catalogPermission.service'

const SCOPE_OPTIONS: SelectOption[] = [
  { label: 'GLOBAL', value: 'GLOBAL' },
  { label: 'COUNTRY', value: 'COUNTRY' },
  { label: 'REGION', value: 'REGION' },
  { label: 'OUTLET', value: 'OUTLET' },
]

const TYPE_OPTIONS: SelectOption[] = [
  { label: 'Percent discount', value: 'PERCENT_DISCOUNT' },
  { label: 'Fixed discount', value: 'FIXED_DISCOUNT' },
  { label: 'Buy X Get Y', value: 'BUY_X_GET_Y' },
  { label: 'Free item', value: 'FREE_ITEM' },
]

interface PromotionFormState {
  code: string
  name: string
  description: string
  promotionType: PromotionType | string
  discountPercent: string
  discountAmount: string
  scopeType: PriceScopeType | string
  scopeId: string
  minOrderAmount: string
  maxUsageTotal: string
  effectiveFrom: string
  effectiveTo: string
}

const INITIAL_FORM: PromotionFormState = {
  code: '',
  name: '',
  description: '',
  promotionType: 'PERCENT_DISCOUNT',
  discountPercent: '',
  discountAmount: '',
  scopeType: 'GLOBAL',
  scopeId: '',
  minOrderAmount: '',
  maxUsageTotal: '',
  effectiveFrom: new Date().toISOString().slice(0, 10),
  effectiveTo: '',
}

function toPayload(form: PromotionFormState): PromotionUpsertRequest {
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim() || null,
    promotionType: form.promotionType,
    discountPercent: form.discountPercent.trim() ? Number(form.discountPercent) : null,
    discountAmount: form.discountAmount.trim() ? Number(form.discountAmount) : null,
    scopeType: form.scopeType,
    scopeId: form.scopeType === 'GLOBAL' ? null : Number(parsePositiveInt(form.scopeId) ?? 0),
    minOrderAmount: form.minOrderAmount.trim() ? Number(form.minOrderAmount) : null,
    maxUsageTotal: form.maxUsageTotal.trim() ? Number(form.maxUsageTotal) : null,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || null,
  }
}

function fromPromotion(promotion: Promotion): PromotionFormState {
  return {
    code: promotion.code,
    name: promotion.name,
    description: promotion.description ?? '',
    promotionType: promotion.promotionType,
    discountPercent: promotion.discountPercent?.toString() ?? '',
    discountAmount: promotion.discountAmount?.toString() ?? '',
    scopeType: promotion.scopeType,
    scopeId: promotion.scopeId?.toString() ?? '',
    minOrderAmount: promotion.minOrderAmount?.toString() ?? '',
    maxUsageTotal: promotion.maxUsageTotal?.toString() ?? '',
    effectiveFrom: promotion.effectiveFrom.slice(0, 10),
    effectiveTo: promotion.effectiveTo?.slice(0, 10) ?? '',
  }
}

function buildClientError(form: PromotionFormState) {
  if (!form.code.trim()) {
    return 'Promotion code không được để trống.'
  }
  if (!form.name.trim()) {
    return 'Promotion name không được để trống.'
  }
  if (!form.effectiveFrom) {
    return 'Effective from là bắt buộc.'
  }
  if (form.scopeType !== 'GLOBAL' && !parsePositiveInt(form.scopeId)) {
    return 'Scope ID phải là số nguyên dương khi scope không phải GLOBAL.'
  }
  if (!form.discountPercent.trim() && !form.discountAmount.trim()) {
    return 'Cần ít nhất một giá trị discountPercent hoặc discountAmount.'
  }
  return null
}

export function PromotionsPage() {
  usePageTitle('Promotions — Catalog')
  const principal = usePrincipal()
  const canRead = canReadPromotions(principal)
  const canWrite = canWritePromotions(principal)
  const hasSystemScope = principal?.scopeRoots?.system === true
  const promotionsQuery = usePromotions({}, { enabled: canRead && hasSystemScope })
  const createMutation = useCreatePromotion()
  const [editingPromotion, setEditingPromotion] = useState<Promotion | null>(null)
  const [form, setForm] = useState<PromotionFormState>(INITIAL_FORM)
  const [clientError, setClientError] = useState<string | null>(null)
  const updateMutation = useUpdatePromotion(editingPromotion?.id ?? 0)
  const deactivateMutation = useDeactivatePromotion()

  const columns = useMemo<Array<DataTableColumn<Promotion>>>(
    () => [
      { key: 'code', header: 'Code', render: (promotion) => promotion.code },
      { key: 'name', header: 'Name', render: (promotion) => promotion.name },
      { key: 'type', header: 'Type', render: (promotion) => promotion.promotionType },
      { key: 'scope', header: 'Scope', render: (promotion) => `${promotion.scopeType}${promotion.scopeId ? ` #${promotion.scopeId}` : ''}` },
      { key: 'discount', header: 'Discount', render: (promotion) => promotion.discountPercent != null ? `${promotion.discountPercent}%` : promotion.discountAmount != null ? `${promotion.discountAmount}` : '—' },
      { key: 'status', header: 'Status', render: (promotion) => promotion.status },
      {
        key: 'actions',
        header: 'Actions',
        render: (promotion) => (
          <div className="form-actions align-start">
            {canWrite ? (
              <Button
                onClick={() => {
                  setEditingPromotion(promotion)
                  setForm(fromPromotion(promotion))
                  setClientError(null)
                }}
                size="sm"
                type="button"
                variant="secondary"
              >
                Edit
              </Button>
            ) : null}
            {canWrite && promotion.status === 'ACTIVE' ? (
              <Button
                loading={deactivateMutation.isPending}
                onClick={() => void deactivateMutation.mutateAsync(promotion.id)}
                size="sm"
                type="button"
                variant="ghost"
              >
                Deactivate
              </Button>
            ) : null}
          </div>
        ),
      },
    ],
    [canWrite, deactivateMutation],
  )

  if (!canRead && !canWrite) {
    return (
      <DashboardLayout title="Promotions" description="Promotion management surface for catalog.">
        <PermissionDeniedInline message="Bạn cần catalog.promotion.read hoặc catalog.promotion.write để mở promotions." />
      </DashboardLayout>
    )
  }

  if (!hasSystemScope) {
    return (
      <DashboardLayout title="Promotions" description="Promotion management surface for catalog.">
        <PermissionDeniedInline message="Promotions yêu cầu system scope vì backend enforce catalog.promotion.read/write ở phạm vi system." />
      </DashboardLayout>
    )
  }

  async function submitForm() {
    const error = buildClientError(form)
    if (error) {
      setClientError(error)
      return
    }

    const payload = toPayload(form)
    if (editingPromotion) {
      await updateMutation.mutateAsync(payload)
    } else {
      await createMutation.mutateAsync(payload)
    }

    setEditingPromotion(null)
    setForm(INITIAL_FORM)
    setClientError(null)
  }

  return (
    <DashboardLayout
      title="Promotions"
      description="Frontend promotions surface được publish theo backend public API của catalog-service."
    >
      {clientError ? <p className="error-text">{clientError}</p> : null}
      {createMutation.error || updateMutation.error || deactivateMutation.error ? (
        <p className="error-text">
          {createMutation.error instanceof Error
            ? createMutation.error.message
            : updateMutation.error instanceof Error
              ? updateMutation.error.message
              : deactivateMutation.error instanceof Error
                ? deactivateMutation.error.message
                : 'Không thể xử lý promotion.'}
        </p>
      ) : null}

      {canWrite ? (
        <FormSection
          description="Create hoặc update promotion theo backend PromotionUpsertRequest."
          title={editingPromotion ? `Edit promotion #${editingPromotion.id}` : 'Create promotion'}
        >
          <div className="field-grid">
            <Input label="Code *" onChange={(event) => setForm((prev) => ({ ...prev, code: event.target.value.toUpperCase() }))} value={form.code} />
            <Input label="Name *" onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} value={form.name} />
            <Select label="Promotion type" onChange={(event) => setForm((prev) => ({ ...prev, promotionType: event.target.value }))} options={TYPE_OPTIONS} value={form.promotionType} />
            <Select label="Scope type" onChange={(event) => setForm((prev) => ({ ...prev, scopeType: event.target.value }))} options={SCOPE_OPTIONS} value={form.scopeType} />
            <Input disabled={form.scopeType === 'GLOBAL'} label="Scope ID" onChange={(event) => setForm((prev) => ({ ...prev, scopeId: event.target.value }))} type="number" value={form.scopeId} />
            <Input label="Discount percent" onChange={(event) => setForm((prev) => ({ ...prev, discountPercent: event.target.value }))} type="number" value={form.discountPercent} />
            <Input label="Discount amount" onChange={(event) => setForm((prev) => ({ ...prev, discountAmount: event.target.value }))} type="number" value={form.discountAmount} />
            <Input label="Min order amount" onChange={(event) => setForm((prev) => ({ ...prev, minOrderAmount: event.target.value }))} type="number" value={form.minOrderAmount} />
            <Input label="Max usage total" onChange={(event) => setForm((prev) => ({ ...prev, maxUsageTotal: event.target.value }))} type="number" value={form.maxUsageTotal} />
            <Input label="Effective from *" onChange={(event) => setForm((prev) => ({ ...prev, effectiveFrom: event.target.value }))} type="date" value={form.effectiveFrom} />
            <Input label="Effective to" onChange={(event) => setForm((prev) => ({ ...prev, effectiveTo: event.target.value }))} type="date" value={form.effectiveTo} />
            <Input label="Description" onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))} value={form.description} />
          </div>

          <FormActions
            primaryAction={
              <Button
                loading={createMutation.isPending || updateMutation.isPending}
                onClick={() => void submitForm()}
                type="button"
              >
                {editingPromotion ? 'Update promotion' : 'Create promotion'}
              </Button>
            }
            secondaryAction={
              editingPromotion ? (
                <Button
                  onClick={() => {
                    setEditingPromotion(null)
                    setForm(INITIAL_FORM)
                    setClientError(null)
                  }}
                  type="button"
                  variant="secondary"
                >
                  Cancel edit
                </Button>
              ) : null
            }
          />
        </FormSection>
      ) : null}

      {canRead ? (
        <Card title="Promotion list">
          <DataTable
            columns={columns}
            emptyDescription="Chưa có promotion nào trong catalog."
            emptyTitle="No promotions"
            error={promotionsQuery.error ? getCatalogErrorMessage(promotionsQuery.error, 'Không thể tải promotions.') : null}
            loading={promotionsQuery.isLoading}
            loadingDescription="Đang tải promotion list..."
            loadingTitle="Đang tải promotions"
            onRetry={() => void promotionsQuery.refetch()}
            rowKey={(promotion) => promotion.id}
            rows={promotionsQuery.data ?? []}
          />
        </Card>
      ) : (
        <Card title="Promotion list">
          <p className="muted-text">Current principal có quyền write nhưng không có catalog.promotion.read, nên page chỉ publish form tạo/sửa.</p>
        </Card>
      )}
    </DashboardLayout>
  )
}
