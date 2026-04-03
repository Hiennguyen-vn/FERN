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
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { parsePositiveInt } from '@shared/validators/parseInput'
import { useRegion, useUpdateRegion } from '../hooks/useOrg'
import { getOrgErrorMessage } from '../services/orgError.service'
import { buildParentRegionLabel, formatOrgInstant } from '../services/orgReadModel.service'
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
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canEdit = orgUiPolicy.canOpenRegionEdit(principal)
  const regionQuery = useRegion(regionId, { enabled: canEdit && Number.isFinite(regionId) && regionId > 0 })
  const updateMutation = useUpdateRegion(regionId)
  const { getError: getServerError } = useFieldErrors(updateMutation.error)
  const [clientErrors, setClientErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const [form, setForm] = useState<FormState | null>(null)

  usePageTitle(regionQuery.data ? `${regionQuery.data.name} — Edit Region` : 'Edit Region — Org')

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

  const readonlyMessage =
    'Region code là immutable trong edit flow này. Review parent linkage, currency và timezone trước khi publish thay đổi.'

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
      <ReadonlyBanner message={readonlyMessage} />

      <EntityHeader
        eyebrow="Org / Region Edit"
        metadata={
          <>
            <span>Region code: {regionQuery.data.code}</span>
            <span>Parent: {buildParentRegionLabel(regionQuery.data.parentRegionId)}</span>
            <span>Currency: {regionQuery.data.currencyCode}</span>
            <span>Timezone: {regionQuery.data.timezoneName}</span>
          </>
        }
        title={regionQuery.data.name}
      />

      <section className="surface-panel command-stage" aria-label="Region edit command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Edit workflow</span>
            <span className="meta-chip">{regionQuery.data.currencyCode}</span>
            <span className={regionQuery.data.parentRegionId ? 'meta-chip' : 'meta-chip-success'}>
              {regionQuery.data.parentRegionId ? `Child of #${regionQuery.data.parentRegionId}` : 'Root region'}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Organization / Region Edit</p>
            <strong className="action-summary-title">Adjust hierarchy settings</strong>
            <p className="muted-text">
              Update the reference values that drive hierarchy, currency, and timezone behavior
              without altering the immutable region code.
            </p>
          </div>
          <div className="meta-grid">
            <span>Region code: {regionQuery.data.code}</span>
            <span>Parent region: {buildParentRegionLabel(regionQuery.data.parentRegionId)}</span>
            <span>Currency: {effectiveForm.currencyCode || regionQuery.data.currencyCode}</span>
            <span>Timezone: {effectiveForm.timezoneName || regionQuery.data.timezoneName}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Edit guardrails</span>
            <strong>Structure-safe changes</strong>
            <p>
              Keep immutable identity, parent linkage, and latest audit timestamp visible while
              publishing regional reference updates.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Immutable code</strong>
                <span className="muted-text">The edit surface keeps the region code locked.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{regionQuery.data.code}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Parent linkage</strong>
                <span className="muted-text">Hierarchy role for the current region node.</span>
              </div>
              <div className="command-support-stack">
                <span className={regionQuery.data.parentRegionId ? 'meta-chip' : 'meta-chip-success'}>
                  {regionQuery.data.parentRegionId ? 'Nested' : 'Root'}
                </span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Audit freshness</strong>
                <span className="muted-text">Latest backend update before this edit session.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{formatOrgInstant(regionQuery.data.updatedAt)}</span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Region edit summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="account_tree" />
            </span>
            <span className="workspace-stat-badge">Node</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Region record</span>
            <strong className="workspace-stat-value">#{regionQuery.data.id}</strong>
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
            <strong className="workspace-stat-value">{effectiveForm.currencyCode || 'Required'}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="schedule" />
            </span>
            <span className="workspace-stat-badge">Timezone</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Working timezone</span>
            <strong className="workspace-stat-value">{effectiveForm.timezoneName || 'Required'}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="flag" />
            </span>
            <span className="workspace-stat-badge warning">Hierarchy</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Parent region</span>
            <strong className="workspace-stat-value">
              {effectiveForm.parentRegionId ? `#${effectiveForm.parentRegionId}` : 'Root'}
            </strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
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
        </div>

        <aside className="surface-grid-side">
          <section className="surface-panel command-table-stack">
            <div className="state-panel-heading">
              <span className="eyebrow">Publish actions</span>
              <h2 className="card-title">Edit guidance</h2>
              <p className="muted-text">
                Review immutable identity and audit timestamps before saving the updated region
                metadata.
              </p>
            </div>
            <div className="meta-grid">
              <span>Created at: {formatOrgInstant(regionQuery.data.createdAt)}</span>
              <span>Updated at: {formatOrgInstant(regionQuery.data.updatedAt)}</span>
              <span>Tax code: {effectiveForm.taxCode.trim() || 'No tax code'}</span>
              <span>Parent region: {effectiveForm.parentRegionId ? `#${effectiveForm.parentRegionId}` : 'Root region'}</span>
            </div>
            {updateMutation.error ? (
              <p className="error-text error-text-compact">
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
          </section>
        </aside>
      </div>
    </DashboardLayout>
  )
}
