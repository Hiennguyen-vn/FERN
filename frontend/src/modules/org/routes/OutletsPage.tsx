import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
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
import { useOutlet, useOutlets, useRegions } from '../hooks/useOrg'
import type { OrgOutlet } from '../model/org.types'
import { getOrgErrorMessage } from '../services/orgError.service'
import { loadRecentOutletIds, saveRecentOutletId } from '../services/orgBrowseHistory.service'
import {
  buildOutletContactLabel,
  buildRegionLabel,
  formatOrgDate,
  mergeOrgIds,
  matchesOrgSearch,
} from '../services/orgReadModel.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

export function OutletsPage() {
  usePageTitle('Outlets — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { outletIds, selectedOutletId, selectedRegionId } = useScopeContext()
  const canOpen = orgUiPolicy.canOpenOutletsPage(principal)
  const [lookupInput, setLookupInput] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [recentOutletIds, setRecentOutletIds] = useState(() => loadRecentOutletIds())
  const [manualOutletIds, setManualOutletIds] = useState<number[]>([])
  const [lookupOutletId, setLookupOutletId] = useState<number | null>(null)
  const [regionFilter, setRegionFilter] = useState(selectedRegionId ? String(selectedRegionId) : 'ALL')

  const scopedOutletIds = useMemo(
    () => mergeOrgIds(outletIds, selectedOutletId ? [selectedOutletId] : []),
    [outletIds, selectedOutletId],
  )

  const outletLookupQuery = useOutlet(lookupOutletId ?? 0, {
    enabled: canOpen && lookupOutletId !== null,
  })

  useEffect(() => {
    if (!outletLookupQuery.data) {
      return
    }

    saveRecentOutletId(outletLookupQuery.data.id)
    setRecentOutletIds(loadRecentOutletIds())
    setManualOutletIds((current) => mergeOrgIds(current, [outletLookupQuery.data?.id]))
    setLookupOutletId(null)
  }, [outletLookupQuery.data])

  const visibleOutletIds = useMemo(
    () => mergeOrgIds(scopedOutletIds, recentOutletIds, manualOutletIds),
    [manualOutletIds, recentOutletIds, scopedOutletIds],
  )

  const outletsQuery = useOutlets(visibleOutletIds, {
    enabled: canOpen && visibleOutletIds.length > 0,
  })

  const allRows = useMemo(
    () => [...outletsQuery.rows].sort((left, right) => left.name.localeCompare(right.name, 'vi')),
    [outletsQuery.rows],
  )

  const regionIds = useMemo(
    () => mergeOrgIds(allRows.map((row) => row.regionId), selectedRegionId ? [selectedRegionId] : []),
    [allRows, selectedRegionId],
  )

  const regionsQuery = useRegions(regionIds, {
    enabled: canOpen && regionIds.length > 0,
  })

  const regionLookup = useMemo(() => new Map(regionsQuery.rows.map((row) => [row.id, row] as const)), [regionsQuery.rows])

  const statusOptions = useMemo<SelectOption[]>(() => {
    const statuses = Array.from(new Set(allRows.map((outlet) => outlet.status).filter(Boolean))).sort()
    return [
      { label: 'Tất cả trạng thái', value: 'ALL' },
      ...statuses.map((status) => ({ label: status, value: status })),
    ]
  }, [allRows])

  const regionOptions = useMemo<SelectOption[]>(() => {
    const availableRegionIds = mergeOrgIds(allRows.map((outlet) => outlet.regionId), selectedRegionId ? [selectedRegionId] : [])
    return [
      { label: 'Tất cả regions', value: 'ALL' },
      ...availableRegionIds.map((regionId) => ({
        label: buildRegionLabel(regionId, regionLookup.get(regionId)),
        value: String(regionId),
      })),
    ]
  }, [allRows, regionLookup, selectedRegionId])

  const rows = useMemo(() => {
    return allRows.filter((outlet) => {
      const matchesRegion = regionFilter === 'ALL' || String(outlet.regionId) === regionFilter
      const matchesStatus = statusFilter === 'ALL' || outlet.status === statusFilter
      const matchesSearchTerm = matchesOrgSearch(
        [outlet.id, outlet.code, outlet.name, outlet.address, outlet.phone, outlet.email],
        search,
      )

      return matchesRegion && matchesStatus && matchesSearchTerm
    })
  }, [allRows, regionFilter, search, statusFilter])

  const columns = useMemo<Array<DataTableColumn<OrgOutlet>>>(
    () => [
      {
        key: 'outlet',
        header: 'Outlet',
        render: (outlet) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{outlet.name}</strong>
            <span className="muted-text">{outlet.code}</span>
          </div>
        ),
      },
      {
        key: 'region',
        header: 'Region',
        render: (outlet) => buildRegionLabel(outlet.regionId, regionLookup.get(outlet.regionId)),
      },
      {
        key: 'status',
        header: 'Status',
        render: (outlet) => <StatusBadge status={outlet.status} />,
      },
      {
        key: 'dates',
        header: 'Operational dates',
        render: (outlet) => `${formatOrgDate(outlet.openedAt)} → ${formatOrgDate(outlet.closedAt)}`,
      },
      {
        key: 'contact',
        header: 'Contact',
        render: (outlet) => buildOutletContactLabel(outlet),
      },
    ],
    [regionLookup],
  )

  function submitLookup() {
    const parsed = Number(lookupInput)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập outlet ID hợp lệ để tra cứu.')
      return
    }

    setValidationError(null)
    setLookupOutletId(parsed)
  }

  if (!canOpen) {
    return (
      <DashboardLayout title="Outlets" description="Read-first outlet administration surface.">
        <PermissionDeniedInline message="Bạn cần quyền org.outlet.read để mở danh sách outlet." />
      </DashboardLayout>
    )
  }

  const lookupError = outletLookupQuery.error
    ? getOrgErrorMessage(outletLookupQuery.error, 'Không thể tra cứu outlet theo ID.')
    : null
  const tableError = rows.length === 0 && outletsQuery.error
    ? getOrgErrorMessage(outletsQuery.error, 'Không thể tải outlet trong scope hiện tại.')
    : null

  return (
    <DashboardLayout
      title="Outlets"
      description="Table-first outlet browse cho operational metadata, status và region context."
    >
      <ReadonlyBanner message="Outlets hiện publish ở chế độ read-first. Vì backend chưa có public list endpoint, trang này dùng scope hiện tại, recent history và ID lookup." />

      <Card title="Outlet lookup & filters">
        <div className="field-grid">
          <Input
            label="Lookup outlet ID"
            onChange={(event) => setLookupInput(event.target.value)}
            placeholder="VD: 101"
            type="number"
            value={lookupInput}
          />
          <Input
            label="Search outlets"
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tên, mã hoặc contact..."
            value={search}
          />
          <Select
            label="Region filter"
            onChange={(event) => setRegionFilter(event.target.value)}
            options={regionOptions}
            value={regionFilter}
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
            Lookup outlet
          </Button>
        </div>
        <div className="meta-grid">
          <span>Scoped outlet IDs: {scopedOutletIds.length}</span>
          <span>Recent outlet IDs: {recentOutletIds.length}</span>
          <span>Filtered rows: {rows.length}</span>
        </div>
        {outletLookupQuery.isLoading ? <p className="muted-text">Đang tra cứu outlet theo ID...</p> : null}
        {validationError ? <p className="error-text">{validationError}</p> : null}
        {lookupError ? <p className="error-text">{lookupError}</p> : null}
      </Card>

      <DataTable
        columns={columns}
        emptyDescription="Không có outlet nào trong scope hiện tại hoặc recent history. Dùng ô lookup để mở outlet theo ID."
        emptyTitle="No scoped outlets yet"
        error={tableError}
        loading={outletsQuery.isLoading}
        loadingDescription="Đang tải outlet detail cho các ID có thể truy cập..."
        loadingTitle="Đang tải outlets"
        onRetry={() => void outletsQuery.refresh()}
        onRowClick={(outlet) => navigate(`/org/outlets/${outlet.id}`)}
        rowKey={(outlet) => outlet.id}
        rows={rows}
      />
    </DashboardLayout>
  )
}
