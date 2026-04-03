import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  DataTable,
  Input,
  MaskedField,
  PermissionDeniedInline,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrContractBrowse } from '../hooks/useHr'
import type { HrContract } from '../model/hr.types'
import { contractUiPolicy } from '../services/contractUiPolicy.service'
import { getHrErrorMessage } from '../services/hrError.service'
import { canWriteContracts } from '../services/hrPermission.service'
import { formatCurrencyAmount, formatDateRange } from '../services/hrReadModel.service'

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'DRAFT', value: 'DRAFT' },
  { label: 'EXPIRED', value: 'EXPIRED' },
  { label: 'TERMINATED', value: 'TERMINATED' },
]

const PAGE_SIZE = 50

export function ContractsPage() {
  usePageTitle('HR Contracts')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const canReadContracts = contractUiPolicy.canOpenContractsPage(principal)
  const canCreateContracts = canWriteContracts(principal)
  const canViewSalary = contractUiPolicy.canViewSensitiveFields(principal)
  const [employeeFilter, setEmployeeFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [searchText, setSearchText] = useState('')
  const [page, setPage] = useState(0)
  const parsedEmployeeId = Number(employeeFilter)
  const contractsQuery = useHrContractBrowse(
    {
      employeeId: Number.isInteger(parsedEmployeeId) && parsedEmployeeId > 0 ? parsedEmployeeId : undefined,
      search: searchText.trim() || undefined,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      page,
      size: PAGE_SIZE,
    },
    { enabled: canReadContracts },
  )

  const hasMore = contractsQuery.data?.hasMore ?? false

  const columns = useMemo<Array<DataTableColumn<HrContract>>>(
    () => [
      {
        key: 'contract',
        header: 'Contract',
        render: (contract) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>#{contract.id}</strong>
            <span className="muted-text">{contract.employmentType}</span>
          </div>
        ),
      },
      {
        key: 'employeeId',
        header: 'Employee',
        render: (contract) => `#${contract.employeeId}`,
      },
      {
        key: 'status',
        header: 'Status',
        render: (contract) => <StatusBadge status={contract.contractStatus} />,
      },
      {
        key: 'effective',
        header: 'Effective dates',
        render: (contract) => formatDateRange(contract.startDate, contract.endDate),
      },
      {
        key: 'baseSalary',
        header: canViewSalary ? 'Base salary' : 'Base salary (masked)',
        render: (contract) => (
          <MaskedField
            label=""
            mode={canViewSalary ? 'readonly-visible' : 'masked'}
            value={formatCurrencyAmount(contract.baseSalary)}
          />
        ),
      },
      {
        key: 'taxCode',
        header: canViewSalary ? 'Tax code' : 'Tax code (masked)',
        render: (contract) => (
          <MaskedField
            label=""
            mode={canViewSalary ? 'readonly-visible' : 'masked'}
            value={contract.taxCode ?? '—'}
          />
        ),
      },
    ],
    [canViewSalary],
  )

  if (!canReadContracts) {
    return (
      <DashboardLayout title="HR Contracts" description="Contract browse cho module HR">
        <PermissionDeniedInline message="Bạn cần quyền hr.contract.read để xem danh sách hợp đồng." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Hợp đồng"
      description="Contract browse workspace cho HR và scope-aware contract review."
      actions={
        canCreateContracts ? (
          <Button asChild size="sm">
            <Link to="/hr/contracts/new">Create contract</Link>
          </Button>
        ) : null
      }
    >
      <div className="field-grid">
        <Input
          label="Employee ID"
          onChange={(event) => { setEmployeeFilter(event.target.value); setPage(0) }}
          placeholder="Lọc theo employee ID"
          type="number"
          value={employeeFilter}
        />
        <Input
          label="Search contracts"
          onChange={(event) => { setSearchText(event.target.value); setPage(0) }}
          placeholder="Contract ID, employee, type..."
          value={searchText}
        />
        <Select
          label="Status filter"
          onChange={(event) => { setStatusFilter(event.target.value); setPage(0) }}
          options={statusOptions}
          value={statusFilter}
        />
      </div>

      <DataTable
        canNext={hasMore}
        canPrevious={page > 0}
        columns={columns}
        currentPage={page}
        emptyDescription="Không có contract nào khớp bộ lọc hiện tại."
        emptyTitle="No matching contracts"
        error={contractsQuery.error ? getHrErrorMessage(contractsQuery.error, 'Không thể tải danh sách contracts.') : null}
        loading={contractsQuery.isLoading}
        loadingDescription="Đang tải contracts..."
        loadingTitle="Đang tải contracts"
        onNext={() => setPage((p) => p + 1)}
        onPrevious={() => setPage((p) => Math.max(0, p - 1))}
        onRetry={() => void contractsQuery.refetch()}
        onRowClick={(contract) => navigate(`/hr/contracts/${contract.id}?employeeId=${contract.employeeId}`)}
        rowKey={(contract) => contract.id}
        rows={contractsQuery.data?.items ?? []}
      />
    </DashboardLayout>
  )
}
