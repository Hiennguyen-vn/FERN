import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  DataTable,
  EntityHeader,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { hrApi } from '../../hr/api/hr.api'
import type { PayrollPeriod, CreatePayrollPeriodPayload } from '../../hr/model/hr.types'
import { getFinanceErrorMessage } from '../services/financeError.service'
import { formatFinanceDateLabel } from '../services/financeWorkflow.service'
import { payrollApprovalUiPolicy } from '../services/payrollApprovalUiPolicy.service'

export function PayrollPeriodsPage() {
  usePageTitle('Payroll Periods — Finance')
  const principal = usePrincipal()
  const queryClient = useQueryClient()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canOpen = payrollApprovalUiPolicy.canOpenPayrollQueue(principal)
  const [regionFilter, setRegionFilter] = useState(
    selectedRegionId ? String(selectedRegionId) : regionIds.length === 1 ? String(regionIds[0]) : '',
  )

  const activeRegionId =
    regionFilter.trim() && Number.isFinite(Number(regionFilter)) ? Number(regionFilter) : undefined
  const canQuery = Boolean(activeRegionId) || Boolean(principal?.scopeRoots.system)

  const periodsQuery = useQuery({
    enabled: canOpen && canQuery,
    queryFn: () => hrApi.listPayrollPeriods(activeRegionId),
    queryKey: ['finance', 'payrollPeriods', activeRegionId],
  })

  const [isCreating, setIsCreating] = useState(false)
  const [createForm, setCreateForm] = useState<Partial<CreatePayrollPeriodPayload>>({})

  const createMutation = useMutation({
    mutationFn: (payload: CreatePayrollPeriodPayload) => hrApi.createPayrollPeriod(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payrollPeriods'] })
      setIsCreating(false)
      setCreateForm({})
    },
  })

  const regionOptions = useMemo<SelectOption[]>(
    () => regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    [regionIds],
  )

  const selectedRegionLabel = activeRegionId ? `Region #${activeRegionId}` : principal?.scopeRoots.system ? 'System scope' : 'Region required'
  const periodCount = periodsQuery.data?.length ?? 0
  const requiredCreateFields = [createForm.name, createForm.startDate, createForm.endDate].filter(Boolean).length

  const columns = useMemo<Array<DataTableColumn<PayrollPeriod>>>(
    () => [
      {
        key: 'referenceCode',
        header: 'Ref Code',
        render: (period) => <strong>{period.referenceCode}</strong>,
      },
      {
        key: 'name',
        header: 'Name',
        render: (period) => period.name,
      },
      {
        key: 'startDate',
        header: 'Start Date',
        render: (period) => formatFinanceDateLabel(period.startDate),
      },
      {
        key: 'endDate',
        header: 'End Date',
        render: (period) => formatFinanceDateLabel(period.endDate),
      },
      {
        key: 'payDate',
        header: 'Pay Date',
        render: (period) => formatFinanceDateLabel(period.payDate),
      },
      {
        key: 'status',
        header: 'Status',
        render: (period) => <StatusBadge status={period.status} />,
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Payroll Periods" description="Quản lý kỳ lương">
        <PermissionDeniedInline message="Bạn cần finance.payroll.read để mở trang này." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <Button disabled={!activeRegionId} onClick={() => setIsCreating(!isCreating)} variant="primary">
          {isCreating ? 'Hủy tạo' : 'Tạo Payroll Period'}
        </Button>
      }
      title="Payroll Periods"
      description="Quản lý và tạo mới cấu hình kỳ lương cho các region."
    >
      <ReadonlyBanner
        message={
          activeRegionId
            ? 'Payroll periods đang hiển thị theo region hiện tại. Tạo mới sẽ gắn trực tiếp vào region đang được chọn.'
            : 'Chọn region hợp lệ trước khi xem hoặc tạo payroll period mới.'
        }
      />

      <EntityHeader
        eyebrow="Finance / Payroll Periods"
        metadata={
          <>
            <span>Active scope: {selectedRegionLabel}</span>
            <span>Visible periods: {periodCount}</span>
            <span>Create mode: {isCreating ? 'Open' : 'Closed'}</span>
          </>
        }
        title="Regional payroll calendar"
      />

      <section className="surface-panel command-stage" aria-label="Payroll periods command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Regional queue</span>
            <span className={activeRegionId ? 'meta-chip-success' : 'meta-chip'}>{selectedRegionLabel}</span>
            <span className={isCreating ? 'meta-chip-success' : 'meta-chip'}>{isCreating ? 'Create open' : 'Create closed'}</span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Finance / Payroll Periods</p>
            <strong className="action-summary-title">Manage the payroll calendar by region</strong>
            <p className="muted-text">
              Use the current region context to review payroll cycles, open a new period, and keep
              payment dates aligned with the operational calendar.
            </p>
          </div>
          <div className="meta-grid">
            <span>Region scope: {selectedRegionLabel}</span>
            <span>Visible periods: {periodCount}</span>
            <span>Create readiness: {requiredCreateFields}/3 required fields</span>
            <span>System scope: {principal?.scopeRoots.system ? 'Available' : 'Region-bound'}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Cycle controls</span>
            <strong>Period planning posture</strong>
            <p>
              Keep active region context, current period volume, and create readiness visible before
              publishing a new payroll cycle.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Region context</strong>
                <span className="muted-text">Current scope that drives both listing and creation.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{selectedRegionLabel}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Open cycles</strong>
                <span className="muted-text">Current number of payroll periods visible in this workspace.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{periodCount}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Create access</strong>
                <span className="muted-text">A region must be selected before a new period can be saved.</span>
              </div>
              <div className="command-support-stack">
                <span className={activeRegionId ? 'meta-chip-success' : 'meta-chip'}>
                  {activeRegionId ? 'Ready' : 'Region required'}
                </span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Payroll periods summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="event_note" />
            </span>
            <span className="workspace-stat-badge">Calendar</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Visible payroll periods</span>
            <strong className="workspace-stat-value">{periodCount}</strong>
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
            <span className="workspace-stat-label">Active region</span>
            <strong className="workspace-stat-value">{selectedRegionLabel}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="edit_calendar" />
            </span>
            <span className={isCreating ? 'workspace-stat-badge success' : 'workspace-stat-badge warning'}>
              Create
            </span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Create panel</span>
            <strong className="workspace-stat-value">{isCreating ? 'Open' : 'Closed'}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="rule" />
            </span>
            <span className="workspace-stat-badge warning">Ready</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Required create fields</span>
            <strong className="workspace-stat-value">{requiredCreateFields}/3</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
          <Card title="Bộ lọc">
            <div className="field-grid">
              {regionOptions.length > 0 ? (
                <Select
                  label="Region"
                  onChange={(event) => setRegionFilter(event.target.value)}
                  options={regionOptions}
                  placeholder={principal?.scopeRoots.system ? 'All regions (system scope)' : 'Select region'}
                  value={regionFilter}
                />
              ) : (
                <Input
                  label="Region ID"
                  onChange={(event) => setRegionFilter(event.target.value)}
                  placeholder={principal?.scopeRoots.system ? 'Optional region filter' : 'Region required'}
                  type="number"
                  value={regionFilter}
                />
              )}
            </div>
          </Card>

          <Card title="Payroll period register">
            <DataTable
              columns={columns}
              emptyDescription={
                canQuery
                  ? 'Không có payroll period nào khớp bộ lọc hiện tại.'
                  : 'Chọn region hợp lệ trước khi xem danh sách payroll period.'
              }
              emptyTitle={canQuery ? 'No matching payroll periods' : 'Region required'}
              error={periodsQuery.error ? getFinanceErrorMessage(periodsQuery.error, 'Không thể tải danh sách payroll periods.') : null}
              loading={periodsQuery.isLoading}
              loadingDescription="Đang tải danh sách..."
              loadingTitle="Đang tải"
              onRetry={() => void periodsQuery.refetch()}
              rowKey={(period) => period.id}
              rows={periodsQuery.data ?? []}
            />
          </Card>
        </div>

        <aside className="surface-grid-side">
          {isCreating ? (
            <Card title="Tạo Payroll Period mới">
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (createForm.name && createForm.startDate && createForm.endDate && activeRegionId) {
                    createMutation.mutate({
                      name: createForm.name,
                      startDate: createForm.startDate,
                      endDate: createForm.endDate,
                      payDate: createForm.payDate,
                      note: createForm.note,
                      regionId: activeRegionId,
                    })
                  }
                }}
              >
                <div className="field-grid">
                  <Input
                    label="Region ID"
                    value={activeRegionId?.toString() || ''}
                    disabled
                  />
                  <Input
                    label="Tên kỳ lương"
                    placeholder="VD: Tháng 3/2026"
                    required
                    value={createForm.name || ''}
                    onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                  />
                  <Input
                    label="Ngày bắt đầu (YYYY-MM-DD)"
                    type="date"
                    required
                    value={createForm.startDate || ''}
                    onChange={(e) => setCreateForm({ ...createForm, startDate: e.target.value })}
                  />
                  <Input
                    label="Ngày kết thúc (YYYY-MM-DD)"
                    type="date"
                    required
                    value={createForm.endDate || ''}
                    onChange={(e) => setCreateForm({ ...createForm, endDate: e.target.value })}
                  />
                  <Input
                    label="Ngày thanh toán (YYYY-MM-DD)"
                    type="date"
                    value={createForm.payDate || ''}
                    onChange={(e) => setCreateForm({ ...createForm, payDate: e.target.value })}
                  />
                  <Input
                    label="Ghi chú"
                    value={createForm.note || ''}
                    onChange={(e) => setCreateForm({ ...createForm, note: e.target.value })}
                  />
                </div>

                {createMutation.error && (
                  <p className="error-text section-spacing-top">
                    {getFinanceErrorMessage(createMutation.error, 'Lỗi khi tạo kỳ lương')}
                  </p>
                )}

                <div className="section-spacing-top">
                  <Button type="submit" variant="primary" disabled={createMutation.isPending || !activeRegionId}>
                    {createMutation.isPending ? 'Đang tạo...' : 'Lưu lại'}
                  </Button>
                </div>
              </form>
            </Card>
          ) : (
            <section className="surface-panel command-table-stack">
              <div className="state-panel-heading">
                <span className="eyebrow">Create guidance</span>
                <h2 className="card-title">Cycle setup notes</h2>
                <p className="muted-text">
                  Open the create panel once the correct region is selected and the next payroll
                  cycle dates are ready to be published.
                </p>
              </div>
              <div className="command-support-list">
                <article className="command-support-item">
                  <div className="command-support-copy">
                    <strong>Region-bound creation</strong>
                    <span className="muted-text">Each payroll period is created against the active region in scope.</span>
                  </div>
                  <div className="command-support-stack">
                    <span className={activeRegionId ? 'meta-chip-success' : 'meta-chip'}>
                      {activeRegionId ? 'Scoped' : 'Pick region'}
                    </span>
                  </div>
                </article>
                <article className="command-support-item">
                  <div className="command-support-copy">
                    <strong>Required fields</strong>
                    <span className="muted-text">Name, start date, and end date are the minimum values needed to create.</span>
                  </div>
                  <div className="command-support-stack">
                    <span className="command-support-metric">3</span>
                  </div>
                </article>
                <article className="command-support-item">
                  <div className="command-support-copy">
                    <strong>Payment planning</strong>
                    <span className="muted-text">Pay date and note stay optional, but should be filled when known.</span>
                  </div>
                  <div className="command-support-stack">
                    <span className="meta-chip">Optional</span>
                  </div>
                </article>
              </div>
            </section>
          )}
        </aside>
      </div>
    </DashboardLayout>
  )
}
