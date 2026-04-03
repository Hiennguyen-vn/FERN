import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useFieldErrors } from '@core/api/useFieldErrors'
import {
  Button,
  EntityHeader,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  Select,
  StatusBadge,
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
      <EntityHeader
        eyebrow="Org / Outlet Create"
        metadata={
          <>
            <span>Region ID: {form.regionId.trim() || 'Pending'}</span>
            <span>Code: {form.code.trim() || 'Pending'}</span>
            <span>Opened: {form.openedAt || 'Optional'}</span>
          </>
        }
        status={<StatusBadge status={form.status} />}
        title={form.name.trim() || 'New outlet record'}
      />

      <section className="surface-panel command-stage" aria-label="Outlet create command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip-success">Create workflow</span>
            <span className="meta-chip">Region {form.regionId.trim() || 'Pending'}</span>
            <span className={form.status === 'ACTIVE' ? 'meta-chip-success' : 'meta-chip'}>
              {form.status}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Outlet Create</p>
            <strong className="action-summary-title">Define a new operating branch</strong>
            <p className="muted-text">
              Set region linkage, lifecycle state, and contact defaults so the outlet starts with
              the correct operating context and administrative metadata.
            </p>
          </div>
          <div className="meta-grid">
            <span>Region ID: {form.regionId.trim() || 'Pending'}</span>
            <span>Code: {form.code.trim() || 'Pending'}</span>
            <span>Name: {form.name.trim() || 'Pending'}</span>
            <span>Status: {form.status}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Creation checks</span>
            <strong>Operations-first setup</strong>
            <p>
              Keep region linkage, lifecycle state, and contact reachability visible before creating
              the outlet record.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Region linkage</strong>
                <span className="muted-text">Every new outlet must start attached to a valid region id.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{form.regionId.trim() || 'Required'}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Contact reachability</strong>
                <span className="muted-text">
                  {form.email.trim() || 'No email'} · {form.phone.trim() || 'No phone'}
                </span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">
                  {[form.email.trim(), form.phone.trim()].filter(Boolean).length}/2
                </span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Validation readiness</strong>
                <span className="muted-text">Required identity fields must be filled before publish.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">
                  {[form.regionId.trim(), form.code.trim(), form.name.trim()].filter(Boolean).length}/3
                </span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Outlet create summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="public" />
            </span>
            <span className="workspace-stat-badge">Region</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Linked region id</span>
            <strong className="workspace-stat-value">{form.regionId.trim() || 'Required'}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="badge" />
            </span>
            <span className="workspace-stat-badge">Identity</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Pending outlet code</span>
            <strong className="workspace-stat-value">{form.code.trim() || 'Required'}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="contact_page" />
            </span>
            <span className="workspace-stat-badge success">Reachable</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Contact points available</span>
            <strong className="workspace-stat-value">
              {[form.email.trim(), form.phone.trim()].filter(Boolean).length}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="task_alt" />
            </span>
            <span className={form.status === 'ACTIVE' ? 'workspace-stat-badge success' : 'workspace-stat-badge warning'}>
              Lifecycle
            </span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Initial status</span>
            <strong className="workspace-stat-value">{form.status}</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
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
        </div>

        <aside className="surface-grid-side">
          <section className="surface-panel command-table-stack">
            <div className="state-panel-heading">
              <span className="eyebrow">Publish actions</span>
              <h2 className="card-title">Create guidance</h2>
              <p className="muted-text">
                Confirm region linkage, operational dates, and contact data before creating the new
                outlet record.
              </p>
            </div>
            <div className="meta-grid">
              <span>Address: {form.address.trim() || 'Optional'}</span>
              <span>Opened at: {form.openedAt || 'Optional'}</span>
              <span>Closed at: {form.closedAt || 'Optional'}</span>
              <span>Email: {form.email.trim() || 'Optional'}</span>
            </div>
            {createMutation.error ? (
              <p className="error-text error-text-compact">
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
          </section>
        </aside>
      </div>
    </DashboardLayout>
  )
}
