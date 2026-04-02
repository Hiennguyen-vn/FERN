import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  DataTable,
  EmptyState,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
  Textarea,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
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
import { toOptionalNumber, parsePositiveInt } from '@shared/validators/parseInput'

export function PayrollPreparationPage() {
  usePageTitle('HR Payroll Preparation')
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

  const regionOptions = useMemo<SelectOption[]>(
    () => regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    [regionIds],
  )

  const periodOptions = useMemo<SelectOption[]>(
    () =>
      (periodsQuery.data ?? []).map((period) => ({
        label: `${period.referenceCode} · ${period.name}`,
        value: String(period.id),
      })),
    [periodsQuery.data],
  )

  const periodColumns = useMemo<Array<DataTableColumn<PayrollPeriod>>>(
    () => [
      {
        key: 'reference',
        header: 'Period',
        render: (period) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{period.referenceCode}</strong>
            <span className="muted-text">{period.name}</span>
          </div>
        ),
      },
      {
        key: 'dates',
        header: 'Date range',
        render: (period) => formatDateRange(period.startDate, period.endDate),
      },
      {
        key: 'payDate',
        header: 'Pay date',
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
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{run.runCode}</strong>
            <span className="muted-text">Period #{run.payrollPeriodId}</span>
          </div>
        ),
      },
      {
        key: 'runDate',
        header: 'Run date',
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
      setPeriodError('Chọn region hợp lệ trước khi tạo payroll period.')
      return
    }
    if (!periodForm.name.trim() || !periodForm.startDate || !periodForm.endDate) {
      setPeriodError('Name, start date và end date là bắt buộc.')
      return
    }
    if (new Date(periodForm.endDate).getTime() < new Date(periodForm.startDate).getTime()) {
      setPeriodError('endDate phải cùng ngày hoặc sau startDate.')
      return
    }
    if (periodForm.payDate && new Date(periodForm.payDate).getTime() < new Date(periodForm.endDate).getTime()) {
      setPeriodError('payDate phải cùng ngày hoặc sau endDate.')
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
      setPeriodError(getHrErrorMessage(error, 'Không thể tạo payroll period.'))
    }
  }

  async function handleCreateRun() {
    setRunError(null)
    setFailedRunId(null)
    // parsePositiveInt rejects 0, negatives, and non-integers (backend: payrollPeriodId @NotNull Long)
    const payrollPeriodId = parsePositiveInt(runForm.payrollPeriodId)
    if (!payrollPeriodId) {
      setRunError('Chọn payroll period trước khi prepare draft run.')
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
      setRunError(getHrErrorMessage(error, 'Không thể prepare payroll draft run.'))
      return
    }

    const runId = Number(run.id)
    if (!Number.isFinite(runId) || runId <= 0) {
      setRunError('Payroll draft đã được tạo nhưng response không trả về runId hợp lệ để submit sang Finance.')
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
          ? `Đã tạo payroll draft #${runId} nhưng chưa submit sang Finance. ${detail}`
          : `Đã tạo payroll draft #${runId} nhưng chưa submit sang Finance.`,
      )
    }
  }

  if (!canOpen) {
    return (
      <DashboardLayout title="Payroll Preparation" description="Prepare payroll periods và draft payroll runs theo region">
        <PermissionDeniedInline message="Bạn cần finance.payroll.read hoặc finance.payroll.prepare để mở payroll workspace." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Payroll Preparation"
      description="Section-form workflow để chuẩn bị payroll period và draft payroll run cho phase finance tiếp theo."
    >
      {!activeRegionId && !principal?.scopeRoots.system ? (
        <ReadonlyBanner message="Chọn region trong app shell trước khi dùng payroll preparation. Finance payroll APIs yêu cầu region context rõ ràng." />
      ) : !canPrepare ? (
        <ReadonlyBanner message="Bạn đang ở chế độ read-only. Cần finance.payroll.prepare để tạo payroll period hoặc prepare payroll run." />
      ) : (
        <ReadonlyBanner message="Payroll preparation hiện publish ở workflow-first mode: tạo period, prepare + submit payroll run, rồi review run ở màn kế tiếp." />
      )}

      <FormSection description="Region scope quyết định period và runs nào được hiển thị hoặc prepare." title="Preparation context">
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
      </FormSection>

      <FormSection description="Chuẩn bị payroll period làm cơ sở cho các draft payroll runs." title="Create payroll period">
        {canPrepare ? (
          <>
            <div className="field-grid">
              <Input
                label="Period name"
                onChange={(event) => setPeriodForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="VD: Payroll March 2026"
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
          <PermissionDeniedInline message="Bạn cần finance.payroll.prepare để tạo payroll period." />
        )}
      </FormSection>

      <FormSection description="Prepare payroll draft run từ một payroll period đã có." title="Prepare payroll draft">
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
                  placeholder="Nhập payroll period ID"
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
                periodsQuery.data && periodsQuery.data.length > 0 ? (
                  <Button
                    disabled={isSubmittingRun}
                    onClick={() =>
                      setRunForm((current) => ({
                        ...current,
                        payrollPeriodId: current.payrollPeriodId || String(periodsQuery.data![0].id),
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
              <div className="form-actions">
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
          <PermissionDeniedInline message="Bạn cần finance.payroll.prepare để prepare payroll draft run." />
        )}
      </FormSection>

      <FormSection description="Các payroll periods khả dụng trong phạm vi đang xem." title="Payroll periods">
        {canRead ? (
          <DataTable
            columns={periodColumns}
            emptyDescription="Chưa có payroll period nào trong phạm vi đang xem."
            emptyTitle="No payroll periods"
            error={periodsQuery.error ? getHrErrorMessage(periodsQuery.error, 'Không thể tải payroll periods.') : null}
            errorTitle="Không thể tải payroll periods"
            loading={periodsQuery.isLoading}
            loadingDescription="Đang tải payroll periods..."
            loadingTitle="Loading payroll periods"
            onRetry={() => void periodsQuery.refetch()}
            rowKey={(period) => period.id}
            rows={periodsQuery.data ?? []}
          />
        ) : (
          <PermissionDeniedInline message="Bạn cần finance.payroll.read để xem payroll periods." />
        )}
      </FormSection>

      <FormSection description="Recent payroll runs để mở review ngay sau khi prepare." title="Payroll runs">
        {canRead ? (
          <DataTable
            columns={runColumns}
            emptyDescription="Chưa có payroll run nào trong phạm vi đang xem."
            emptyTitle="No payroll runs"
            error={runsQuery.error ? getHrErrorMessage(runsQuery.error, 'Không thể tải payroll runs.') : null}
            errorTitle="Không thể tải payroll runs"
            loading={runsQuery.isLoading}
            loadingDescription="Đang tải payroll runs..."
            loadingTitle="Loading payroll runs"
            onRetry={() => void runsQuery.refetch()}
            onRowClick={(run) => navigate(`/hr/payroll-draft-review/${run.id}`)}
            rowKey={(run) => run.id}
            rows={runsQuery.data ?? []}
          />
        ) : (
          <EmptyState
            description="Bạn có quyền prepare nhưng không có finance.payroll.read, nên danh sách periods/runs không hiển thị ở đây."
            title="Read-side payroll data unavailable"
          />
        )}
      </FormSection>
    </DashboardLayout>
  )
}
