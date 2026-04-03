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
import { useCreateContract } from '../hooks/useHr'
import { canWriteContracts } from '../services/hrPermission.service'

interface FormState {
  employeeId: string
  employmentType: string
  salaryType: string
  baseSalary: string
  regionId: string
  taxCode: string
  contractStatus: string
  startDate: string
  endDate: string
}

const EMPLOYMENT_TYPE_OPTIONS: SelectOption[] = [
  { label: 'FULL_TIME', value: 'FULL_TIME' },
  { label: 'PART_TIME', value: 'PART_TIME' },
  { label: 'CONTRACTOR', value: 'CONTRACTOR' },
]

const SALARY_TYPE_OPTIONS: SelectOption[] = [
  { label: 'MONTHLY', value: 'MONTHLY' },
  { label: 'HOURLY', value: 'HOURLY' },
  { label: 'DAILY', value: 'DAILY' },
]

const CONTRACT_STATUS_OPTIONS: SelectOption[] = [
  { label: 'DRAFT', value: 'DRAFT' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'INACTIVE', value: 'INACTIVE' },
  { label: 'TERMINATED', value: 'TERMINATED' },
]

const INITIAL_FORM: FormState = {
  employeeId: '',
  employmentType: 'FULL_TIME',
  salaryType: 'MONTHLY',
  baseSalary: '',
  regionId: '',
  taxCode: '',
  contractStatus: 'ACTIVE',
  startDate: new Date().toISOString().slice(0, 10),
  endDate: '',
}

export function ContractCreatePage() {
  usePageTitle('Create Contract — HR')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canCreate = canWriteContracts(principal)
  const createMutation = useCreateContract()
  const { getError: getServerError } = useFieldErrors(createMutation.error)
  const [form, setForm] = useState<FormState>(INITIAL_FORM)
  const [clientError, setClientError] = useState<string | null>(null)

  if (!canCreate) {
    return (
      <DashboardLayout title="Create Contract" description="Create a new employee contract.">
        <PermissionDeniedInline message="Bạn cần quyền hr.contract.write để tạo hợp đồng." />
      </DashboardLayout>
    )
  }

  async function handleSubmit() {
    const employeeId = parsePositiveInt(form.employeeId)
    const baseSalary = Number(form.baseSalary)
    if (!employeeId || !form.employmentType || !form.salaryType || !Number.isFinite(baseSalary) || baseSalary < 0 || !form.startDate) {
      setClientError('Employee ID, employment type, salary type, base salary và start date là bắt buộc.')
      return
    }
    if (form.endDate && form.endDate < form.startDate) {
      setClientError('End date phải lớn hơn hoặc bằng start date.')
      return
    }

    setClientError(null)
    const contract = await createMutation.mutateAsync({
      employeeId,
      employmentType: form.employmentType,
      salaryType: form.salaryType,
      baseSalary,
      regionId: parsePositiveInt(form.regionId) ?? null,
      taxCode: form.taxCode.trim() || null,
      contractStatus: form.contractStatus || null,
      startDate: form.startDate,
      endDate: form.endDate || null,
    })

    navigate(`/hr/contracts/${contract.id}?employeeId=${employeeId}`)
  }

  return (
    <DashboardLayout
      title="Create Contract"
      description="Publish contract create flow theo backend POST /employee-contracts."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/hr/contracts">Back to contracts</Link>
        </Button>
      }
    >
      <FormSection title="Contract form" description="Employee ID, employment type, salary type, base salary và start date là bắt buộc.">
        <div className="field-grid">
          <Input
            error={clientError ?? getServerError('employeeId')}
            label="Employee ID *"
            onChange={(event) => setForm((prev) => ({ ...prev, employeeId: event.target.value }))}
            type="number"
            value={form.employeeId}
          />
          <Select
            label="Employment type *"
            onChange={(event) => setForm((prev) => ({ ...prev, employmentType: event.target.value }))}
            options={EMPLOYMENT_TYPE_OPTIONS}
            value={form.employmentType}
          />
          <Select
            label="Salary type *"
            onChange={(event) => setForm((prev) => ({ ...prev, salaryType: event.target.value }))}
            options={SALARY_TYPE_OPTIONS}
            value={form.salaryType}
          />
          <Input
            error={getServerError('baseSalary')}
            label="Base salary *"
            onChange={(event) => setForm((prev) => ({ ...prev, baseSalary: event.target.value }))}
            type="number"
            value={form.baseSalary}
          />
          <Input
            error={getServerError('regionId')}
            label="Region ID"
            onChange={(event) => setForm((prev) => ({ ...prev, regionId: event.target.value }))}
            type="number"
            value={form.regionId}
          />
          <Input
            error={getServerError('taxCode')}
            label="Tax code"
            onChange={(event) => setForm((prev) => ({ ...prev, taxCode: event.target.value }))}
            value={form.taxCode}
          />
          <Select
            label="Contract status"
            onChange={(event) => setForm((prev) => ({ ...prev, contractStatus: event.target.value }))}
            options={CONTRACT_STATUS_OPTIONS}
            value={form.contractStatus}
          />
          <Input
            error={getServerError('startDate')}
            label="Start date *"
            onChange={(event) => setForm((prev) => ({ ...prev, startDate: event.target.value }))}
            type="date"
            value={form.startDate}
          />
          <Input
            error={getServerError('endDate')}
            label="End date"
            onChange={(event) => setForm((prev) => ({ ...prev, endDate: event.target.value }))}
            type="date"
            value={form.endDate}
          />
        </div>
      </FormSection>

      {createMutation.error && !clientError ? (
        <p className="error-text">
          {createMutation.error instanceof Error ? createMutation.error.message : 'Không thể tạo hợp đồng.'}
        </p>
      ) : null}

      <FormActions
        primaryAction={
          <Button loading={createMutation.isPending} onClick={() => void handleSubmit()}>
            Create contract
          </Button>
        }
        secondaryAction={
          <Button asChild variant="secondary">
            <Link to="/hr/contracts">Cancel</Link>
          </Button>
        }
      />
    </DashboardLayout>
  )
}
