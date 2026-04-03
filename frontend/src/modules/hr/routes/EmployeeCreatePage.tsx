import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
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
import { useCreateEmployee } from '../hooks/useHr'
import { canWriteEmployees } from '../services/hrPermission.service'

interface FormState {
  employeeCode: string
  fullName: string
  dob: string
  gender: string
  email: string
  phone: string
  status: string
  hiredAt: string
  userAccountId: string
}

const GENDER_OPTIONS: SelectOption[] = [
  { label: 'Unspecified', value: '' },
  { label: 'MALE', value: 'MALE' },
  { label: 'FEMALE', value: 'FEMALE' },
  { label: 'OTHER', value: 'OTHER' },
]

const STATUS_OPTIONS: SelectOption[] = [
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'INACTIVE', value: 'INACTIVE' },
  { label: 'ON_LEAVE', value: 'ON_LEAVE' },
  { label: 'TERMINATED', value: 'TERMINATED' },
]

const INITIAL_FORM: FormState = {
  employeeCode: '',
  fullName: '',
  dob: '',
  gender: '',
  email: '',
  phone: '',
  status: 'ACTIVE',
  hiredAt: new Date().toISOString().slice(0, 10),
  userAccountId: '',
}

export function EmployeeCreatePage() {
  usePageTitle('Create Employee — HR')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canCreate = canWriteEmployees(principal)
  const createMutation = useCreateEmployee()
  const { getError: getServerError } = useFieldErrors(createMutation.error)
  const [form, setForm] = useState<FormState>(INITIAL_FORM)
  const [clientError, setClientError] = useState<string | null>(null)

  if (!canCreate) {
    return (
      <DashboardLayout title="Create Employee" description="Create a new employee record.">
        <PermissionDeniedInline message="Bạn cần quyền hr.employee.write để tạo nhân viên." />
      </DashboardLayout>
    )
  }

  async function handleSubmit() {
    if (!form.fullName.trim()) {
      setClientError('Full name là bắt buộc.')
      return
    }

    setClientError(null)
    const employee = await createMutation.mutateAsync({
      employeeCode: form.employeeCode.trim() || null,
      fullName: form.fullName.trim(),
      dob: form.dob || null,
      gender: form.gender || null,
      email: form.email.trim() || null,
      phone: form.phone.trim() || null,
      status: form.status || null,
      hiredAt: form.hiredAt || null,
      userAccountId: parsePositiveInt(form.userAccountId) ?? null,
    })

    navigate(`/hr/employees/${employee.id}`)
  }

  return (
    <DashboardLayout
      title="Create Employee"
      description="Publish employee create flow theo backend POST /employees."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/hr/employees">Back to employees</Link>
        </Button>
      }
    >
      <FormSection title="Employee profile" description="Chỉ full name là bắt buộc ở backend contract hiện tại.">
        <div className="field-grid">
          <Input
            error={getServerError('employeeCode')}
            label="Employee code"
            onChange={(event) => setForm((prev) => ({ ...prev, employeeCode: event.target.value }))}
            value={form.employeeCode}
          />
          <Input
            error={clientError ?? getServerError('fullName')}
            label="Full name *"
            onChange={(event) => setForm((prev) => ({ ...prev, fullName: event.target.value }))}
            value={form.fullName}
          />
          <Input
            error={getServerError('dob')}
            label="Date of birth"
            onChange={(event) => setForm((prev) => ({ ...prev, dob: event.target.value }))}
            type="date"
            value={form.dob}
          />
          <Select
            label="Gender"
            onChange={(event) => setForm((prev) => ({ ...prev, gender: event.target.value }))}
            options={GENDER_OPTIONS}
            value={form.gender}
          />
          <Input
            error={getServerError('email')}
            label="Email"
            onChange={(event) => setForm((prev) => ({ ...prev, email: event.target.value }))}
            type="email"
            value={form.email}
          />
          <Input
            error={getServerError('phone')}
            label="Phone"
            onChange={(event) => setForm((prev) => ({ ...prev, phone: event.target.value }))}
            value={form.phone}
          />
          <Select
            label="Status"
            onChange={(event) => setForm((prev) => ({ ...prev, status: event.target.value }))}
            options={STATUS_OPTIONS}
            value={form.status}
          />
          <Input
            error={getServerError('hiredAt')}
            label="Hired at"
            onChange={(event) => setForm((prev) => ({ ...prev, hiredAt: event.target.value }))}
            type="date"
            value={form.hiredAt}
          />
          <Input
            error={getServerError('userAccountId')}
            label="User account ID"
            onChange={(event) => setForm((prev) => ({ ...prev, userAccountId: event.target.value }))}
            type="number"
            value={form.userAccountId}
          />
        </div>
      </FormSection>

      {createMutation.error && !clientError ? (
        <p className="error-text">
          {createMutation.error instanceof Error ? createMutation.error.message : 'Không thể tạo nhân viên.'}
        </p>
      ) : null}

      <FormActions
        primaryAction={
          <Button loading={createMutation.isPending} onClick={() => void handleSubmit()}>
            Create employee
          </Button>
        }
        secondaryAction={
          <Button asChild variant="secondary">
            <Link to="/hr/employees">Cancel</Link>
          </Button>
        }
      />
    </DashboardLayout>
  )
}
