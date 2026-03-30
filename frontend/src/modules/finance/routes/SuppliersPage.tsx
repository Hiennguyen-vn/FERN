import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Card,
  DataTable,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useFinanceSuppliers } from '../hooks/useFinance'
import type { FinanceSupplier } from '../model/finance.types'
import { getFinanceErrorMessage } from '../services/financeError.service'
import { formatFinanceDateLabel, matchesFinanceSearch } from '../services/financeWorkflow.service'
import { supplierUiPolicy } from '../services/supplierUiPolicy.service'

export function SuppliersPage() {
  usePageTitle('Finance Suppliers')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { selectedRegionId } = useScopeContext()
  const canOpen = supplierUiPolicy.canOpenSuppliersPage(principal)
  const { data: suppliers = [], error, isLoading, refetch } = useFinanceSuppliers({ enabled: canOpen })
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [regionFilter, setRegionFilter] = useState(selectedRegionId ? String(selectedRegionId) : 'ALL')

  const statusOptions = useMemo<SelectOption[]>(() => {
    const statuses = Array.from(new Set(suppliers.map((supplier) => supplier.status).filter(Boolean))).sort()
    return [
      { label: 'Tất cả trạng thái', value: 'ALL' },
      ...statuses.map((status) => ({ label: status, value: status })),
    ]
  }, [suppliers])

  const regionOptions = useMemo<SelectOption[]>(() => {
    const regionIds = Array.from(
      new Set(
        suppliers
          .map((supplier) => supplier.defaultRegionId)
          .filter((regionId): regionId is number => Number.isFinite(regionId)),
      ),
    ).sort((left, right) => left - right)

    return [
      { label: 'Tất cả region', value: 'ALL' },
      ...regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    ]
  }, [suppliers])

  const filteredSuppliers = useMemo(() => {
    return suppliers.filter((supplier) => {
      const matchesStatus = statusFilter === 'ALL' || supplier.status === statusFilter
      const matchesRegion =
        regionFilter === 'ALL' || String(supplier.defaultRegionId ?? '') === regionFilter
      const matchesQuery = matchesFinanceSearch(
        [supplier.id, supplier.supplierCode, supplier.name, supplier.taxCode, supplier.email, supplier.phone],
        search,
      )

      return matchesStatus && matchesRegion && matchesQuery
    })
  }, [regionFilter, search, statusFilter, suppliers])

  const columns = useMemo<Array<DataTableColumn<FinanceSupplier>>>(
    () => [
      {
        key: 'supplier',
        header: 'Supplier',
        render: (supplier) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{supplier.name}</strong>
            <span className="muted-text">{supplier.supplierCode}</span>
          </div>
        ),
      },
      {
        key: 'contact',
        header: 'Liên hệ',
        render: (supplier) => supplier.email ?? supplier.phone ?? 'No contact info',
      },
      {
        key: 'region',
        header: 'Default region',
        render: (supplier) => (supplier.defaultRegionId ? `#${supplier.defaultRegionId}` : 'No default region'),
      },
      {
        key: 'approvedAt',
        header: 'Approved at',
        render: (supplier) => formatFinanceDateLabel(supplier.approvedAt),
      },
      {
        key: 'status',
        header: 'Status',
        render: (supplier) => <StatusBadge status={supplier.status} />,
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Suppliers" description="Read-first supplier master browse cho Finance">
        <PermissionDeniedInline message="Bạn cần quyền procurement.supplier.read để mở danh sách nhà cung cấp." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Nhà cung cấp"
      description="Dense supplier browse phục vụ payment review, payroll-linked finance operations và procurement coordination."
    >
      <ReadonlyBanner message="Supplier master đang publish ở chế độ read-first trong Finance. Trang này ưu tiên browse/detail thay vì edit." />

      <Card title="Bộ lọc nhà cung cấp">
        <div className="field-grid">
          <Input
            label="Tìm theo mã, tên hoặc liên hệ"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="VD: SUP-001 hoặc Lotus Foods"
            value={search}
          />
          <Select
            label="Trạng thái"
            onChange={(event) => setStatusFilter(event.target.value)}
            options={statusOptions}
            value={statusFilter}
          />
          <Select
            label="Region"
            onChange={(event) => setRegionFilter(event.target.value)}
            options={regionOptions}
            value={regionFilter}
          />
        </div>
        <div className="meta-grid">
          <span>Total suppliers: {suppliers.length}</span>
          <span>Filtered rows: {filteredSuppliers.length}</span>
          <span>Mode: Finance read-first supplier console</span>
        </div>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription={
          suppliers.length === 0
            ? 'Chưa có supplier nào trong phạm vi hiện tại.'
            : 'Không có supplier nào khớp bộ lọc hiện tại.'
        }
        emptyTitle={suppliers.length === 0 ? 'No suppliers' : 'No matching suppliers'}
        error={error ? getFinanceErrorMessage(error, 'Không thể tải supplier master data.') : null}
        loading={isLoading}
        loadingDescription="Đang tải supplier master data..."
        loadingTitle="Đang tải nhà cung cấp"
        onRetry={() => void refetch()}
        onRowClick={(supplier) => navigate(`/finance/suppliers/${supplier.id}`)}
        rowKey={(supplier) => supplier.id}
        rows={filteredSuppliers}
      />
    </DashboardLayout>
  )
}
