import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  DataTable,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  MaskedField,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { maskedEmptyState } from '@shared/utils/tableHelpers'
import { usePayrollRun, useSubmitPayrollRun } from '../hooks/useHr'
import type { PayrollEmployeeResult } from '../model/hr.types'
import { getHrErrorMessage } from '../services/hrError.service'
import {
  buildPayrollSummary,
  formatCurrencyAmount,
  formatDateLabel,
  formatDecimal,
} from '../services/hrReadModel.service'
import {
  canReadPayroll,
} from '../services/hrPermission.service'
import { payrollPrepUiPolicy } from '../services/payrollPrepUiPolicy.service'

export function PayrollDraftReviewPage() {
  const { runId: runIdParam } = useParams<{ runId: string }>()
  const runId = Number(runIdParam)
  const principal = usePrincipal()
  const canReadRun = canReadPayroll(principal)
  const canReadDetail = payrollPrepUiPolicy.canReadDetails(principal)
  const canPrepareRun = payrollPrepUiPolicy.canPrepare(principal)
  const runQuery = usePayrollRun(runId, {
    enabled: canReadRun && Number.isFinite(runId),
  })
  const submitPayrollRun = useSubmitPayrollRun()
  const [submitError, setSubmitError] = useState<string | null>(null)

  const summary = useMemo(
    () => (runQuery.data ? buildPayrollSummary(runQuery.data) : null),
    [runQuery.data],
  )
  const exceptionRows = useMemo(
    () => (runQuery.data?.employees ?? []).filter((employee) => employee.exceptionMessage),
    [runQuery.data?.employees],
  )

  usePageTitle(runQuery.data ? `${runQuery.data.runCode} — Payroll Draft Review` : 'Payroll Draft Review — HR')

  const employeeColumns = useMemo<Array<DataTableColumn<PayrollEmployeeResult>>>(
    () => [
      {
        key: 'employee',
        header: 'Employee',
        render: (employee) => `#${employee.employeeId ?? '—'}`,
      },
      {
        key: 'contract',
        header: 'Contract',
        render: (employee) => (employee.contractId ? `#${employee.contractId}` : '—'),
      },
      {
        key: 'outlet',
        header: 'Outlet',
        render: (employee) => (employee.outletId ? `#${employee.outletId}` : '—'),
      },
      {
        key: 'hours',
        header: 'Work / OT',
        render: (employee) => `${formatDecimal(employee.workHours)} / ${formatDecimal(employee.overtimeHours)}`,
      },
      {
        key: 'netPay',
        header: 'Net pay',
        render: (employee) => (canReadDetail ? formatCurrencyAmount(employee.netPay) : '••••••'),
      },
      {
        key: 'paymentStatus',
        header: 'Payment status',
        render: (employee) => <StatusBadge status={employee.paymentStatus} />,
      },
    ],
    [canReadDetail],
  )

  const exceptionColumns = useMemo<Array<DataTableColumn<PayrollEmployeeResult>>>(
    () => [
      {
        key: 'employee',
        header: 'Employee',
        render: (employee) => `#${employee.employeeId ?? '—'}`,
      },
      {
        key: 'exception',
        header: 'Exception',
        render: (employee) => employee.exceptionMessage ?? '—',
      },
      {
        key: 'netPay',
        header: 'Net pay',
        render: (employee) => (canReadDetail ? formatCurrencyAmount(employee.netPay) : '••••••'),
      },
    ],
    [canReadDetail],
  )

  if (!canReadRun) {
    return (
      <DashboardLayout title="Payroll Draft Review" description="Review-first payroll draft workspace">
        <PermissionDeniedInline message="Bạn cần finance.payroll.read để mở payroll draft review." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(runId) || runId <= 0) {
    return (
      <DashboardLayout title="Payroll Draft Review" description="Review-first payroll draft workspace">
        <EmptyState description="URL không chứa runId hợp lệ." title="Thiếu runId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (runQuery.isLoading) {
    return (
      <DashboardLayout title="Payroll Draft Review" description="Review-first payroll draft workspace">
        <EmptyState description="Đang tải payroll run summary, employee results và exceptions..." title="Đang tải payroll draft" />
      </DashboardLayout>
    )
  }

  if (runQuery.error) {
    return (
      <DashboardLayout
        title="Payroll Draft Review"
        description="Review-first payroll draft workspace"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/payroll-preparation">Quay lại preparation</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Tải lại"
          message={getHrErrorMessage(runQuery.error, 'Không thể tải payroll run detail.')}
          onAction={() => void runQuery.refetch()}
          title="Không thể tải payroll run"
        />
      </DashboardLayout>
    )
  }

  if (!runQuery.data || !summary) {
    return (
      <DashboardLayout
        title="Payroll Draft Review"
        description="Review-first payroll draft workspace"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/payroll-preparation">Quay lại preparation</Link>
          </Button>
        }
      >
        <EmptyState description="Payroll run này không tồn tại hoặc nằm ngoài phạm vi hiện tại." title="Không tìm thấy payroll run" />
      </DashboardLayout>
    )
  }

  const run = runQuery.data
  const canSubmitToFinance =
    canPrepareRun && (run.status.toUpperCase() === 'DRAFT' || run.status.toUpperCase() === 'REJECTED')

  async function handleSubmitToFinance() {
    setSubmitError(null)

    try {
      await submitPayrollRun.mutateAsync({ runId: run.id })
    } catch (error) {
      setSubmitError(getHrErrorMessage(error, 'Không thể submit payroll run sang Finance.'))
    }
  }

  return (
    <DashboardLayout
      title="Payroll Draft Review"
      description="Summary + detail + exception review cho payroll draft trước phase finance approval."
      actions={
        <div className="form-actions">
          {canSubmitToFinance ? (
            <Button loading={submitPayrollRun.isPending} onClick={() => void handleSubmitToFinance()} size="sm">
              Submit to Finance
            </Button>
          ) : null}
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/payroll-preparation">Quay lại preparation</Link>
          </Button>
        </div>
      }
    >
      <ReadonlyBanner
        message={
          run.status.toUpperCase() === 'SUBMITTED'
            ? 'Payroll run đã được submit sang Finance và sẵn sàng cho approval queue.'
            : payrollPrepUiPolicy.isRunReadonly(run.status)
            ? 'Payroll run đang ở trạng thái terminal/read-only.'
            : 'HR hiện publish payroll draft review theo chế độ review-first. Approval/payment vẫn thuộc phase Finance tiếp theo.'
        }
      />
      {submitError ? <ErrorState message={submitError} title="Không thể submit payroll run" /> : null}

      <EntityHeader
        eyebrow="HR / Payroll"
        metadata={
          <>
            <span>Period #{run.payrollPeriodId}</span>
            <span>Run date: {formatDateLabel(run.runDate)}</span>
            <span>Submitted: {formatDateLabel(run.submittedAt)}</span>
            <span>Approved: {formatDateLabel(run.approvedAt)}</span>
          </>
        }
        status={<StatusBadge status={run.status} />}
        title={run.runCode}
      />

      <div className="card-grid">
        <MaskedField
          helperText={
            canReadDetail
              ? 'Visible because principal has finance.payroll.detail.read.'
              : 'Masked because employee-level payroll detail is restricted.'
          }
          label="Draft payout total"
          mode={canReadDetail ? 'readonly-visible' : 'masked'}
          value={formatCurrencyAmount(summary.totalAmount)}
        />
        <MaskedField
          helperText={
            canReadDetail
              ? 'Aggregated net pay across employee results.'
              : 'Masked because employee-level payroll detail is restricted.'
          }
          label="Aggregated net pay"
          mode={canReadDetail ? 'readonly-visible' : 'masked'}
          value={formatCurrencyAmount(summary.totalNetPay)}
        />
        <MaskedField
          helperText="Employee result count from current payroll run."
          label="Employee result count"
          mode="readonly-visible"
          value={summary.employeeCount.toString()}
        />
        <MaskedField
          helperText="Employees with exceptionMessage populated in backend payroll run."
          label="Exceptions"
          mode="readonly-visible"
          value={summary.exceptionCount.toString()}
        />
      </div>

      {!canReadDetail ? (
        <PermissionDeniedInline message="Employee-level gross/net/tax details được backend masked vì thiếu finance.payroll.detail.read." />
      ) : null}

      <FormSection description="Toàn bộ employee results trong payroll draft hiện tại." title="Employee results">
        <DataTable
          columns={employeeColumns}
          {...maskedEmptyState(
            !canReadDetail,
            'Results masked by backend',
            'Employee results bị backend ẩn vì thiếu finance.payroll.detail.read. Liên hệ admin để được cấp quyền.',
            'No employee results',
            'Payroll run này chưa có employee results.',
          )}
          rowKey={(employee) => employee.id}
          rows={run.employees}
        />
      </FormSection>

      <FormSection description="Tập trung các dòng có exception message để review trước khi submit sang Finance." title="Exception review">
        <DataTable
          columns={exceptionColumns}
          emptyDescription="Không có exception nào trong payroll draft này."
          emptyTitle="No payroll exceptions"
          rowKey={(employee) => employee.id}
          rows={exceptionRows}
        />
      </FormSection>
    </DashboardLayout>
  )
}
