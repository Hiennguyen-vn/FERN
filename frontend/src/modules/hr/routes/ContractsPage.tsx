import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrContracts, useHrEmployee } from '../hooks/useHr'
import type { HrContract, RecentHrContractLookup } from '../model/hr.types'
import { contractUiPolicy } from '../services/contractUiPolicy.service'
import { employeeUiPolicy } from '../services/employeeUiPolicy.service'
import { getHrErrorMessage } from '../services/hrError.service'
import {
  formatDateRange,
  matchesSearch,
} from '../services/hrReadModel.service'
import {
  clearRecentHrContracts,
  loadRecentHrContracts,
  saveRecentHrContract,
} from '../services/recentHrLookups.service'

const statusOptions: SelectOption[] = [
  { label: 'Tất cả trạng thái', value: 'ALL' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'DRAFT', value: 'DRAFT' },
  { label: 'EXPIRED', value: 'EXPIRED' },
  { label: 'TERMINATED', value: 'TERMINATED' },
]

function matchesContractSearch(item: RecentHrContractLookup, search: string) {
  return matchesSearch(
    [
      item.contract.id,
      item.employeeId,
      item.contract.employmentType,
      item.contract.salaryType,
      item.employeeCode,
      item.employeeName,
    ],
    search,
  )
}

function toContractLookupRow(contract: HrContract, context: { employeeCode?: string | null; employeeName?: string | null }) {
  return {
    contract,
    employeeCode: context.employeeCode,
    employeeId: contract.employeeId,
    employeeName: context.employeeName,
  }
}

