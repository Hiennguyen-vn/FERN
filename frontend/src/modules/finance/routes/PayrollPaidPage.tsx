import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  AuditMetaBlock,
  Button,
  ConfirmActionDialog,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  MaskedField,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
  Textarea,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useMarkPayrollPaid, usePayrollRun } from '../hooks/useFinance'
import { getFinanceErrorMessage } from '../services/financeError.service'
import {
  buildPayrollSummary,
  formatFinanceCurrency,
  formatFinanceDateLabel,
} from '../services/financeWorkflow.service'
import { payrollApprovalUiPolicy } from '../services/payrollApprovalUiPolicy.service'

export function PayrollPaidPage() {
  const { runId: runIdParam } = useParams<{ runId: string }>()
  const runId = Number(runIdParam)
  const principal = usePrincipal()
  const canOpen = payrollApprovalUiPolicy.canOpenPayrollPaid(principal)
  const canReadDetail = payrollApprovalUiPolicy.canReadDetails(principal)
  const runQuery = usePayrollRun(runId, { enabled: canOpen && Number.isFinite(runId) })
  const markPaidMutation = useMarkPayrollPaid()
  const [paymentReference, setPaymentReference] = useState('')
  const [note, setNote] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  usePageTitle(runQuery.data ? `${runQuery.data.runCode} — Payroll Paid` : 'Payroll Paid — Finance')

  const summary = useMemo(() => (runQuery.data ? buildPayrollSummary(runQuery.data) : null), [runQuery.data])

  async function handleConfirmPaid() {
    if (!runQuery.data) {
      return
    }

    if (!paymentReference.trim()) {
      setValidationError('Payment reference là bắt buộc để mark payroll run as paid.')
      setDialogOpen(false)
      return
    }

    setValidationError(null)

    try {
      await markPaidMutation.mutateAsync({
        payload: {
          note: note.trim() || undefined,
          paymentReference: paymentReference.trim(),
        },
        runId: runQuery.data.id,
      })
      setDialogOpen(false)
    } catch (error) {
      setValidationError(getFinanceErrorMessage(error, 'Không thể mark payroll run as paid.'))
      setDialogOpen(false)
    }
  }

  if (!canOpen) {
    return (
      <DashboardLayout title="Payroll Paid" description="Mark-paid workflow cho payroll run đã được Finance approved">
        <PermissionDeniedInline message="Bạn cần finance.payroll.read hoặc finance.payroll.pay để mở payroll paid workflow." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(runId) || runId <= 0) {
    return (
      <DashboardLayout
        title="Payroll Paid"
        description="Mark-paid workflow cho payroll run đã được Finance approved"
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
      <DashboardLayout title="Payroll Paid" description="Mark-paid workflow cho payroll run đã được Finance approved">
        <EmptyState description="Đang tải payroll run để chuẩn bị mark-paid workflow..." title="Đang tải payroll run" />
      </DashboardLayout>
    )
  }

  if (runQuery.error) {
    return (
      <DashboardLayout
        title="Payroll Paid"
        description="Mark-paid workflow cho payroll run đã được Finance approved"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/payroll-approvals">Quay lại approvals</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Tải lại"
          message={getFinanceErrorMessage(runQuery.error, 'Không thể tải payroll paid workflow.')}
          onAction={() => void runQuery.refetch()}
          title="Không thể tải payroll run"
        />
      </DashboardLayout>
    )
  }

  if (!runQuery.data || !summary) {
    return (
      <DashboardLayout
        title="Payroll Paid"
        description="Mark-paid workflow cho payroll run đã được Finance approved"
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
  const canMarkPaid = payrollApprovalUiPolicy.canMarkPaid(principal, run.status)
  const isTerminal = payrollApprovalUiPolicy.isTerminal(run.status) || run.status.toUpperCase() === 'PAID'

  let message = 'Mark-paid workflow chỉ khả dụng cho payroll run ở trạng thái APPROVED.'
  if (run.status.toUpperCase() === 'APPROVED') {
    message = canMarkPaid
      ? 'Điền payment reference và note để hoàn tất mark-paid workflow.'
      : 'Bạn chỉ có quyền review run này. Cần finance.payroll.pay để mark as paid.'
  } else if (run.status.toUpperCase() === 'PAID') {
    message = 'Payroll run này đã PAID và đang ở trạng thái terminal/read-only.'
  }

  return (
    <DashboardLayout
      title="Payroll Paid"
      description="Controlled mark-paid workflow với terminal readonly presentation rõ ràng."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to={`/finance/payroll-approvals/${run.id}`}>Open approval detail</Link>
          </Button>
          <Button asChild size="sm" variant="ghost">
            <Link to="/finance/payroll-approvals">Quay lại approvals</Link>
          </Button>
        </div>
      }
    >
      <ReadonlyBanner message={message} />

      <EntityHeader
        eyebrow="Finance / Payroll Paid"
        metadata={
          <>
            <span>Period #{run.payrollPeriodId}</span>
            <span>Run date: {formatFinanceDateLabel(run.runDate)}</span>
            <span>Approved at: {formatFinanceDateLabel(run.approvedAt)}</span>
            <span>Paid at: {formatFinanceDateLabel(run.paidAt)}</span>
          </>
        }
        status={<StatusBadge status={run.status} />}
        title={run.runCode}
      />

      <section className="surface-panel command-stage" aria-label="Payroll paid command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Settlement workspace</span>
            <span className={isTerminal ? 'meta-chip-success' : 'meta-chip'}>
              {isTerminal ? 'Terminal record' : 'Awaiting settlement'}
            </span>
            <span className="meta-chip">Employees {summary.employeeCount}</span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Finance / Payroll Paid</p>
            <strong className="action-summary-title">Current settlement posture</strong>
            <p className="muted-text">
              Confirm the payout status, reference completeness, and payroll visibility before
              finalizing the run as paid or reviewing the locked audit trail.
            </p>
          </div>
          <div className="meta-grid">
            <span>Run date: {formatFinanceDateLabel(run.runDate)}</span>
            <span>Submitted at: {formatFinanceDateLabel(run.submittedAt)}</span>
            <span>Approved at: {formatFinanceDateLabel(run.approvedAt)}</span>
            <span>Payment reference: {run.paymentRef ?? 'Not paid yet'}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Settlement checks</span>
            <strong>Payment handoff</strong>
            <p>
              Keep lifecycle state, payment reference, and payroll detail visibility in view before
              confirming the terminal mark-paid action.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Payment state</strong>
                <span className="muted-text">Current lifecycle status for this payroll run.</span>
              </div>
              <div className="command-support-stack">
                <StatusBadge status={run.status} />
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Settlement reference</strong>
                <span className="muted-text">Reference saved for the audit trail and payout proof.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{run.paymentRef ?? 'Pending'}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Detail visibility</strong>
                <span className="muted-text">
                  {canReadDetail ? 'Aggregated payroll values are visible in this scope.' : 'Payroll values remain masked in the current scope.'}
                </span>
              </div>
              <div className="command-support-stack">
                <span className={canReadDetail ? 'meta-chip-success' : 'meta-chip'}>
                  {canReadDetail ? 'Visible' : 'Masked'}
                </span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Payroll paid summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="payments" />
            </span>
            <span className="workspace-stat-badge">Run total</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Payroll amount</span>
            <strong className="workspace-stat-value">
              {canReadDetail ? formatFinanceCurrency(summary.totalAmount) : 'Masked'}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="account_balance_wallet" />
            </span>
            <span className={canReadDetail ? 'workspace-stat-badge success' : 'workspace-stat-badge warning'}>
              Net pay
            </span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Aggregated net pay</span>
            <strong className="workspace-stat-value">
              {canReadDetail ? formatFinanceCurrency(summary.totalNetPay) : 'Masked'}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="groups" />
            </span>
            <span className="workspace-stat-badge">Employees</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Employee result count</span>
            <strong className="workspace-stat-value">{summary.employeeCount}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="warning" />
            </span>
            <span className="workspace-stat-badge warning">Exceptions</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Exception load</span>
            <strong className="workspace-stat-value">{summary.exceptionCount}</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
          <FormSection description="Summary payroll signals stay visible here before the payout is finalized." title="Settlement summary">
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
                    ? 'Aggregated net pay across current payroll run.'
                    : 'Masked because employee-level payroll detail is restricted.'
                }
                label="Aggregated net pay"
                mode={canReadDetail ? 'readonly-visible' : 'masked'}
                value={formatFinanceCurrency(summary.totalNetPay)}
              />
              <MaskedField label="Employee result count" mode="readonly-visible" value={summary.employeeCount.toString()} />
              <MaskedField label="Exceptions" mode="readonly-visible" value={summary.exceptionCount.toString()} />
            </div>
          </FormSection>

          <FormSection description="Trường này chỉ actionable khi payroll run đã APPROVED và principal có finance.payroll.pay." title="Mark paid workflow">
            {canMarkPaid ? (
              <>
                <div className="field-grid">
                  <Input
                    label="Payment reference"
                    onChange={(event) => setPaymentReference(event.target.value)}
                    placeholder="VD: PAY-202603-001"
                    value={paymentReference}
                  />
                  <Textarea
                    label="Payment note"
                    onChange={(event) => setNote(event.target.value)}
                    placeholder="Optional note for audit trail"
                    value={note}
                  />
                </div>
                <FormActions
                  primaryAction={
                    <Button loading={markPaidMutation.isPending} onClick={() => setDialogOpen(true)} size="sm">
                      Mark payroll as paid
                    </Button>
                  }
                  secondaryAction={
                    <Button asChild size="sm" variant="secondary">
                      <Link to={`/finance/payroll-approvals/${run.id}`}>Back to approval detail</Link>
                    </Button>
                  }
                />
              </>
            ) : isTerminal ? (
              <PermissionDeniedInline
                message="Run này đã ở trạng thái terminal. Payment metadata ở bên cạnh là nguồn sự thật hiện tại."
                title="Terminal readonly state"
              />
            ) : (
              <PermissionDeniedInline message="Run này chưa ở trạng thái APPROVED hoặc bạn thiếu finance.payroll.pay, nên mark-paid workflow đang bị khóa." />
            )}
            {validationError ? <p className="error-text">{validationError}</p> : null}
          </FormSection>
        </div>

        <aside className="surface-grid-side">
          <AuditMetaBlock
            items={[
              { label: 'Submitted at', value: formatFinanceDateLabel(run.submittedAt) },
              { label: 'Approved at', value: formatFinanceDateLabel(run.approvedAt) },
              { label: 'Paid at', value: formatFinanceDateLabel(run.paidAt) },
              { label: 'Payment reference', value: run.paymentRef ?? 'Not paid yet' },
              { label: 'Note', value: run.note ?? 'No note' },
            ]}
            title="Payment audit metadata"
          />
        </aside>
      </div>

      <ConfirmActionDialog
        confirmLabel="Mark as paid"
        description="Xác nhận mark payroll run này là PAID. Payment reference sẽ được lưu cho audit trail và terminal state."
        onCancel={() => setDialogOpen(false)}
        onConfirm={() => void handleConfirmPaid()}
        open={dialogOpen}
        title="Mark payroll as paid"
      />
    </DashboardLayout>
  )
}
