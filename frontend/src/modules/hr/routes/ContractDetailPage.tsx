import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import {
  Button,
  EmptyState,
  EntityHeader,
  ErrorState,
  FormSection,
  Input,
  MaskedField,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useHrContracts, useHrEmployee } from '../hooks/useHr'
import { contractUiPolicy } from '../services/contractUiPolicy.service'
import { employeeUiPolicy } from '../services/employeeUiPolicy.service'
import { getHrErrorMessage } from '../services/hrError.service'
import {
  formatCurrencyAmount,
  formatDateRange,
} from '../services/hrReadModel.service'
import {
  findRecentHrContract,
  saveRecentHrContract,
} from '../services/recentHrLookups.service'

export function ContractDetailPage() {
  const navigate = useNavigate()
  const { contractId: contractIdParam } = useParams<{ contractId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const contractId = Number(contractIdParam)
  const principal = usePrincipal()
  const canReadContracts = contractUiPolicy.canOpenContractsPage(principal)
  const canReadEmployees = employeeUiPolicy.canOpenEmployeeDetail(principal)
  const canViewSensitiveFields = contractUiPolicy.canViewSensitiveFields(principal)

  const recentLookup = useMemo(
    () => (Number.isFinite(contractId) && contractId > 0 ? findRecentHrContract(contractId) : null),
    [contractId],
  )

  const searchParamEmployeeId = Number(searchParams.get('employeeId'))
  const resolvedEmployeeId =
    Number.isInteger(searchParamEmployeeId) && searchParamEmployeeId > 0
      ? searchParamEmployeeId
      : recentLookup?.employeeId ?? 0

  const [lookupEmployeeId, setLookupEmployeeId] = useState(
    resolvedEmployeeId > 0 ? String(resolvedEmployeeId) : '',
  )
  const [validationError, setValidationError] = useState<string | null>(null)

  const employeeQuery = useHrEmployee(resolvedEmployeeId, {
    enabled: canReadEmployees && resolvedEmployeeId > 0,
  })
  const contractsQuery = useHrContracts(resolvedEmployeeId, {
    enabled: canReadContracts && resolvedEmployeeId > 0,
  })

  const contract = useMemo(() => {
    if (contractsQuery.data) {
      return contractsQuery.data.find((item) => item.id === contractId) ?? null
    }

    if (recentLookup?.employeeId === resolvedEmployeeId) {
      return recentLookup.contract
    }

    return null
  }, [contractId, contractsQuery.data, recentLookup, resolvedEmployeeId])

  useEffect(() => {
    if (!contract) {
      return
    }

    saveRecentHrContract(contract, {
      employeeCode: employeeQuery.data?.employeeCode ?? recentLookup?.employeeCode,
      employeeName: employeeQuery.data?.fullName ?? recentLookup?.employeeName,
    })
  }, [contract, employeeQuery.data, recentLookup])

  usePageTitle(contract ? `Contract #${contract.id} — HR` : 'Chi tiết hợp đồng — HR')

  function submitLookup() {
    const parsed = Number(lookupEmployeeId)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập employee ID hợp lệ để resolve contract detail.')
      return
    }

    setValidationError(null)
    setSearchParams({ employeeId: String(parsed) })
  }

  if (!canReadContracts) {
    return (
      <DashboardLayout title="Chi tiết hợp đồng" description="Inspect hợp đồng nhân viên và dữ liệu nhạy cảm liên quan">
        <PermissionDeniedInline message="Bạn cần quyền hr.contract.read để xem chi tiết hợp đồng." />
      </DashboardLayout>
    )
  }

  if (!Number.isFinite(contractId) || contractId <= 0) {
    return (
      <DashboardLayout title="Chi tiết hợp đồng" description="Inspect hợp đồng nhân viên và dữ liệu nhạy cảm liên quan">
        <EmptyState description="URL không chứa contractId hợp lệ." title="Thiếu contractId hợp lệ" />
      </DashboardLayout>
    )
  }

  if (resolvedEmployeeId <= 0) {
    return (
      <DashboardLayout
        title="Chi tiết hợp đồng"
        description="Resolve contract detail từ employee-scoped contract lookup."
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/contracts">Quay lại contracts</Link>
          </Button>
        }
      >
        <ReadonlyBanner message="Public API chưa publish contract detail endpoint trực tiếp. Cần employee ID để resolve contract trong employee-scoped contract list." />
        <FormSection title="Resolve contract context">
          <div className="field-grid">
            <Input
              label="Employee ID"
              onChange={(event) => setLookupEmployeeId(event.target.value)}
              placeholder="VD: 1001"
              type="number"
              value={lookupEmployeeId}
            />
          </div>
          <div className="form-actions align-start">
            <Button onClick={submitLookup} size="sm">
              Resolve contract
            </Button>
          </div>
          {validationError ? <p className="error-text">{validationError}</p> : null}
        </FormSection>
      </DashboardLayout>
    )
  }

  if (contractsQuery.isLoading) {
    return (
      <DashboardLayout title="Chi tiết hợp đồng" description="Inspect hợp đồng nhân viên và dữ liệu nhạy cảm liên quan">
        <EmptyState description="Đang resolve hợp đồng trong employee-scoped contract list..." title="Đang tải hợp đồng" />
      </DashboardLayout>
    )
  }

  if (contractsQuery.error) {
    return (
      <DashboardLayout
        title="Chi tiết hợp đồng"
        description="Inspect hợp đồng nhân viên và dữ liệu nhạy cảm liên quan"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/contracts">Quay lại contracts</Link>
          </Button>
        }
      >
        <ErrorState
          actionLabel="Tải lại"
          message={getHrErrorMessage(contractsQuery.error, `Không thể tải contracts của employee #${resolvedEmployeeId}.`)}
          onAction={() => void contractsQuery.refetch()}
          title="Không thể resolve contract detail"
        />
      </DashboardLayout>
    )
  }

  if (!contract) {
    return (
      <DashboardLayout
        title="Chi tiết hợp đồng"
        description="Inspect hợp đồng nhân viên và dữ liệu nhạy cảm liên quan"
        actions={
          <Button asChild size="sm" variant="secondary">
            <Link to="/hr/contracts">Quay lại contracts</Link>
          </Button>
        }
      >
        <EmptyState
          description={`Không tìm thấy contract #${contractId} trong employee #${resolvedEmployeeId}.`}
          title="Contract not found in employee scope"
        />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Chi tiết hợp đồng"
      description="Read-first inspection cho hợp đồng lao động và payroll-sensitive fields."
      actions={
        <Button asChild size="sm" variant="secondary">
          <Link to={`/hr/contracts?employeeId=${resolvedEmployeeId}`}>Quay lại contracts</Link>
        </Button>
      }
    >
      <ReadonlyBanner
        message={
          contractUiPolicy.isTerminal(contract.contractStatus)
            ? 'Hợp đồng đang ở trạng thái terminal/read-only.'
            : 'Contract detail hiện publish ở chế độ read-first. Các thao tác chỉnh sửa chưa được mở.'
        }
      />

      <EntityHeader
        actions={
          canReadEmployees ? (
            <Button onClick={() => navigate(`/hr/employees/${resolvedEmployeeId}`)} size="sm" variant="secondary">
              Open employee detail
            </Button>
          ) : null
        }
        eyebrow="HR / Contract"
        metadata={
          <>
            <span>Employee #{resolvedEmployeeId}</span>
            <span>Region: {contract.regionId ? `#${contract.regionId}` : 'Unscoped'}</span>
            <span>Salary type: {contract.salaryType}</span>
            <span>Effective: {formatDateRange(contract.startDate, contract.endDate)}</span>
          </>
        }
        status={<StatusBadge status={contract.contractStatus} />}
        title={`Contract #${contract.id}`}
      />

      <FormSection description="Tổng quan hợp đồng hiện có trong backend contract read model." title="Contract overview">
        <div className="meta-grid">
          <span>Employment type: {contract.employmentType}</span>
          <span>Salary type: {contract.salaryType}</span>
          <span>Status: {contract.contractStatus}</span>
          <span>Effective: {formatDateRange(contract.startDate, contract.endDate)}</span>
        </div>
      </FormSection>

      <FormSection
        description="Các field nhạy cảm sẽ bị masked khi principal không có hr.contract.detail.read."
        title="Sensitive payroll fields"
      >
        <div className="field-grid">
          <MaskedField
            helperText={
              canViewSensitiveFields
                ? 'Base salary read model từ backend contract response.'
                : 'Backend đã masked baseSalary vì thiếu hr.contract.detail.read.'
            }
            label="Base salary"
            mode={canViewSensitiveFields ? 'readonly-visible' : 'masked'}
            value={formatCurrencyAmount(contract.baseSalary)}
          />
          <MaskedField
            helperText={
              canViewSensitiveFields
                ? 'Tax code hiện rõ vì principal có quyền chi tiết hợp đồng.'
                : 'Backend đã masked taxCode vì thiếu hr.contract.detail.read.'
            }
            label="Tax code"
            mode={canViewSensitiveFields ? 'readonly-visible' : 'masked'}
            value={contract.taxCode ?? 'Missing tax code'}
          />
        </div>
      </FormSection>

      <FormSection description="Context nhân viên để điều hướng people-ops liền mạch." title="Employee context">
        {canReadEmployees ? (
          employeeQuery.error ? (
            <ErrorState
              actionLabel="Retry employee"
              message={getHrErrorMessage(employeeQuery.error, `Không thể tải employee #${resolvedEmployeeId}.`)}
              onAction={() => void employeeQuery.refetch()}
              title="Không thể tải employee context"
            />
          ) : employeeQuery.data ? (
            <div className="meta-grid">
              <span>{employeeQuery.data.employeeCode}</span>
              <span>{employeeQuery.data.fullName}</span>
              <span>{employeeQuery.data.email ?? 'No email'}</span>
              <span>{employeeQuery.data.phone ?? 'No phone'}</span>
            </div>
          ) : (
            <EmptyState description="Không thể resolve employee context cho contract này." title="No employee context" />
          )
        ) : (
          <PermissionDeniedInline message="Bạn cần quyền hr.employee.read để xem employee context của hợp đồng." />
        )}
      </FormSection>
    </DashboardLayout>
  )
}
