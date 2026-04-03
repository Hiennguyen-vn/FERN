import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useFieldErrors } from '@core/api/useFieldErrors'
import { useScopeContext } from '@core/scopes/useScopeContext'
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
import { useCreateEmployeeAssignment } from '../hooks/useHr'
import { canWriteAssignments } from '../services/hrPermission.service'

interface FormState {
  employeeId: string
  regionId: string
  outletId: string
  positionTitle: string
  startDate: string
  endDate: string
  primaryAssignment: string
  status: string
}

const BOOLEAN_OPTIONS: SelectOption[] = [
  { label: 'Yes', value: 'true' },
  { label: 'No', value: 'false' },
]

const STATUS_OPTIONS: SelectOption[] = [
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'INACTIVE', value: 'INACTIVE' },
  { label: 'ENDED', value: 'ENDED' },
]

export function AssignmentCreatePage() {
  usePageTitle('Create Assignment — HR')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canCreate = canWriteAssignments(principal)
  const createMutation = useCreateEmployeeAssignment()
  const { getError: getServerError } = useFieldErrors(createMutation.error)
  const [form, setForm] = useState<FormState>({
    employeeId: '',
    regionId: selectedRegionId ? String(selectedRegionId) : '',
    outletId: selectedOutletId ? String(selectedOutletId) : '',
    positionTitle: '',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: '',
    primaryAssignment: 'true',
    status: 'ACTIVE',
  })
  const [clientError, setClientError] = useState<string | null>(null)

  if (!canCreate) {
    return (
      <DashboardLayout title="Create Assignment" description="Create a new employee assignment.">
        <PermissionDeniedInline message="Bạn cần quyền hr.shift.write để tạo employee assignment." />
      </DashboardLayout>
    )
  }

  async function handleSubmit() {
    const employeeId = parsePositiveInt(form.employeeId)
    const regionId = parsePositiveInt(form.regionId)
    const outletId = parsePositiveInt(form.outletId)
    if (!employeeId || !regionId || !outletId || !form.positionTitle.trim() || !form.startDate) {
      setClientError('Employee ID, region ID, outlet ID, position title và start date là bắt buộc.')
      return
    }
    if (form.endDate && form.endDate < form.startDate) {
      setClientError('End date phải lớn hơn hoặc bằng start date.')
      return
    }

    setClientError(null)
    const assignment = await createMutation.mutateAsync({
      employeeId,
      regionId,
      outletId,
      positionTitle: form.positionTitle.trim(),
      startDate: form.startDate,
      endDate: form.endDate || null,
      primaryAssignment: form.primaryAssignment === 'true',
      status: form.status || null,
    })

    navigate(`/hr/employees/${assignment.employeeId}`)
  }

  return (
    <DashboardLayout
      title="Create Assignment"
      description="Publish employee assignment create flow theo backend POST /employee-assignments."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to="/hr/shift-scheduling">Back to shift scheduling</Link>
        </Button>
      }
    >
      <FormSection title="Assignment form" description="Assignment này là employee master-data assignment, không phải shift-assignment.">
        <div className="field-grid">
          <Input
            error={clientError ?? getServerError('employeeId')}
            label="Employee ID *"
            onChange={(event) => setForm((prev) => ({ ...prev, employeeId: event.target.value }))}
            type="number"
            value={form.employeeId}
          />
          <Input
            error={getServerError('regionId')}
            label="Region ID *"
            onChange={(event) => setForm((prev) => ({ ...prev, regionId: event.target.value }))}
            type="number"
            value={form.regionId}
          />
          <Input
            error={getServerError('outletId')}
            label="Outlet ID *"
            onChange={(event) => setForm((prev) => ({ ...prev, outletId: event.target.value }))}
            type="number"
            value={form.outletId}
          />
          <Input
            error={getServerError('positionTitle')}
            label="Position title *"
            onChange={(event) => setForm((prev) => ({ ...prev, positionTitle: event.target.value }))}
            value={form.positionTitle}
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
          <Select
            label="Primary assignment"
            onChange={(event) => setForm((prev) => ({ ...prev, primaryAssignment: event.target.value }))}
            options={BOOLEAN_OPTIONS}
            value={form.primaryAssignment}
          />
          <Select
            label="Status"
            onChange={(event) => setForm((prev) => ({ ...prev, status: event.target.value }))}
            options={STATUS_OPTIONS}
            value={form.status}
          />
        </div>
      </FormSection>

      {createMutation.error && !clientError ? (
        <p className="error-text">
          {createMutation.error instanceof Error ? createMutation.error.message : 'Không thể tạo assignment.'}
        </p>
      ) : null}

      <FormActions
        primaryAction={
          <Button loading={createMutation.isPending} onClick={() => void handleSubmit()}>
            Create assignment
          </Button>
        }
        secondaryAction={
          <Button asChild variant="secondary">
            <Link to="/hr/shift-scheduling">Cancel</Link>
          </Button>
        }
      />
    </DashboardLayout>
  )
}