export function ContractsPage() {
  usePageTitle('HR Contracts')
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const principal = usePrincipal()
  const canReadContracts = contractUiPolicy.canOpenContractsPage(principal)
  const canReadEmployees = employeeUiPolicy.canOpenEmployeesPage(principal)
  const searchParamEmployeeId = Number(searchParams.get('employeeId'))
  const initialEmployeeId =
    Number.isInteger(searchParamEmployeeId) && searchParamEmployeeId > 0 ? searchParamEmployeeId : null

  const [lookupInput, setLookupInput] = useState(initialEmployeeId ? String(initialEmployeeId) : '')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [searchText, setSearchText] = useState('')
  const [recentContracts, setRecentContracts] = useState<RecentHrContractLookup[]>(() => loadRecentHrContracts())

  const lookedUpEmployeeId = initialEmployeeId ?? 0
  const employeeQuery = useHrEmployee(lookedUpEmployeeId, {
    enabled: canReadEmployees && lookedUpEmployeeId > 0,
  })
  const contractsQuery = useHrContracts(lookedUpEmployeeId, {
    enabled: canReadContracts && lookedUpEmployeeId > 0,
  })

  useEffect(() => {
    if (!contractsQuery.data || lookedUpEmployeeId <= 0) {
      return
    }

    contractsQuery.data.forEach((contract) => {
      saveRecentHrContract(contract, {
        employeeCode: employeeQuery.data?.employeeCode,
        employeeName: employeeQuery.data?.fullName,
      })
    })
    setRecentContracts(loadRecentHrContracts())
  }, [contractsQuery.data, employeeQuery.data, lookedUpEmployeeId])

  const currentContracts = useMemo(() => {
    return (contractsQuery.data ?? []).filter((contract) => {
      const matchesStatus = statusFilter === 'ALL' || contract.contractStatus === statusFilter
      return matchesStatus && matchesSearch([contract.id, contract.employmentType, contract.salaryType], searchText)
    })
  }, [contractsQuery.data, searchText, statusFilter])

  const filteredRecentContracts = useMemo(() => {
    return recentContracts.filter((item) => {
      const matchesStatus = statusFilter === 'ALL' || item.contract.contractStatus === statusFilter
      return matchesStatus && matchesContractSearch(item, searchText)
    })
  }, [recentContracts, searchText, statusFilter])

  const visibleRecentContracts = useMemo(() => {
    const currentContractIds = new Set(currentContracts.map((contract) => contract.id))
    return filteredRecentContracts.filter((item) => !currentContractIds.has(item.contract.id))
  }, [currentContracts, filteredRecentContracts])

  const columns = useMemo<Array<DataTableColumn<RecentHrContractLookup>>>(
    () => [
      {
        key: 'contract',
        header: 'Contract',
        render: (row) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>#{row.contract.id}</strong>
            <span className="muted-text">{row.contract.employmentType}</span>
          </div>
        ),
      },
      {
        key: 'employee',
        header: 'Employee',
        render: (row) => row.employeeCode && row.employeeName ? `${row.employeeCode} · ${row.employeeName}` : `#${row.employeeId}`,
      },
      {
        key: 'status',
        header: 'Status',
        render: (row) => <StatusBadge status={row.contract.contractStatus} />,
      },
      {
        key: 'effective',
        header: 'Effective dates',
        render: (row) => formatDateRange(row.contract.startDate, row.contract.endDate),
      },
    ],
    [],
  )

  function submitLookup() {
    const parsed = Number(lookupInput)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập employee ID hợp lệ để tải contracts.')
      return
    }

    setValidationError(null)
    setSearchParams({ employeeId: String(parsed) })
  }

  if (!canReadContracts) {
    return (
      <DashboardLayout title="HR Contracts" description="Employee-scoped contract browse cho module HR">
        <PermissionDeniedInline message="Bạn cần quyền hr.contract.read để xem danh sách hợp đồng." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Hợp đồng"
      description="Employee-scoped contract workspace vì backend hiện không publish public contract list endpoint."
    >
      <ReadonlyBanner message="Contracts đang publish ở chế độ read-first. Lookup theo employee ID để xem hợp đồng trong phạm vi hiện tại." />

      <Card title="Load contracts by employee">
        <div className="field-grid">
          <Input
            label="Employee ID"
            onChange={(event) => setLookupInput(event.target.value)}
            placeholder="VD: 1001"
            type="number"
            value={lookupInput}
          />
          <Input
            label="Tìm trong current/recent contracts"
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Contract ID, employee, type..."
            value={searchText}
          />
          <Select
            label="Status filter"
            onChange={(event) => setStatusFilter(event.target.value)}
            options={statusOptions}
            value={statusFilter}
          />
        </div>
        <div className="form-actions align-start">
          <Button onClick={submitLookup} size="sm">
            Load employee contracts
          </Button>
          {recentContracts.length > 0 ? (
            <Button
              onClick={() => {
                clearRecentHrContracts()
                setRecentContracts([])
              }}
              size="sm"
              variant="ghost"
            >
              Clear recent
            </Button>
          ) : null}
        </div>
        {validationError ? <p className="error-text">{validationError}</p> : null}
      </Card>

      {lookedUpEmployeeId > 0 ? (
        contractsQuery.isLoading ? (
          <Card title="Đang tải contracts">
            <p className="muted-text">Đang tải hợp đồng cho employee #{lookedUpEmployeeId}...</p>
          </Card>
        ) : contractsQuery.error ? (
          <ErrorState
            actionLabel="Retry lookup"
            message={getHrErrorMessage(contractsQuery.error, `Không thể tải contracts của employee #${lookedUpEmployeeId}.`)}
            onAction={() => void contractsQuery.refetch()}
            title="Không thể tải contracts"
          />
        ) : (
          <Card title="Current employee contract set">
            <p className="muted-text">
              {employeeQuery.data ? `Employee: ${employeeQuery.data.employeeCode} · ${employeeQuery.data.fullName}` : `Employee #${lookedUpEmployeeId}`}
            </p>
            <DataTable
              columns={columns}
              emptyDescription="Nhân viên này chưa có hợp đồng nào trong phạm vi hiện tại."
              emptyTitle="No contracts for employee"
              onRowClick={(row) => navigate(`/hr/contracts/${row.contract.id}?employeeId=${row.employeeId}`)}
              rowKey={(row) => row.contract.id}
              rows={currentContracts.map((contract) =>
                toContractLookupRow(contract, {
                  employeeCode: employeeQuery.data?.employeeCode,
                  employeeName: employeeQuery.data?.fullName,
                }),
              )}
            />
          </Card>
        )
      ) : null}

      {lookedUpEmployeeId <= 0 && recentContracts.length === 0 ? (
        <EmptyState
          description="Chưa có contract nào được inspect gần đây. Lookup theo employee ID để xây recent history."
          title="No recent contracts yet"
        />
      ) : visibleRecentContracts.length > 0 ? (
        <DataTable
          columns={columns}
          emptyDescription="Không có recent contract nào khớp bộ lọc hiện tại."
          emptyTitle="No matching recent contracts"
          onRowClick={(row) => navigate(`/hr/contracts/${row.contract.id}?employeeId=${row.employeeId}`)}
          rowKey={(row) => row.contract.id}
          rows={visibleRecentContracts}
        />
      ) : lookedUpEmployeeId <= 0 ? (
        <EmptyState
          description="Recent contract history không có bản ghi nào khớp bộ lọc hiện tại."
          title="No matching recent contracts"
        />
      ) : null}
    </DashboardLayout>
  )
}
