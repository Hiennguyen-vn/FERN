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
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import { useRegion, useUpdateRegion } from '../hooks/useOrg'
import { getOrgErrorMessage } from '../services/orgError.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

interface FormState {
  parentRegionId: string
  currencyCode: string
  name: string
  taxCode: string
  timezoneName: string
}

function buildClientErrors(form: FormState): Partial<Record<keyof FormState, string>> {
  const errors: Partial<Record<keyof FormState, string>> = {}
  if (!form.currencyCode.trim()) {
    errors.currencyCode = 'Currency code không được để trống.'
  }
  if (!form.name.trim()) {
    errors.name = 'Tên region không được để trống.'
  }
  if (!form.timezoneName.trim()) {
    errors.timezoneName = 'Timezone không được để trống.'
  }
  if (form.parentRegionId.trim() && !parsePositiveInt(form.parentRegionId)) {
    errors.parentRegionId = 'Parent region ID phải là số nguyên dương.'
  }
  return errors
}

export function RegionEditPage() {
  const { regionId: regionIdParam } = useParams<{ regionId: string }>()
  const regionId = Number(regionIdParam)
  usePageTitle('Edit Region — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canEdit = orgUiPolicy.canOpenRegionEdit(principal)
  const regionQuery = useRegion(regionId, { enabled: canEdit && Number.isFinite(regionId) && regionId > 0 })
  const updateMutation = useUpdateRegion(regionId)
  const { getError: getServerError } = useFieldErrors(updateMutation.error)
  const [clientErrors, setClientErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const [form, setForm] = useState<FormState | null>(null)

  if (!canEdit) {
    return (
      <DashboardLayout title="Edit Region" description="Cập nhật region hiện có.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.write để sửa region." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(regionId) || regionId <= 0) {
    return (
      <DashboardLayout title="Edit Region" description="Cập nhật region hiện có.">
        <EmptyState description="URL không chứa regionId hợp lệ." title="Thiếu regionId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (regionQuery.isLoading) {
    return (
      <DashboardLayout title="Edit Region" description="Cập nhật region hiện có.">
        <Card title="Đang tải region">
          <p className="muted-text">Đang tải region #{regionId} để chỉnh sửa...</p>
        </Card>
      </DashboardLayout>
    )
  }

  if (regionQuery.error) {
    return (
      <DashboardLayout title="Edit Region" description="Cập nhật region hiện có.">
        <ErrorState
          actionLabel="Retry"
          message={getOrgErrorMessage(regionQuery.error, `Không thể tải region #${regionId}.`)}
          onAction={() => void regionQuery.refetch()}
          title="Unable to load region"
        />
      </DashboardLayout>
    )
  }

  if (!regionQuery.data) {
    return (
      <DashboardLayout title="Edit Region" description="Cập nhật region hiện có.">
        <EmptyState description="Region này không tồn tại hoặc nằm ngoài scope hiện tại." title="Region not found" />
      </DashboardLayout>
    )
  }

  const effectiveForm =
    form ??
    {
      parentRegionId: regionQuery.data.parentRegionId?.toString() ?? '',
      currencyCode: regionQuery.data.currencyCode,
      name: regionQuery.data.name,
      taxCode: regionQuery.data.taxCode ?? '',
      timezoneName: regionQuery.data.timezoneName,
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
      parentRegionId: parsePositiveInt(effectiveForm.parentRegionId) ?? null,
      currencyCode: effectiveForm.currencyCode.trim().toUpperCase(),
      name: effectiveForm.name.trim(),
      taxCode: effectiveForm.taxCode.trim() || null,
      timezoneName: effectiveForm.timezoneName.trim(),
    })

    navigate(`/org/regions/${regionId}`)
  }

  return (
    <DashboardLayout
      title="Edit Region"
      description="Publish edit workflow cho regions theo backend public API."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to={`/org/regions/${regionId}`}>Back to region detail</Link>
        </Button>
      }
    >
      <FormSection
        description={`Code của region là immutable ở frontend edit flow này: ${regionQuery.data.code}`}
        title="Region metadata"
      >
        <div className="field-grid">
          <Input label="Code" readOnly value={regionQuery.data.code} />
          <Input {...field('name')} label="Name *" />
          <Input {...field('currencyCode')} label="Currency code *" />
          <Input {...field('timezoneName')} label="Timezone *" />
          <Input {...field('parentRegionId')} label="Parent region ID" type="number" />
          <Input {...field('taxCode')} label="Tax code" />
        </div>
      </FormSection>

      {updateMutation.error ? (
        <p className="error-text">
          {updateMutation.error instanceof Error ? updateMutation.error.message : 'Không thể cập nhật region.'}
        </p>
      ) : null}

      <FormActions
        primaryAction={
          <Button loading={updateMutation.isPending} onClick={() => void handleSubmit()}>
            Save region
          </Button>
        }
        secondaryAction={
          <Button asChild variant="secondary">
            <Link to={`/org/regions/${regionId}`}>Cancel</Link>
          </Button>
        }
      />
    </DashboardLayout>
  )
}
