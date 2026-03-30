import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
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
    </DashboardLayout>
  )
}
