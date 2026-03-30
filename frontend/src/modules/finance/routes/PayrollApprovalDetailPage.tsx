import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  ApprovalPanel,
  AuditMetaBlock,
  Button,
  ConfirmActionDialog,
  DataTable,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  MaskedField,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
  Textarea,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import {
  useApprovePayrollRun,
  useCancelPayrollRun,
  usePayrollRun,
  useRejectPayrollRun,
} from '../hooks/useFinance'
import type { FinancePayrollEmployeeResult } from '../model/finance.types'
import { getFinanceErrorMessage } from '../services/financeError.service'
import {
  buildPayrollSummary,
  formatFinanceCurrency,
  formatFinanceDateLabel,
} from '../services/financeWorkflow.service'
import { payrollApprovalUiPolicy } from '../services/payrollApprovalUiPolicy.service'

type DialogMode = 'approve' | 'reject' | 'cancel' | null

function approvalDescription(mode: Exclude<DialogMode, null>) {
  switch (mode) {
    case 'approve':
      return 'Xác nhận phê duyệt payroll run này để chuyển sang trạng thái APPROVED.'
    case 'reject':
      return 'Xác nhận từ chối payroll run này để trả về trạng thái REJECTED.'
    case 'cancel':
      return 'Xác nhận huỷ payroll run này. Chỉ draft hoặc rejected runs mới được cancel.'
  }
}

