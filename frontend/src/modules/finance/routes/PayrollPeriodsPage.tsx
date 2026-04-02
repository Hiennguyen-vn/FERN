import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  DataTable,
  Input,
  PermissionDeniedInline,
  Select,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn, SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { hrApi } from '../../hr/api/hr.api'
import type { PayrollPeriod, CreatePayrollPeriodPayload } from '../../hr/model/hr.types'
import { getFinanceErrorMessage } from '../services/financeError.service'
import { formatFinanceDateLabel } from '../services/financeWorkflow.service'
import { payrollApprovalUiPolicy } from '../services/payrollApprovalUiPolicy.service'

export function PayrollPeriodsPage() {
  usePageTitle('Payroll Periods — Finance')
  const principal = usePrincipal()
  const queryClient = useQueryClient()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canOpen = payrollApprovalUiPolicy.canOpenPayrollQueue(principal)
  const [regionFilter, setRegionFilter] = useState(
    selectedRegionId ? String(selectedRegionId) : regionIds.length === 1 ? String(regionIds[0]) : '',
  )

  const activeRegionId =
    regionFilter.trim() && Number.isFinite(Number(regionFilter)) ? Number(regionFilter) : undefined
  const canQuery = Boolean(activeRegionId) || Boolean(principal?.scopeRoots.system)
  
  const periodsQuery = useQuery({
    enabled: canOpen && canQuery,
    queryFn: () => hrApi.listPayrollPeriods(activeRegionId),
    queryKey: ['finance', 'payrollPeriods', activeRegionId],
  })

  const [isCreating, setIsCreating] = useState(false)
  const [createForm, setCreateForm] = useState<Partial<CreatePayrollPeriodPayload>>({})

  const createMutation = useMutation({
    mutationFn: (payload: CreatePayrollPeriodPayload) => hrApi.createPayrollPeriod(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['finance', 'payrollPeriods'] })
      setIsCreating(false)
      setCreateForm({})
    },
  })

  const regionOptions = useMemo<SelectOption[]>(
    () => regionIds.map((regionId) => ({ label: `Region #${regionId}`, value: String(regionId) })),
    [regionIds],
  )

  const columns = useMemo<Array<DataTableColumn<PayrollPeriod>>>(
    () => [
      {
        key: 'referenceCode',
        header: 'Ref Code',
        render: (period) => <strong>{period.referenceCode}</strong>,
      },
      {
        key: 'name',
        header: 'Name',
        render: (period) => period.name,
      },
      {
        key: 'startDate',
        header: 'Start Date',
        render: (period) => formatFinanceDateLabel(period.startDate),
      },
      {
        key: 'endDate',
        header: 'End Date',
        render: (period) => formatFinanceDateLabel(period.endDate),
      },
      {
        key: 'payDate',
        header: 'Pay Date',
        render: (period) => formatFinanceDateLabel(period.payDate),
      },
      {
        key: 'status',
        header: 'Status',
        render: (period) => <StatusBadge status={period.status} />,
      },
    ],
    [],
  )

  if (!canOpen) {
    return (
      <DashboardLayout title="Payroll Periods" description="Quản lý kỳ lương">
        <PermissionDeniedInline message="Bạn cần finance.payroll.read để mở trang này." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      title="Payroll Periods"
      description="Quản lý và tạo mới cấu hình kỳ lương cho các region."
    >
      <div className="action-bar" style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'flex-end' }}>
        <Button onClick={() => setIsCreating(!isCreating)} variant="primary">
          {isCreating ? 'Hủy tạo' : 'Tạo Payroll Period'}
        </Button>
      </div>

      {isCreating && (
        <Card title="Tạo Payroll Period mới" className="mb-4">
          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (createForm.name && createForm.startDate && createForm.endDate && activeRegionId) {
                createMutation.mutate({
                  name: createForm.name,
                  startDate: createForm.startDate,
                  endDate: createForm.endDate,
                  payDate: createForm.payDate,
                  note: createForm.note,
                  regionId: activeRegionId,
                })
              }
            }}
          >
            <div className="field-grid">
              <Input
                label="Region ID"
                value={activeRegionId?.toString() || ''}
                disabled
              />
              <Input
                label="Tên kỳ lương"
                placeholder="VD: Tháng 3/2026"
                required
                value={createForm.name || ''}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
              />
              <Input
                label="Ngày bắt đầu (YYYY-MM-DD)"
                type="date"
                required
                value={createForm.startDate || ''}
                onChange={(e) => setCreateForm({ ...createForm, startDate: e.target.value })}
              />
              <Input
                label="Ngày kết thúc (YYYY-MM-DD)"
                type="date"
                required
                value={createForm.endDate || ''}
                onChange={(e) => setCreateForm({ ...createForm, endDate: e.target.value })}
              />
              <Input
                label="Ngày thanh toán (YYYY-MM-DD)"
                type="date"
                value={createForm.payDate || ''}
                onChange={(e) => setCreateForm({ ...createForm, payDate: e.target.value })}
              />
              <Input
                label="Ghi chú"
                value={createForm.note || ''}
                onChange={(e) => setCreateForm({ ...createForm, note: e.target.value })}
              />
            </div>
            
            {createMutation.error && (
              <div style={{ color: 'red', marginTop: '1rem' }}>
                {getFinanceErrorMessage(createMutation.error, 'Lỗi khi tạo kỳ lương')}
              </div>
            )}

            <div style={{ marginTop: '1rem' }}>
              <Button type="submit" variant="primary" disabled={createMutation.isPending || !activeRegionId}>
                {createMutation.isPending ? 'Đang tạo...' : 'Lưu lại'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      <Card title="Bộ lọc">
        <div className="field-grid">
          {regionOptions.length > 0 ? (
            <Select
              label="Region"
              onChange={(event) => setRegionFilter(event.target.value)}
              options={regionOptions}
              placeholder={principal?.scopeRoots.system ? 'All regions (system scope)' : 'Select region'}
              value={regionFilter}
            />
          ) : (
            <Input
              label="Region ID"
              onChange={(event) => setRegionFilter(event.target.value)}
              placeholder={principal?.scopeRoots.system ? 'Optional region filter' : 'Region required'}
              type="number"
              value={regionFilter}
            />
          )}
        </div>
      </Card>

      <DataTable
        columns={columns}
        emptyDescription={
          canQuery
            ? 'Không có payroll period nào khớp bộ lọc hiện tại.'
            : 'Chọn region hợp lệ trước khi xem danh sách payroll period.'
        }
        emptyTitle={canQuery ? 'No matching payroll periods' : 'Region required'}
        error={periodsQuery.error ? getFinanceErrorMessage(periodsQuery.error, 'Không thể tải danh sách payroll periods.') : null}
        loading={periodsQuery.isLoading}
        loadingDescription="Đang tải danh sách..."
        loadingTitle="Đang tải"
        onRetry={() => void periodsQuery.refetch()}
        rowKey={(period) => period.id}
        rows={periodsQuery.data ?? []}
      />
    </DashboardLayout>
  )
}
