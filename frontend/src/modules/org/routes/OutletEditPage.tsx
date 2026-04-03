import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useFieldErrors } from '@core/api/useFieldErrors'
import {
  Button,
  Card,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import { useOutlet, useUpdateOutlet } from '../hooks/useOrg'
import type { OrgOutletStatus } from '../model/org.types'
import { getOrgErrorMessage } from '../services/orgError.service'
import { formatOrgDate, formatOrgInstant } from '../services/orgReadModel.service'
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
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canEdit = orgUiPolicy.canOpenOutletEdit(principal)
  const outletQuery = useOutlet(outletId, { enabled: canEdit && Number.isFinite(outletId) && outletId > 0 })
  const updateMutation = useUpdateOutlet(outletId)
  const { getError: getServerError } = useFieldErrors(updateMutation.error)
  const [clientErrors, setClientErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const [form, setForm] = useState<FormState | null>(null)

  usePageTitle(outletQuery.data ? `${outletQuery.data.name} — Edit Outlet` : 'Edit Outlet — Org')

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

  let readonlyMessage = 'Outlet code là immutable trong edit flow này. Review status, region linkage và operational dates trước khi publish.'
  if (outletQuery.data.status === 'CLOSED') {
    readonlyMessage = 'Outlet này đang CLOSED. Chỉ nên chỉnh metadata khi có yêu cầu hành chính rõ ràng.'
  } else if (outletQuery.data.status === 'INACTIVE') {
    readonlyMessage = 'Outlet này đang INACTIVE. Kiểm tra lại region linkage và contact metadata trước khi lưu.'
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
      <ReadonlyBanner message={readonlyMessage} />

      <EntityHeader
        eyebrow="Org / Outlet Edit"
        metadata={
          <>
            <span>Outlet code: {outletQuery.data.code}</span>
            <span>Region ID: #{outletQuery.data.regionId}</span>
            <span>Opened: {formatOrgDate(outletQuery.data.openedAt)}</span>
          </>
        }
        status={<StatusBadge status={outletQuery.data.status} />}
        title={outletQuery.data.name}
      />

      <section className="surface-panel command-stage" aria-label="Outlet edit command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Edit workflow</span>
            <span className="meta-chip">Region #{effectiveForm.regionId || outletQuery.data.regionId}</span>
            <span className={effectiveForm.status === 'ACTIVE' ? 'meta-chip-success' : 'meta-chip'}>
              {effectiveForm.status}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Outlet Edit</p>
            <strong className="action-summary-title">Adjust outlet operations profile</strong>
            <p className="muted-text">
              Update the outlet identity, lifecycle status, and operational contact fields while
              keeping the immutable code and audit context visible.
            </p>
          </div>
          <div className="meta-grid">
            <span>Outlet code: {outletQuery.data.code}</span>
            <span>Region ID: #{effectiveForm.regionId || outletQuery.data.regionId}</span>
            <span>Status: {effectiveForm.status}</span>
            <span>Opened: {formatOrgDate(effectiveForm.openedAt || outletQuery.data.openedAt)}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Edit guardrails</span>
            <strong>Operational-safe changes</strong>
            <p>
              Keep lifecycle state, contact reachability, and current audit freshness visible before
              publishing outlet changes.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Immutable code</strong>
                <span className="muted-text">Outlet code remains fixed after creation.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{outletQuery.data.code}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Contact reachability</strong>
                <span className="muted-text">
                  {effectiveForm.email.trim() || 'No email'} · {effectiveForm.phone.trim() || 'No phone'}
                </span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">
                  {[effectiveForm.email.trim(), effectiveForm.phone.trim()].filter(Boolean).length}/2
                </span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Audit freshness</strong>
                <span className="muted-text">Latest backend update for this outlet record.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{formatOrgInstant(outletQuery.data.updatedAt)}</span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Outlet edit summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="storefront" />
            </span>
            <span className="workspace-stat-badge">Identity</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Outlet record</span>
            <strong className="workspace-stat-value">#{outletQuery.data.id}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="public" />
            </span>
            <span className="workspace-stat-badge">Scope</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Linked region</span>
            <strong className="workspace-stat-value">#{effectiveForm.regionId || outletQuery.data.regionId}</strong>
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
              {[effectiveForm.email.trim(), effectiveForm.phone.trim()].filter(Boolean).length}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="task_alt" />
            </span>
            <span className={effectiveForm.status === 'ACTIVE' ? 'workspace-stat-badge success' : 'workspace-stat-badge warning'}>
              Lifecycle
            </span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Working status</span>
            <strong className="workspace-stat-value">{effectiveForm.status}</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
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
                onChange={(event) =>
                  setForm((prev) => ({ ...(prev ?? effectiveForm), status: event.target.value as OrgOutletStatus }))
                }
                options={STATUS_OPTIONS}
                value={effectiveForm.status}
              />
            </div>
          </FormSection>

          <FormSection description="Contact metadata của outlet." title="Contact">
            <div className="field-grid">
              <Input {...field('address')} label="Address" />
              <Input {...field('phone')} label="Phone" />
              <Input {...field('email')} label="Email" type="email" />
            </div>
          </FormSection>

          <FormSection description="Ngày khai trương và ngày đóng cửa." title="Operational dates">
            <div className="field-grid">
              <Input {...field('openedAt')} label="Opened at" type="date" />
              <Input {...field('closedAt')} label="Closed at" type="date" />
            </div>
          </FormSection>
        </div>

        <aside className="surface-grid-side">
          <section className="surface-panel command-table-stack">
            <div className="state-panel-heading">
              <span className="eyebrow">Publish actions</span>
              <h2 className="card-title">Edit guidance</h2>
              <p className="muted-text">
                Confirm lifecycle status, region linkage, and operational dates before saving the
                updated outlet record.
              </p>
            </div>
            <div className="meta-grid">
              <span>Created at: {formatOrgInstant(outletQuery.data.createdAt)}</span>
              <span>Updated at: {formatOrgInstant(outletQuery.data.updatedAt)}</span>
              <span>Opened at: {formatOrgDate(effectiveForm.openedAt || outletQuery.data.openedAt)}</span>
              <span>Closed at: {formatOrgDate(effectiveForm.closedAt || outletQuery.data.closedAt)}</span>
            </div>
            {updateMutation.error ? (
              <p className="error-text error-text-compact">
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
          </section>
        </aside>
      </div>
    </DashboardLayout>
  )
}
