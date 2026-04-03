import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useFieldErrors } from '@core/api/useFieldErrors'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  Select,
} from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import { useOutlet, useUpdateOutlet } from '../hooks/useOrg'
import type { OrgOutletStatus } from '../model/org.types'
import { getOrgErrorMessage } from '../services/orgError.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

const STATUS_OPTIONS: SelectOption[] = [
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Closed', value: 'CLOSED' },
]

interface FormState {
  regionId: string
  name: string
  status: OrgOutletStatus
  address: string
  phone: string
  email: string
  openedAt: string
  closedAt: string
}

function buildClientErrors(form: FormState): Partial<Record<keyof FormState, string>> {
  const errors: Partial<Record<keyof FormState, string>> = {}
  if (!parsePositiveInt(form.regionId)) {
    errors.regionId = 'Region ID là số nguyên dương.'
  }
  if (!form.name.trim()) {
    errors.name = 'Tên outlet không được để trống.'
  }
  return errors
}

export function OutletEditPage() {
  const { outletId: outletIdParam } = useParams<{ outletId: string }>()
  const outletId = Number(outletIdParam)
  usePageTitle('Edit Outlet — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canEdit = orgUiPolicy.canOpenOutletEdit(principal)
  const outletQuery = useOutlet(outletId, { enabled: canEdit && Number.isFinite(outletId) && outletId > 0 })
  const updateMutation = useUpdateOutlet(outletId)
  const { getError: getServerError } = useFieldErrors(updateMutation.error)
  const [clientErrors, setClientErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const [form, setForm] = useState<FormState | null>(null)

  if (!canEdit) {
    return (
      <DashboardLayout title="Edit Outlet" description="Cập nhật outlet hiện có.">
        <PermissionDeniedInline message="Bạn cần quyền org.outlet.write để sửa outlet." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(outletId) || outletId <= 0) {
    return (
      <DashboardLayout title="Edit Outlet" description="Cập nhật outlet hiện có.">
        <EmptyState description="URL không chứa outletId hợp lệ." title="Thiếu outletId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (outletQuery.isLoading) {
    return (
      <DashboardLayout title="Edit Outlet" description="Cập nhật outlet hiện có.">
        <Card title="Đang tải outlet">
          <p className="muted-text">Đang tải outlet #{outletId} để chỉnh sửa...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (outletQuery.error) {
    return (
      <DashboardLayout title="Edit Outlet" description="Cập nhật outlet hiện có.">
        <ErrorState
          actionLabel="Retry"
          message={getOrgErrorMessage(outletQuery.error, `Không thể tải outlet #${outletId}.`)}
          onAction={() => void outletQuery.refetch()}
          title="Unable to load outlet"
        />
      </DashboardLayout>
    )
  }

  if (!outletQuery.data) {
    return (
      <DashboardLayout title="Edit Outlet" description="Cập nhật outlet hiện có.">
        <EmptyState description="Outlet này không tồn tại hoặc nằm ngoài scope hiện tại." title="Outlet not found" />
      </DashboardLayout>
    )
  }

  const effectiveForm =
    form ??
    {
      regionId: outletQuery.data.regionId.toString(),
      name: outletQuery.data.name,
      status: outletQuery.data.status,
      address: outletQuery.data.address ?? '',
      phone: outletQuery.data.phone ?? '',
      email: outletQuery.data.email ?? '',
      openedAt: outletQuery.data.openedAt ?? '',
      closedAt: outletQuery.data.closedAt ?? '',
    }

  function field(key: keyof FormState) {
    return {
      value: effectiveForm[key],
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
        setForm((prev) => ({ ...(prev ?? effectiveForm), [key]: e.target.value }))
        if (clientErrors[key]) {
          setClientErrors((prev) => ({ ...prev, [key]: undefined }))
        }
      },
      error: clientErrors[key] ?? getServerError(key),
    }
  }

  async function handleSubmit() {
    const errors = buildClientErrors(effectiveForm)
    if (Object.keys(errors).length > 0) {
      setClientErrors(errors)
      return
    }

    await updateMutation.mutateAsync({
      regionId: Number(effectiveForm.regionId),
      name: effectiveForm.name.trim(),
      status: effectiveForm.status,
      address: effectiveForm.address.trim() || null,
      phone: effectiveForm.phone.trim() || null,
      email: effectiveForm.email.trim() || null,
      openedAt: effectiveForm.openedAt || null,
      closedAt: effectiveForm.closedAt || null,
    })

    navigate(`/org/outlets/${outletId}`)
  }

  return (
    <DashboardLayout
      title="Edit Outlet"
      description="Publish edit workflow cho outlets theo backend public API."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to={`/org/outlets/${outletId}`}>Back to outlet detail</Link>
        </Button>
      }
    >
      <FormSection
        description={`Code của outlet là immutable ở frontend edit flow này: ${outletQuery.data.code}`}
        title="Outlet identity"
      >
        <div className="field-grid">
          <Input label="Code" readOnly value={outletQuery.data.code} />
          <Input {...field('regionId')} label="Region ID *" type="number" />
          <Input {...field('name')} label="Name *" />
          <Select
            label="Status *"
            onChange={(event) => setForm((prev) => ({ ...(prev ?? effectiveForm), status: event.target.value as OrgOutletStatus }))}
            options={STATUS_OPTIONS}
            value={effectiveForm.status}
          />
        </div>
      </FormSection>

      <FormSection title="Contact" description="Contact metadata của outlet.">
        <div className="field-grid">
          <Input {...field('address')} label="Address" />
          <Input {...field('phone')} label="Phone" />
          <Input {...field('email')} label="Email" type="email" />
        </div>
      </FormSection>

      <FormSection title="Operational dates" description="Ngày khai trương và ngày đóng cửa.">
        <div className="field-grid">
          <Input {...field('openedAt')} label="Opened at" type="date" />
          <Input {...field('closedAt')} label="Closed at" type="date" />
        </div>
      </FormSection>

      {updateMutation.error ? (
        <p className="error-text">
          {updateMutation.error instanceof Error ? updateMutation.error.message : 'Không thể cập nhật outlet.'}
        </p>
      ) : null}

      <FormActions
        primaryAction={
          <Button loading={updateMutation.isPending} onClick={() => void handleSubmit()}>
            Save outlet
          </Button>
        }
        secondaryAction={
          <Button asChild variant="secondary">
            <Link to={`/org/outlets/${outletId}`}>Cancel</Link>
          </Button>
        }
      />
    </DashboardLayout>
  )
}
