import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useFieldErrors } from '@core/api/useFieldErrors'
import {
  Button,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import { useCreateRegion } from '../hooks/useOrg'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

interface FormState {
  code: string
  parentRegionId: string
  currencyCode: string
  name: string
  taxCode: string
  timezoneName: string
}

const INITIAL_FORM: FormState = {
  code: '',
  parentRegionId: '',
  currencyCode: 'VND',
  name: '',
  taxCode: '',
  timezoneName: 'Asia/Ho_Chi_Minh',
}

function buildClientErrors(form: FormState): Partial<Record<keyof FormState, string>> {
  const errors: Partial<Record<keyof FormState, string>> = {}
  if (!form.code.trim()) {
    errors.code = 'Code không được để trống.'
  }
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

export function RegionCreatePage() {
  usePageTitle('Create Region — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canCreate = orgUiPolicy.canOpenRegionCreate(principal)
  const [form, setForm] = useState<FormState>(INITIAL_FORM)
  const [clientErrors, setClientErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const createMutation = useCreateRegion()
  const { getError: getServerError } = useFieldErrors(createMutation.error)

  function field(key: keyof FormState) {
    return {
      value: form[key],
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
        setForm((prev) => ({ ...prev, [key]: e.target.value }))
        if (clientErrors[key]) {
          setClientErrors((prev) => ({ ...prev, [key]: undefined }))
        }
      },
      error: clientErrors[key] ?? getServerError(key),
    }
  }

  async function handleSubmit() {
    const errors = buildClientErrors(form)
    if (Object.keys(errors).length > 0) {
      setClientErrors(errors)
      return
    }

    await createMutation.mutateAsync({
      code: form.code.trim(),
      parentRegionId: parsePositiveInt(form.parentRegionId) ?? null,
      currencyCode: form.currencyCode.trim().toUpperCase(),
      name: form.name.trim(),
      taxCode: form.taxCode.trim() || null,
      timezoneName: form.timezoneName.trim(),
    })

    navigate('/org/regions')
  }

  if (!canCreate) {
    return (
      <DashboardLayout title="Create Region" description="Tạo region mới trong cây tổ chức.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.write để tạo region mới." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Create Region"
      description="Publish create workflow cho regions theo backend public API."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/org/regions">Back to regions</Link>
        </Button>
      }
    >
      <FormSection
        description="Các trường bắt buộc: Code, Name, Currency code, Timezone."
        title="Region identity"
      >
        <div className="field-grid">
          <Input {...field('code')} label="Code *" placeholder="e.g. VN-SOUTH" />
          <Input {...field('name')} label="Name *" placeholder="e.g. Southern Region" />
          <Input {...field('currencyCode')} label="Currency code *" placeholder="e.g. VND" />
          <Input {...field('timezoneName')} label="Timezone *" placeholder="e.g. Asia/Ho_Chi_Minh" />
          <Input {...field('parentRegionId')} label="Parent region ID" placeholder="e.g. 1" type="number" />
          <Input {...field('taxCode')} label="Tax code" placeholder="Optional" />
        </div>
      </FormSection>

      {createMutation.error ? (
        <p className="error-text">
          {createMutation.error instanceof Error ? createMutation.error.message : 'Không thể tạo region.'}
        </p>
      ) : null}

      <FormActions
        primaryAction={
          <Button loading={createMutation.isPending} onClick={() => void handleSubmit()}>
            Create region
          </Button>
        }
        secondaryAction={
          <Button asChild variant="secondary">
            <Link to="/org/regions">Cancel</Link>
          </Button>
        }
      />
    </DashboardLayout>
  )
}
