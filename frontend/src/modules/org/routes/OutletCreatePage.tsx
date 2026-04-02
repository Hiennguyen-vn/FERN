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
  Select,
} from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import { useCreateOutlet } from '../hooks/useOrg'
import type { OrgOutletStatus } from '../model/org.types'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

// Backend enum: DRAFT | ACTIVE | INACTIVE | CLOSED  (OutletStatus.java)
const STATUS_OPTIONS: SelectOption[] = [
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Closed', value: 'CLOSED' },
]

interface FormState {
  regionId: string
  code: string
  name: string
  status: OrgOutletStatus
  address: string
  phone: string
  email: string
  openedAt: string
  closedAt: string
}

const INITIAL_FORM: FormState = {
  regionId: '',
  code: '',
  name: '',
  status: 'DRAFT',
  address: '',
  phone: '',
  email: '',
  openedAt: '',
  closedAt: '',
}

function buildClientErrors(form: FormState): Partial<Record<keyof FormState, string>> {
  const errors: Partial<Record<keyof FormState, string>> = {}
  // Backend: regionId @NotNull Long — must be a positive integer
  if (!parsePositiveInt(form.regionId)) {
    errors.regionId = 'Region ID là số nguyên dương.'
  }
  if (!form.code.trim()) {
    errors.code = 'Code không được để trống.'
  }
  if (!form.name.trim()) {
    errors.name = 'Tên outlet không được để trống.'
  }
  return errors
}

export function OutletCreatePage() {
  usePageTitle('Create Outlet — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canCreate = orgUiPolicy.canOpenOutletCreate(principal)

  const [form, setForm] = useState<FormState>(INITIAL_FORM)
  const [clientErrors, setClientErrors] = useState<Partial<Record<keyof FormState, string>>>({})

  const createMutation = useCreateOutlet()
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
      regionId: Number(form.regionId),
      code: form.code.trim(),
      name: form.name.trim(),
      status: form.status,
      address: form.address.trim() || null,
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      openedAt: form.openedAt || null,
      closedAt: form.closedAt || null,
    })

    navigate('/org/outlets')
  }

  if (!canCreate) {
    return (
      <DashboardLayout
        title="Create Outlet"
        description="Tạo outlet mới và gắn vào region tương ứng."
      >
        <PermissionDeniedInline message="Bạn cần quyền org.outlet.write để tạo outlet mới." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/org/outlets">Back to outlets</Link>
        </Button>
      }
      description="Tạo outlet mới và gắn vào region tương ứng."
      title="Create Outlet"
    >
      <FormSection
        description="Các trường bắt buộc: Region ID, Code, Name, Status."
        title="Outlet identity"
      >
        <div className="field-grid">
          <Input
            {...field('regionId')}
            label="Region ID *"
            placeholder="e.g. 1"
            type="number"
          />
          <Input
            {...field('code')}
            label="Code *"
            placeholder="e.g. HN-001"
          />
          <Input
            {...field('name')}
            label="Name *"
            placeholder="e.g. Hà Nội - Hoàn Kiếm"
          />
          <Select
            label="Status *"
            onChange={(event) => setForm((prev) => ({ ...prev, status: event.target.value as OrgOutletStatus }))}
            options={STATUS_OPTIONS}
            value={form.status}
          />
        </div>
      </FormSection>

      <FormSection
        description="Thông tin liên hệ và địa chỉ vật lý của outlet. Tất cả optional."
        title="Contact"
      >
        <div className="field-grid">
          <Input
            {...field('address')}
            label="Address"
            placeholder="e.g. 12 Đinh Tiên Hoàng, Hoàn Kiếm, Hà Nội"
          />
          <Input
            {...field('phone')}
            label="Phone"
            placeholder="e.g. 024-3825-xxxx"
          />
          <Input
            {...field('email')}
            label="Email"
            placeholder="e.g. hanoi-hoankiem@fern.vn"
            type="email"
          />
        </div>
      </FormSection>

      <FormSection
        description="Ngày khai trương và ngày đóng cửa (nếu có). Định dạng YYYY-MM-DD."
        title="Operational dates"
      >
        <div className="field-grid">
          <Input
            {...field('openedAt')}
            label="Opened at"
            type="date"
          />
          <Input
            {...field('closedAt')}
            label="Closed at"
            type="date"
          />
        </div>
      </FormSection>

      {createMutation.error ? (
        <p className="error-text">
          {createMutation.error instanceof Error
            ? createMutation.error.message
            : 'Không thể tạo outlet. Vui lòng kiểm tra lại các trường.'}
        </p>
      ) : null}

      <FormActions
        primaryAction={
          <Button
            loading={createMutation.isPending}
            onClick={() => void handleSubmit()}
          >
            Create outlet
          </Button>
        }
        secondaryAction={
          <Button asChild variant="secondary">
            <Link to="/org/outlets">Cancel</Link>
          </Button>
        }
      />
    </DashboardLayout>
  )
}
