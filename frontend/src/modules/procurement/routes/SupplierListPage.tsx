import { Link } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  DataTable,
  ErrorState,
  PermissionDeniedInline,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useSuppliers } from '../hooks/useSuppliers'
import type { Supplier } from '../model/procurement.types'
import { canReadSuppliers, canWriteSuppliers } from '../services/procurementPermission.service'

export function SupplierListPage() {
  usePageTitle('Suppliers')
  const principal = usePrincipal()
  const canRead = canReadSuppliers(principal)
  const canWrite = canWriteSuppliers(principal)
  const query = useSuppliers({ enabled: canRead })

  const columns: Array<DataTableColumn<Supplier>> = [
    { key: 'supplierCode', header: 'Code', render: (row) => row.supplierCode },
    {
      key: 'name',
      header: 'Name',
      render: (row) => (
        <Link className="table-link" to={`/procurement/suppliers/${row.id}`}>
          {row.name}
        </Link>
      ),
    },
    { key: 'email', header: 'Email', render: (row) => row.email ?? '—' },
    { key: 'phone', header: 'Phone', render: (row) => row.phone ?? '—' },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  return (
    <DashboardLayout
      actions={
        canWrite ? (
          <Button asChild size="sm">
            <Link to="/procurement/suppliers/new">Add supplier</Link>
          </Button>
        ) : null
      }
      description="Quản lý danh sách nhà cung cấp và thông tin liên hệ."
      eyebrow="Procurement"
      title="Suppliers"
    >
      {!canRead ? (
        <PermissionDeniedInline message="You need procurement.supplier.read to view suppliers." />
      ) : query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load suppliers'}
          onAction={() => void query.refetch()}
          title="Unable to load suppliers"
        />
      ) : (
        <DataTable
          columns={columns}
          emptyDescription="No suppliers have been created yet."
          emptyTitle="No suppliers"
          loading={query.isLoading}
          loadingTitle="Loading suppliers"
          rowKey={(row) => row.id}
          rows={query.data ?? []}
        />
      )}
    </DashboardLayout>
  )
}
