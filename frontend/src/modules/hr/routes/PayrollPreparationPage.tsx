import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  FormActions,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
  Textarea,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { toOptionalNumber, parsePositiveInt } from '@shared/validators/parseInput'
import {
  useCreatePayrollPeriod,
  useCreatePayrollRun,
  usePayrollPeriods,
  usePayrollRuns,
  useSubmitPayrollRun,
} from '../hooks/useHr'
import type { PayrollPeriod, PayrollRun } from '../model/hr.types'
import { getHrErrorMessage } from '../services/hrError.service'
import {
  formatCurrencyAmount,
  formatDateLabel,
  formatDateRange,
} from '../services/hrReadModel.service'
import {
  canPreparePayroll,
  canReadPayroll,
} from '../services/hrPermission.service'
import { payrollPrepUiPolicy } from '../services/payrollPrepUiPolicy.service'

function buildRunRows(runs: PayrollRun[]) {
  return [...runs].sort((left, right) => right.runDate.localeCompare(left.runDate))
}

export function PayrollPreparationPage() {
  usePageTitle('Payroll Preparation')

  const navigate = useNavigate()
  const principal = usePrincipal()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canOpen = payrollPrepUiPolicy.canOpenPayrollPreparation(principal)
  const canRead = canReadPayroll(principal)
  const canPrepare = canPreparePayroll(principal)
  const [selectedRegion, setSelectedRegion] = useState(
    selectedRegionId ? String(selectedRegionId) : regionIds.length === 1 ? String(regionIds[0]) : '',
  )
  const [periodForm, setPeriodForm] = useState({
    endDate: '',
    name: '',
    note: '',
    payDate: '',
    startDate: '',
  })
  const [runForm, setRunForm] = useState({
    note: '',
    payrollPeriodId: '',
    runDate: '',
  })
  const [periodError, setPeriodError] = useState<string | null>(null)
  const [runError, setRunError] = useState<string | null>(null)
  const [failedRunId, setFailedRunId] = useState<number | null>(null)

  const activeRegionId = toOptionalNumber(selectedRegion)
  const canQuery = Boolean(activeRegionId) || Boolean(principal?.scopeRoots.system)
  const periodsQuery = usePayrollPeriods(activeRegionId, { enabled: canRead && canQuery })
  const runsQuery = usePayrollRuns(activeRegionId, { enabled: canRead && canQuery })
  const createPayrollPeriod = useCreatePayrollPeriod()
  const createPayrollRun = useCreatePayrollRun()
  const submitPayrollRun = useSubmitPayrollRun()
  const isSubmittingRun = createPayrollRun.isPending || submitPayrollRun.isPending
  const payrollPeriods = periodsQuery.data ?? []
  const payrollRuns = buildRunRows(runsQuery.data ?? [])

  const regionOptions = useMemo<SelectOption[]>(
    () => regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    [regionIds],
  )

  const periodOptions = useMemo<SelectOption[]>(
    () =>
      payrollPeriods.map((period) => ({
        label: `${period.referenceCode} · ${period.name}`,
        value: String(period.id),
      })),
    [payrollPeriods],
  )

  const latestRun = payrollRuns[0] ?? null
  const estimatedPayout = latestRun?.totalAmount ?? payrollRuns.reduce((sum, run) => sum + (run.totalAmount ?? 0), 0)
  const openPeriods = payrollPeriods.filter((period) => ['OPEN', 'DRAFT', 'ACTIVE'].includes(period.status.toUpperCase())).length
  const awaitingFinance = payrollRuns.filter((run) => ['DRAFT', 'SUBMITTED'].includes(run.status.toUpperCase())).length
  const missingInputs = [!activeRegionId && !principal?.scopeRoots.system, payrollPeriods.length === 0, !canPrepare].filter(Boolean).length

  const readinessItems = [
    {
      description: activeRegionId ? `Region #${activeRegionId} is active for this workspace.` : 'Select a region or use system scope.',
      icon: 'location_on',
      ready: Boolean(activeRegionId) || Boolean(principal?.scopeRoots.system),
      title: 'Region context',
    },
    {
      description: payrollPeriods.length > 0 ? `${payrollPeriods.length} payroll periods are available.` : 'Create a payroll period before preparing a draft run.',
      icon: 'calendar_today',
      ready: payrollPeriods.length > 0,
      title: 'Payroll periods',
    },
    {
      description: canPrepare ? 'Prepare and submit controls are enabled.' : 'This principal can review but not generate payroll drafts.',
      icon: 'rule',
      ready: canPrepare,
      title: 'Preparation access',
    },
    {
      description: canRead ? 'Payroll periods and run history are visible.' : 'Read-side payroll data is not visible in this scope.',
      icon: 'table_view',
      ready: canRead,
      title: 'Read-side visibility',
    },
  ]

  const exceptionItems = [
    !activeRegionId && !principal?.scopeRoots.system ? 'Region context is missing.' : null,
    payrollPeriods.length === 0 ? 'No payroll periods are available for the current region.' : null,
    periodError,
    runError,
  ].filter(Boolean) as string[]

  const periodColumns = useMemo<Array<DataTableColumn<PayrollPeriod>>>(
    () => [
      {
        key: 'reference',
        header: 'Period',
        render: (period) => (
          <div className="cell-stack">
            <strong>{period.referenceCode}</strong>
            <span className="cell-subtitle">{period.name}</span>
          </div>
        ),
      },
      {
        key: 'dates',
        header: 'Date Range',
        render: (period) => formatDateRange(period.startDate, period.endDate),
      },
      {
        key: 'payDate',
        header: 'Pay Date',
        render: (period) => formatDateLabel(period.payDate),
      },
      {
        key: 'status',
        header: 'Status',
        render: (period) => <StatusBadge status={period.status} />,
      },
    ],
    [],
  )

  const runColumns = useMemo<Array<DataTableColumn<PayrollRun>>>(
    () => [
      {
        key: 'run',
        header: 'Run',
        render: (run) => (
          <div className="cell-stack">
            <strong>{run.runCode}</strong>
            <span className="cell-subtitle">Period #{run.payrollPeriodId}</span>
          </div>
        ),
      },
      {
        key: 'runDate',
        header: 'Run Date',
        render: (run) => formatDateLabel(run.runDate),
      },
      {
        key: 'status',
        header: 'Status',
        render: (run) => <StatusBadge status={run.status} />,
      },
      {
        key: 'total',
        header: 'Total',
        render: (run) => formatCurrencyAmount(run.totalAmount),
      },
    ],
    [],
  )

  async function handleCreatePeriod() {
    setPeriodError(null)
    if (!activeRegionId) {
      setPeriodError('Select a valid region before creating a payroll period.')
      return
    }
    if (!periodForm.name.trim() || !periodForm.startDate || !periodForm.endDate) {
      setPeriodError('Name, start date, and end date are required.')
      return
    }
    if (new Date(periodForm.endDate).getTime() < new Date(periodForm.startDate).getTime()) {
      setPeriodError('End date must be on or after the start date.')
      return
    }
    if (periodForm.payDate && new Date(periodForm.payDate).getTime() < new Date(periodForm.endDate).getTime()) {
      setPeriodError('Pay date must be on or after the period end date.')
      return
    }

    try {
      const period = await createPayrollPeriod.mutateAsync({
        endDate: periodForm.endDate,
        name: periodForm.name.trim(),
        note: periodForm.note.trim() || undefined,
        payDate: periodForm.payDate || undefined,
        regionId: activeRegionId,
        startDate: periodForm.startDate,
      })
      setRunForm((current) => ({ ...current, payrollPeriodId: String(period.id) }))
      setPeriodForm({ endDate: '', name: '', note: '', payDate: '', startDate: '' })
    } catch (error) {
      setPeriodError(getHrErrorMessage(error, 'Unable to create the payroll period.'))
    }
  }

  async function handleCreateRun() {
    setRunError(null)
    setFailedRunId(null)
    const payrollPeriodId = parsePositiveInt(runForm.payrollPeriodId)
    if (!payrollPeriodId) {
      setRunError('Select a payroll period before preparing a draft run.')
      return
    }

    let run: { id: number }
    try {
      run = await createPayrollRun.mutateAsync({
        note: runForm.note.trim() || undefined,
        payrollPeriodId,
        runDate: runForm.runDate || undefined,
      })
    } catch (error) {
      setRunError(getHrErrorMessage(error, 'Unable to prepare the payroll draft run.'))
      return
    }

    const runId = Number(run.id)
    if (!Number.isFinite(runId) || runId <= 0) {
      setRunError('A payroll draft was created but the response did not include a valid run ID for finance submission.')
      return
    }

    try {
      await submitPayrollRun.mutateAsync({ runId })
      navigate(`/hr/payroll-draft-review/${runId}`)
    } catch (error) {
      const detail = getHrErrorMessage(error, '').trim()
      setFailedRunId(runId)
      setRunError(
        detail
          ? `Payroll draft #${runId} was created but could not be submitted to Finance. ${detail}`
          : `Payroll draft #${runId} was created but could not be submitted to Finance.`,
      )
    }
  }

  if (!canOpen) {
    return (
      <DashboardLayout
        description="Review readiness, create periods, and prepare payroll draft runs."
        eyebrow="Finance"
        title="Payroll Preparation"
      >
        <PermissionDeniedInline message="You need finance.payroll.read or finance.payroll.prepare to open payroll preparation." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      actions={
        <div className="form-actions align-start">
          <span className="workspace-inline-pill">
            <AppIcon name="location_on" size="sm" />
            {activeRegionId ? `Region #${activeRegionId}` : 'Region required'}
          </span>
          <span className="workspace-inline-pill">
            <AppIcon name="calendar_today" size="sm" />
            {payrollPeriods[0] ? payrollPeriods[0].referenceCode : 'No period selected'}
          </span>
        </div>
      }
      description="Review readiness, create periods, and prepare payroll draft runs."
      eyebrow="Finance"
      title="Payroll Preparation"
    >
      {!activeRegionId && !principal?.scopeRoots.system ? (
        <ReadonlyBanner
          label="Region required"
          message="Select a region in the app shell before preparing payroll data."
          title="Payroll preparation is waiting for context"
          tone="warning"
        />
      ) : !canPrepare ? (
        <ReadonlyBanner
          label="Read-only access"
          message="This principal can inspect payroll data but cannot create payroll periods or draft runs."
          title="Payroll preparation is in read-only mode"
          tone="warning"
        />
      ) : (
        <ReadonlyBanner
          label="Workflow first"
          message="Prepare a period, generate the draft run, then hand off the resulting draft to Finance review."
          title="Payroll preparation workspace"
        />
      )}

      <section className="payroll-summary-grid">
        <article className="payroll-summary-card primary">
          <div className="payroll-summary-card-header">
            <div>
              <p className="workspace-stat-label">
                Estimated total payout
              </p>
              <strong className="workspace-stat-value">{formatCurrencyAmount(estimatedPayout)}</strong>
            </div>
            <span className="workspace-stat-icon">
              <AppIcon filled name="account_balance_wallet" />
            </span>
          </div>
          <div className="detail-kpi-grid">
            <div className="detail-kpi">
              <span className="detail-kpi-label">
                Latest draft
              </span>
              <span className="detail-kpi-value">
                {latestRun?.runCode ?? 'None'}
              </span>
            </div>
            <div className="detail-kpi">
              <span className="detail-kpi-label">
                Awaiting finance
              </span>
              <span className="detail-kpi-value">
                {awaitingFinance}
              </span>
            </div>
          </div>
        </article>
        <article className="payroll-summary-card danger">
          <div className="payroll-summary-card-header">
            <div>
              <p className="workspace-stat-label">
                Missing inputs
              </p>
              <strong className="workspace-stat-value">{missingInputs}</strong>
            </div>
            <span className="workspace-stat-icon">
              <AppIcon filled name="warning" />
            </span>
          </div>
          <p className="muted-text">
            {missingInputs > 0 ? 'Action is required before the next payroll draft can be safely prepared.' : 'Core preparation inputs are available.'}
          </p>
        </article>
        <article className="payroll-summary-card success">
          <div className="payroll-summary-card-header">
            <div>
              <p className="workspace-stat-label">
                Open periods
              </p>
              <strong className="workspace-stat-value">{openPeriods}</strong>
            </div>
            <span className="workspace-stat-icon">
              <AppIcon filled name="task_alt" />
            </span>
          </div>
          <p className="muted-text">
            {awaitingFinance} draft runs are waiting for the finance review chain.
          </p>
        </article>
      </section>

      <section className="payroll-workspace-grid">
        <div className="surface-grid-main">
          <Card title="Preparation Context">
            <div className="field-grid">
              {regionOptions.length > 0 ? (
                <Select
                  label="Region"
                  onChange={(event) => setSelectedRegion(event.target.value)}
                  options={regionOptions}
                  placeholder={principal?.scopeRoots.system ? 'All regions (system scope)' : 'Select region'}
                  value={selectedRegion}
                />
              ) : (
                <Input
                  label="Region ID"
                  onChange={(event) => setSelectedRegion(event.target.value)}
                  placeholder={principal?.scopeRoots.system ? 'Optional region filter' : 'Region required'}
                  type="number"
                  value={selectedRegion}
                />
              )}
            </div>
            <p className="muted-text">
              Region scope determines which payroll periods and run history are visible and which draft actions are allowed.
            </p>
          </Card>

          <Card title="Readiness Checklist">
            <div className="checklist-grid">
              {readinessItems.map((item) => (
                <article className="checklist-item" key={item.title}>
                  <div className="checklist-item-head">
                    <AppIcon filled name={item.icon} size="sm" />
                    <strong>{item.title}</strong>
                  </div>
                  <span className="health-line">
                    <span className={`health-dot ${item.ready ? 'success' : 'warning'}`} />
                    {item.ready ? 'Ready' : 'Attention required'}
                  </span>
                  <span className="cell-subtitle">{item.description}</span>
                </article>
              ))}
            </div>
          </Card>

          <Card title="Create Payroll Period">
            {canPrepare ? (
              <>
                <div className="field-grid">
                  <Input
                    label="Period name"
                    onChange={(event) => setPeriodForm((current) => ({ ...current, name: event.target.value }))}
                    placeholder="Payroll March 2026"
                    value={periodForm.name}
                  />
                  <Input
                    label="Start date"
                    onChange={(event) => setPeriodForm((current) => ({ ...current, startDate: event.target.value }))}
                    type="date"
                    value={periodForm.startDate}
                  />
                  <Input
                    label="End date"
                    onChange={(event) => setPeriodForm((current) => ({ ...current, endDate: event.target.value }))}
                    type="date"
                    value={periodForm.endDate}
                  />
                  <Input
                    label="Pay date"
                    onChange={(event) => setPeriodForm((current) => ({ ...current, payDate: event.target.value }))}
                    type="date"
                    value={periodForm.payDate}
                  />
                </div>
                <Textarea
                  label="Note"
                  onChange={(event) => setPeriodForm((current) => ({ ...current, note: event.target.value }))}
                  placeholder="Optional period note"
                  value={periodForm.note}
                />
                <FormActions
                  primaryAction={
                    <Button loading={createPayrollPeriod.isPending} onClick={() => void handleCreatePeriod()} size="sm">
                      Create payroll period
                    </Button>
                  }
                />
                {periodError ? <p className="error-text">{periodError}</p> : null}
              </>
            ) : (
              <PermissionDeniedInline message="You need finance.payroll.prepare to create payroll periods." />
            )}
          </Card>

          <Card title="Payroll Periods">
            {canRead ? (
              <DataTable
                columns={periodColumns}
                emptyDescription="No payroll periods are available in the current region scope."
                emptyTitle="No payroll periods"
                error={periodsQuery.error ? getHrErrorMessage(periodsQuery.error, 'Unable to load payroll periods.') : null}
                errorTitle="Unable to load payroll periods"
                loading={periodsQuery.isLoading}
                loadingDescription="Loading payroll periods..."
                loadingTitle="Loading payroll periods"
                onRetry={() => void periodsQuery.refetch()}
                rowKey={(period) => period.id}
                rows={payrollPeriods}
              />
            ) : (
              <PermissionDeniedInline message="You need finance.payroll.read to view payroll periods." />
            )}
          </Card>

          <Card title="Draft Payroll Preview">
            {canRead ? (
              <DataTable
                columns={runColumns}
                emptyDescription="No payroll runs are available in the current region scope."
                emptyTitle="No payroll runs"
                error={runsQuery.error ? getHrErrorMessage(runsQuery.error, 'Unable to load payroll runs.') : null}
                errorTitle="Unable to load payroll runs"
                loading={runsQuery.isLoading}
                loadingDescription="Loading payroll runs..."
                loadingTitle="Loading payroll runs"
                onRetry={() => void runsQuery.refetch()}
                onRowClick={(run) => navigate(`/hr/payroll-draft-review/${run.id}`)}
                rowKey={(run) => run.id}
                rows={payrollRuns}
              />
            ) : (
              <EmptyState
                description="This principal can prepare payroll but does not have finance.payroll.read for run history."
                title="Payroll run history unavailable"
              />
            )}
          </Card>
        </div>

        <aside className="payroll-side-column">
          <Card className="sticky-side-card" title="Prepare Payroll Draft">
            {canPrepare ? (
              <>
                <div className="field-grid">
                  {periodOptions.length > 0 ? (
                    <Select
                      label="Payroll period"
                      onChange={(event) => setRunForm((current) => ({ ...current, payrollPeriodId: event.target.value }))}
                      options={periodOptions}
                      placeholder="Select payroll period"
                      value={runForm.payrollPeriodId}
                    />
                  ) : (
                    <Input
                      label="Payroll period ID"
                      onChange={(event) => setRunForm((current) => ({ ...current, payrollPeriodId: event.target.value }))}
                      placeholder="Enter payroll period ID"
                      type="number"
                      value={runForm.payrollPeriodId}
                    />
                  )}
                  <Input
                    label="Run date"
                    onChange={(event) => setRunForm((current) => ({ ...current, runDate: event.target.value }))}
                    type="date"
                    value={runForm.runDate}
                  />
                </div>
                <Textarea
                  label="Preparation note"
                  onChange={(event) => setRunForm((current) => ({ ...current, note: event.target.value }))}
                  placeholder="Optional draft preparation note"
                  value={runForm.note}
                />
                <FormActions
                  primaryAction={
                    <Button loading={isSubmittingRun} onClick={() => void handleCreateRun()} size="sm">
                      Prepare and submit payroll run
                    </Button>
                  }
                  secondaryAction={
                    payrollPeriods.length > 0 ? (
                      <Button
                        disabled={isSubmittingRun}
                        onClick={() =>
                          setRunForm((current) => ({
                            ...current,
                            payrollPeriodId: current.payrollPeriodId || String(payrollPeriods[0].id),
                          }))
                        }
                        size="sm"
                        variant="ghost"
                      >
                        Use latest period
                      </Button>
                    ) : null
                  }
                />
                {runError ? <p className="error-text">{runError}</p> : null}
                {failedRunId ? (
                  <div className="form-actions align-start">
                    <Button
                      disabled={isSubmittingRun}
                      onClick={() => navigate(`/hr/payroll-draft-review/${failedRunId}`)}
                      size="sm"
                      variant="secondary"
                    >
                      Open created draft run
                    </Button>
                  </div>
                ) : null}
              </>
            ) : (
              <PermissionDeniedInline message="You need finance.payroll.prepare to prepare payroll draft runs." />
            )}
          </Card>

          <Card title="Critical Exceptions">
            {exceptionItems.length > 0 ? (
              <ul className="payroll-side-list">
                {exceptionItems.map((item) => (
                  <li key={item}>
                    <strong>{item}</strong>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted-text">No blocking issues are currently preventing payroll preparation.</p>
            )}
          </Card>

          <Card title="Preparation Activity">
            <div className="activity-feed">
              {payrollRuns.slice(0, 3).map((run) => (
                <article className="activity-item" key={run.id}>
                  <div className="activity-item-head">
                    <strong className="activity-item-title">{run.runCode}</strong>
                    <StatusBadge status={run.status} />
                  </div>
                  <span className="cell-subtitle">
                    Run date: {formatDateLabel(run.runDate)} · Total: {formatCurrencyAmount(run.totalAmount)}
                  </span>
                </article>
              ))}
              {payrollRuns.length === 0 ? <p className="muted-text">No payroll preparation activity is available yet.</p> : null}
            </div>
          </Card>
        </aside>
      </section>
    </DashboardLayout>
  )
}
