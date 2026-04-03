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
      <EntityHeader
        eyebrow="Org / Region Create"
        metadata={
          <>
            <span>Code: {form.code.trim() || 'Pending'}</span>
            <span>Parent: {form.parentRegionId.trim() ? `#${form.parentRegionId.trim()}` : 'Root region'}</span>
            <span>Currency: {form.currencyCode.trim() || 'Required'}</span>
            <span>Timezone: {form.timezoneName.trim() || 'Required'}</span>
          </>
        }
        title={form.name.trim() || 'New region record'}
      />

      <section className="surface-panel command-stage" aria-label="Region create command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip-success">Create workflow</span>
            <span className="meta-chip">{form.currencyCode.trim() || 'Currency pending'}</span>
            <span className={form.parentRegionId.trim() ? 'meta-chip' : 'meta-chip-success'}>
              {form.parentRegionId.trim() ? `Child of #${form.parentRegionId.trim()}` : 'Root region'}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Region Create</p>
            <strong className="action-summary-title">Define a new hierarchy node</strong>
            <p className="muted-text">
              Set the region identity, hierarchy parent, and reference settings so the new node can
              anchor outlet scope and reporting structure correctly from the start.
            </p>
          </div>
          <div className="meta-grid">
            <span>Code: {form.code.trim() || 'Pending'}</span>
            <span>Name: {form.name.trim() || 'Pending'}</span>
            <span>Currency: {form.currencyCode.trim() || 'Required'}</span>
            <span>Timezone: {form.timezoneName.trim() || 'Required'}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Creation checks</span>
            <strong>Reference-first setup</strong>
            <p>
              Keep hierarchy placement, timezone, and currency visible here so the region starts
              with the right administrative defaults.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Hierarchy placement</strong>
                <span className="muted-text">Choose whether this region is root-level or nested under a parent.</span>
              </div>
              <div className="command-support-stack">
                <span className={form.parentRegionId.trim() ? 'meta-chip' : 'meta-chip-success'}>
                  {form.parentRegionId.trim() ? 'Nested' : 'Root'}
                </span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Reference settings</strong>
                <span className="muted-text">Currency and timezone determine default operating context.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">
                  {form.currencyCode.trim() || 'Currency'} · {form.timezoneName.trim() || 'Timezone'}
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
                  {[form.code.trim(), form.name.trim(), form.currencyCode.trim(), form.timezoneName.trim()].filter(Boolean).length}/4
                </span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Region create summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="account_tree" />
            </span>
            <span className="workspace-stat-badge">Hierarchy</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Structure role</span>
            <strong className="workspace-stat-value">{form.parentRegionId.trim() ? 'Nested' : 'Root'}</strong>
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
            <span className="workspace-stat-label">Pending code</span>
            <strong className="workspace-stat-value">{form.code.trim() || 'Required'}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge">Currency</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Working currency</span>
            <strong className="workspace-stat-value">{form.currencyCode.trim() || 'Required'}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="schedule" />
            </span>
            <span className="workspace-stat-badge warning">Timezone</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Reference timezone</span>
            <strong className="workspace-stat-value">{form.timezoneName.trim() || 'Required'}</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
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
        </div>

        <aside className="surface-grid-side">
          <section className="surface-panel command-table-stack">
            <div className="state-panel-heading">
              <span className="eyebrow">Publish actions</span>
              <h2 className="card-title">Create guidance</h2>
              <p className="muted-text">
                Confirm hierarchy placement and reference defaults before creating the new region
                record.
              </p>
            </div>
            <div className="meta-grid">
              <span>Parent region: {form.parentRegionId.trim() ? `#${form.parentRegionId.trim()}` : 'Root region'}</span>
              <span>Tax code: {form.taxCode.trim() || 'Optional'}</span>
              <span>Currency: {form.currencyCode.trim() || 'Required'}</span>
              <span>Timezone: {form.timezoneName.trim() || 'Required'}</span>
            </div>
            {createMutation.error ? (
              <p className="error-text error-text-compact">
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
          </section>
        </aside>
      </div>
    </DashboardLayout>
  )
}
