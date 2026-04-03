import { Link, useParams } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useFinanceSupplier } from '../hooks/useFinance'
import { getFinanceErrorMessage } from '../services/financeError.service'
import { formatFinanceDateLabel } from '../services/financeWorkflow.service'
import { supplierUiPolicy } from '../services/supplierUiPolicy.service'

export function SupplierDetailPage() {
  const { supplierId: supplierIdParam } = useParams<{ supplierId: string }>()
  const supplierId = Number(supplierIdParam)
  const principal = usePrincipal()
  const canOpen = supplierUiPolicy.canOpenSupplierDetail(principal)
  const canOpenPaymentRequests = supplierUiPolicy.canOpenSupplierLinkedPaymentRequests(principal)
  const supplierQuery = useFinanceSupplier(supplierId, {
    enabled: canOpen && Number.isFinite(supplierId),
  })

  usePageTitle(supplierQuery.data ? `${supplierQuery.data.name} — Finance` : 'Supplier Detail — Finance')

  if (!canOpen) {
    return (
      <DashboardLayout title="Chi tiết nhà cung cấp" description="Inspect supplier master data từ góc nhìn Finance">
        <PermissionDeniedInline message="Bạn cần quyền procurement.supplier.read để mở supplier detail." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(supplierId) || supplierId <= 0) {
    return (
      <DashboardLayout
        title="Chi tiết nhà cung cấp"
        description="Inspect supplier master data từ góc nhìn Finance"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/suppliers">Quay lại suppliers</Link>
          </Button>
        }
      >
        <EmptyState description="URL không chứa supplierId hợp lệ." title="Thiếu supplierId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (supplierQuery.isLoading) {
    return (
      <DashboardLayout title="Chi tiết nhà cung cấp" description="Inspect supplier master data từ góc nhìn Finance">
        <EmptyState description="Đang tải supplier master data từ list endpoint..." title="Đang tải supplier detail" />
      </DashboardLayout>
    )
  }

  if (supplierQuery.error) {
    return (
      <DashboardLayout
        title="Chi tiết nhà cung cấp"
        description="Inspect supplier master data từ góc nhìn Finance"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/suppliers">Quay lại suppliers</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Tải lại"
          message={getFinanceErrorMessage(supplierQuery.error, 'Không thể tải supplier detail.')}
          onAction={() => void supplierQuery.refetch()}
          title="Không thể tải nhà cung cấp"
        />
      </DashboardLayout>
    )
  }

  if (!supplierQuery.data) {
    return (
      <DashboardLayout
        title="Chi tiết nhà cung cấp"
        description="Inspect supplier master data từ góc nhìn Finance"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/suppliers">Quay lại suppliers</Link>
          </Button>
        }
      >
        <EmptyState
          description="Supplier này không tồn tại trong list endpoint hoặc nằm ngoài phạm vi hiện tại."
          title="Không tìm thấy nhà cung cấp"
        />
      </DashboardLayout>
    )
  }

  const supplier = supplierQuery.data

  return (
    <DashboardLayout
      title="Chi tiết nhà cung cấp"
      description="Read-first supplier detail cho workflow kiểm tra payment requests và kiểm soát payables."
      actions={
        <div className="form-actions align-start">
          <Button asChild size="sm" variant="secondary">
            <Link to="/finance/suppliers">Quay lại suppliers</Link>
          </Button>
          {canOpenPaymentRequests ? (
            <Button asChild size="sm" variant="ghost">
              <Link to="/finance/payment-requests">Open payment requests</Link>
            </Button>
          ) : null}
        </div>
      }
    >
      <ReadonlyBanner message="Supplier detail đang dùng supplier list endpoint làm nguồn sự thật. Chưa publish edit workflow trong bước này." />

      <EntityHeader
        eyebrow="Finance / Supplier"
        metadata={
          <>
            <span>Supplier code: {supplier.supplierCode}</span>
            <span>Default region: {supplier.defaultRegionId ? `#${supplier.defaultRegionId}` : 'Unassigned'}</span>
            <span>Approved at: {formatFinanceDateLabel(supplier.approvedAt)}</span>
          </>
        }
        status={<StatusBadge status={supplier.status} />}
        title={supplier.name}
      />

      <section className="surface-panel command-stage" aria-label="Supplier detail command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Read-first finance record</span>
            <span className="meta-chip">
              Region {supplier.defaultRegionId ? `#${supplier.defaultRegionId}` : 'Unassigned'}
            </span>
            <span className={supplier.approvedAt ? 'meta-chip-success' : 'meta-chip'}>
              {supplier.approvedAt ? 'Approved supplier' : 'Approval pending'}
            </span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Finance / Supplier Detail</p>
            <strong className="action-summary-title">Current payables profile</strong>
            <p className="muted-text">
              Use this record to confirm supplier readiness, contact reachability, and whether the
              payment request console should be opened for follow-up.
            </p>
          </div>
          <div className="meta-grid">
            <span>Supplier code: {supplier.supplierCode}</span>
            <span>Contact points: {[supplier.email, supplier.phone].filter(Boolean).length}</span>
            <span>Default region: {supplier.defaultRegionId ? `#${supplier.defaultRegionId}` : 'Unassigned'}</span>
            <span>Approved at: {formatFinanceDateLabel(supplier.approvedAt)}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Readiness checks</span>
            <strong>Finance handoff</strong>
            <p>
              Validate the minimum payables signals here before moving into invoice review or payment
              scheduling.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Approval lifecycle</strong>
                <span className="muted-text">
                  {supplier.approvedAt ? 'Supplier has completed approval and is ready for payables review.' : 'Supplier has not been approved yet.'}
                </span>
              </div>
              <div className="command-support-stack">
                <StatusBadge status={supplier.status} />
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Contact reachability</strong>
                <span className="muted-text">
                  {supplier.email ?? 'No email'} · {supplier.phone ?? 'No phone'}
                </span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">
                  {[supplier.email, supplier.phone].filter(Boolean).length}/2
                </span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Payment request access</strong>
                <span className="muted-text">
                  {canOpenPaymentRequests ? 'Console is available from this finance scope.' : 'Permission is required to open linked payment requests.'}
                </span>
              </div>
              <div className="command-support-stack">
                <span className={canOpenPaymentRequests ? 'meta-chip-success' : 'meta-chip'}>
                  {canOpenPaymentRequests ? 'Available' : 'Restricted'}
                </span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Supplier detail summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="inventory_2" />
            </span>
            <span className="workspace-stat-badge">Identity</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Supplier record</span>
            <strong className="workspace-stat-value">#{supplier.id}</strong>
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
              {[supplier.email, supplier.phone].filter(Boolean).length}
            </strong>
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
            <span className="workspace-stat-label">Default region</span>
            <strong className="workspace-stat-value">
              {supplier.defaultRegionId ? `#${supplier.defaultRegionId}` : 'None'}
            </strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="task_alt" />
            </span>
            <span className={supplier.approvedAt ? 'workspace-stat-badge success' : 'workspace-stat-badge warning'}>
              Lifecycle
            </span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Approval signal</span>
            <strong className="workspace-stat-value">
              {supplier.approvedAt ? 'Ready' : 'Pending'}
            </strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
          <FormSection description="Thông tin nhận diện và liên hệ của supplier trong supplier master." title="Supplier overview">
            <div className="meta-grid">
              <span>Tax code: {supplier.taxCode ?? 'No tax code'}</span>
              <span>Email: {supplier.email ?? 'No email'}</span>
              <span>Phone: {supplier.phone ?? 'No phone'}</span>
              <span>Address: {supplier.address ?? 'No address'}</span>
            </div>
          </FormSection>

          <FormSection description="Finance sử dụng phần này để xác nhận readiness trước khi review invoice/payment requests." title="Payables context">
            <div className="meta-grid">
              <span>Status: {supplier.status}</span>
              <span>Payment request console: {canOpenPaymentRequests ? 'Available' : 'Permission required'}</span>
              <span>Supplier detail mode: Read-only</span>
            </div>
          </FormSection>
        </div>

        <aside className="surface-grid-side">
          <section className="surface-panel command-table-stack">
            <div className="state-panel-heading">
              <span className="eyebrow">Management actions</span>
              <h2 className="card-title">Linked workflows</h2>
              <p className="muted-text">
                Jump to the next finance surface from the current supplier record.
              </p>
            </div>
            <div className="command-support-list">
              <article className="command-support-item">
                <div className="command-support-copy">
                  <strong>Supplier master</strong>
                  <span className="muted-text">Return to the supplier list and continue browsing.</span>
                </div>
                <div className="command-support-stack">
                  <span className="meta-chip">Back list</span>
                </div>
              </article>
              <article className="command-support-item">
                <div className="command-support-copy">
                  <strong>Payment requests</strong>
                  <span className="muted-text">
                    {canOpenPaymentRequests ? 'Open the linked payables queue from finance.' : 'Queue access is not available in the current scope.'}
                  </span>
                </div>
                <div className="command-support-stack">
                  <span className={canOpenPaymentRequests ? 'meta-chip-success' : 'meta-chip'}>
                    {canOpenPaymentRequests ? 'Openable' : 'Restricted'}
                  </span>
                </div>
              </article>
            </div>
          </section>
        </aside>
      </div>
    </DashboardLayout>
  )
}