export function PayrollApprovalDetailPage() {
  const { runId: runIdParam } = useParams<{ runId: string }>()
  const runId = Number(runIdParam)
  const principal = usePrincipal()
  const canOpen = payrollApprovalUiPolicy.canOpenPayrollDetail(principal)
  const canReadDetail = payrollApprovalUiPolicy.canReadDetails(principal)
  const runQuery = usePayrollRun(runId, { enabled: canOpen && Number.isFinite(runId) })
  const approveMutation = useApprovePayrollRun()
  const rejectMutation = useRejectPayrollRun()
  const cancelMutation = useCancelPayrollRun()
  const [reviewNote, setReviewNote] = useState('')
  const [dialogMode, setDialogMode] = useState<DialogMode>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  usePageTitle(runQuery.data ? `${runQuery.data.runCode} — Finance Approval` : 'Payroll Approval Detail — Finance')

  const summary = useMemo(() => (runQuery.data ? buildPayrollSummary(runQuery.data) : null), [runQuery.data])
  const exceptionRows = useMemo(
    () => (runQuery.data?.employees ?? []).filter((employee) => employee.exceptionMessage),
    [runQuery.data?.employees],
  )

  const employeeColumns = useMemo<Array<DataTableColumn<FinancePayrollEmployeeResult>>>(
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
        key: 'work',
        header: 'Work / OT',
        render: (employee) => `${employee.workHours ?? '—'} / ${employee.overtimeHours ?? '—'}`,
      },
      {
        key: 'netPay',
        header: 'Net pay',
        render: (employee) =>
          canReadDetail ? formatFinanceCurrency(employee.netPay) : '••••••••',
      },
      {
        key: 'paymentStatus',
        header: 'Payment status',
        render: (employee) => <StatusBadge status={employee.paymentStatus} />,
      },
    ],
    [canReadDetail],
  )

  const exceptionColumns = useMemo<Array<DataTableColumn<FinancePayrollEmployeeResult>>>(
    () => [
      {
        key: 'employee',
        header: 'Employee',
        render: (employee) => `#${employee.employeeId ?? '—'}`,
      },
      {
        key: 'exception',
        header: 'Exception',
        render: (employee) => employee.exceptionMessage ?? 'No exception',
      },
      {
        key: 'netPay',
        header: 'Net pay',
        render: (employee) =>
          canReadDetail ? formatFinanceCurrency(employee.netPay) : '••••••••',
      },
    ],
    [canReadDetail],
  )

  async function confirmAction() {
    if (!runQuery.data || !dialogMode) {
      return
    }

    setActionError(null)

    try {
      if (dialogMode === 'approve') {
        await approveMutation.mutateAsync({ payload: { note: reviewNote.trim() || undefined }, runId: runQuery.data.id })
      }
      if (dialogMode === 'reject') {
        await rejectMutation.mutateAsync({ payload: { note: reviewNote.trim() || undefined }, runId: runQuery.data.id })
      }
      if (dialogMode === 'cancel') {
        await cancelMutation.mutateAsync({ payload: { note: reviewNote.trim() || undefined }, runId: runQuery.data.id })
      }
      setDialogMode(null)
    } catch (error) {
      setActionError(
        getFinanceErrorMessage(error, 'Không thể cập nhật payroll run trong approval workspace.'),
      )
    }
  }

  if (!canOpen) {
    return (
      <DashboardLayout title="Payroll Approval Detail" description="Approval workspace cho payroll run">
        <PermissionDeniedInline message="Bạn cần finance.payroll.read để mở payroll approval detail." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(runId) || runId <= 0) {
    return (
      <DashboardLayout
        title="Payroll Approval Detail"
        description="Approval workspace cho payroll run"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/payroll-approvals">Quay lại approvals</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa runId hợp lệ." title="Thiếu runId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (runQuery.isLoading) {
    return (
      <DashboardLayout title="Payroll Approval Detail" description="Approval workspace cho payroll run">
        <EmptyState description="Đang tải payroll run summary, employee results và exceptions..." title="Đang tải payroll approval detail" />
      </DashboardLayout>
    )
  }

  if (runQuery.error) {
    return (
      <DashboardLayout
        title="Payroll Approval Detail"
        description="Approval workspace cho payroll run"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/payroll-approvals">Quay lại approvals</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Tải lại"
          message={getFinanceErrorMessage(runQuery.error, 'Không thể tải payroll approval detail.')}
          onAction={() => void runQuery.refetch()}
          title="Không thể tải payroll run"
        />
      </DashboardLayout>
    )
  }

  if (!runQuery.data || !summary) {
    return (
      <DashboardLayout
        title="Payroll Approval Detail"
        description="Approval workspace cho payroll run"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/payroll-approvals">Quay lại approvals</Link>
          </Button>
        }
      >
        <EmptyState description="Payroll run này không tồn tại hoặc nằm ngoài scope hiện tại." title="Không tìm thấy payroll run" />
      </DashboardLayout>
    )
  }

  const run = runQuery.data
  const canApprove = payrollApprovalUiPolicy.canApprove(principal, run.status)
  const canReject = payrollApprovalUiPolicy.canReject(principal, run.status)
  const canCancel = payrollApprovalUiPolicy.canCancel(principal, run.status)
  const canMarkPaid = payrollApprovalUiPolicy.canMarkPaid(principal, run.status)

  let readonlyMessage =
    'Approval detail đang ở chế độ review-first. Kiểm tra exception và decision panel trước khi thao tác.'

  if (run.status.toUpperCase() === 'APPROVED') {
    readonlyMessage = 'Payroll run đã được phê duyệt. Dùng workflow Mark Paid để hoàn tất vòng đời thanh toán.'
  } else if (run.status.toUpperCase() === 'PAID') {
    readonlyMessage = 'Payroll run này đã PAID và đang ở trạng thái terminal/read-only.'
  } else if (run.status.toUpperCase() === 'CANCELLED') {
    readonlyMessage = 'Payroll run này đã CANCELLED và đang ở trạng thái terminal/read-only.'
  } else if (run.status.toUpperCase() === 'DRAFT' || run.status.toUpperCase() === 'REJECTED') {
    readonlyMessage = canCancel
      ? 'Run này không ở trạng thái review decision. Bạn chỉ có thể cancel nếu muốn đóng vòng đời draft/rejected run.'
      : 'Run này không ở trạng thái review decision. Quay lại preparation flow nếu cần chỉnh sửa hoặc prepare lại.'
  }

  return (
    <DashboardLayout
      title="Payroll Approval Detail"
      description="Approval workspace với summary, exceptions, audit/meta và decision actions theo lifecycle."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/payroll-approvals">Quay lại approvals</Link>
          </Button>
          {canMarkPaid ? (
            <Button asChild size="sm" variant="ghost">
              <Link to={`/finance/payroll-paid/${run.id}`}>Open payroll paid workflow</Link>
            </Button>
          ) : null}
        </div>
      }
    >
      <ReadonlyBanner message={readonlyMessage} />

      <EntityHeader
        eyebrow="Finance / Payroll Approval"
        metadata={
          <>
            <span>Period #{run.payrollPeriodId}</span>
            <span>Run date: {formatFinanceDateLabel(run.runDate)}</span>
            <span>Submitted: {formatFinanceDateLabel(run.submittedAt)}</span>
            <span>Approved: {formatFinanceDateLabel(run.approvedAt)}</span>
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
          label="Run total"
          mode={canReadDetail ? 'readonly-visible' : 'masked'}
          value={formatFinanceCurrency(summary.totalAmount)}
        />
        <MaskedField
          helperText={
            canReadDetail
              ? 'Aggregated employee net pay across current payroll run.'
              : 'Masked because employee-level payroll detail is restricted.'
          }
          label="Aggregated net pay"
          mode={canReadDetail ? 'readonly-visible' : 'masked'}
          value={formatFinanceCurrency(summary.totalNetPay)}
        />
        <MaskedField label="Employee result count" mode="readonly-visible" value={summary.employeeCount} />
        <MaskedField label="Exceptions" mode="readonly-visible" value={summary.exceptionCount} />
      </div>

      {!canReadDetail ? (
        <PermissionDeniedInline message="Employee-level gross/net/tax details đang được masked vì thiếu finance.payroll.detail.read." />
      ) : null}

      <ApprovalPanel
        actions={
          <div className="form-actions align-start">
            {canApprove ? (
              <Button
                loading={approveMutation.isPending}
                onClick={() => setDialogMode('approve')}
                size="sm"
              >
                Approve
              </Button>
            ) : null}
            {canReject ? (
              <Button
                loading={rejectMutation.isPending}
                onClick={() => setDialogMode('reject')}
                size="sm"
                variant="danger"
              >
                Reject
              </Button>
            ) : null}
            {canCancel ? (
              <Button
                loading={cancelMutation.isPending}
                onClick={() => setDialogMode('cancel')}
                size="sm"
                variant="ghost"
              >
                Cancel run
              </Button>
            ) : null}
          </div>
        }
        description="Decision actions chỉ bật khi lifecycle và permission cho phép. Review note sẽ được gửi kèm approve/reject/cancel request."
        status={<StatusBadge status={run.status} />}
        title="Decision panel"
      >
        <Textarea
          label="Review note"
          onChange={(event) => setReviewNote(event.target.value)}
          placeholder="Ghi chú cho quyết định approve / reject / cancel"
          value={reviewNote}
        />
        {actionError ? (
          <ErrorState message={actionError} title="Không thể cập nhật payroll run" />
        ) : null}
      </ApprovalPanel>

      <AuditMetaBlock
        items={[
          { label: 'Run date', value: formatFinanceDateLabel(run.runDate) },
          { label: 'Submitted at', value: formatFinanceDateLabel(run.submittedAt) },
          { label: 'Approved at', value: formatFinanceDateLabel(run.approvedAt) },
          { label: 'Paid at', value: formatFinanceDateLabel(run.paidAt) },
          { label: 'Payment reference', value: run.paymentRef ?? 'No payment reference' },
          { label: 'Note', value: run.note ?? 'No note' },
        ]}
        title="Audit / lifecycle metadata"
      />

      <FormSection description="Toàn bộ employee results trong payroll run hiện tại." title="Employee results">
        <DataTable
          columns={employeeColumns}
          emptyDescription="Payroll run này chưa có employee results."
          emptyTitle="No employee results"
          rowKey={(employee) => employee.id}
          rows={run.employees}
        />
      </FormSection>

      <FormSection description="Tập trung các dòng có exception message để review trước khi approve." title="Exception review">
        <DataTable
          columns={exceptionColumns}
          emptyDescription="Payroll run này không có exception nào."
          emptyTitle="No payroll exceptions"
          rowKey={(employee) => employee.id}
          rows={exceptionRows}
        />
      </FormSection>

      <ConfirmActionDialog
        confirmLabel={
          dialogMode === 'approve' ? 'Approve run' : dialogMode === 'reject' ? 'Reject run' : 'Cancel run'
        }
        danger={dialogMode === 'reject' || dialogMode === 'cancel'}
        description={dialogMode ? approvalDescription(dialogMode) : ''}
        onCancel={() => setDialogMode(null)}
        onConfirm={() => void confirmAction()}
        open={dialogMode !== null}
        title={
          dialogMode === 'approve'
            ? 'Approve payroll run'
            : dialogMode === 'reject'
              ? 'Reject payroll run'
              : 'Cancel payroll run'
        }
      />
    </DashboardLayout>
  )
}
