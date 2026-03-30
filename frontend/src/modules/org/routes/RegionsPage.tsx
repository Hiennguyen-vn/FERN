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
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useRegion, useRegions } from '../hooks/useOrg'
import type { OrgRegion } from '../model/org.types'
import { getOrgErrorMessage } from '../services/orgError.service'
import { loadRecentRegionIds, saveRecentRegionId } from '../services/orgBrowseHistory.service'
import {
  buildParentRegionLabel,
  formatOrgInstant,
  mergeOrgIds,
} from '../services/orgReadModel.service'
import { orgUiPolicy } from '../services/orgUiPolicy.service'

export function RegionsPage() {
  usePageTitle('Regions — Org')
  const navigate = useNavigate()
  const principal = usePrincipal()
  const { regionIds, selectedRegionId } = useScopeContext()
  const canOpen = orgUiPolicy.canOpenRegionsPage(principal)
  const [lookupInput, setLookupInput] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [recentRegionIds, setRecentRegionIds] = useState(() => loadRecentRegionIds())
  const [manualRegionIds, setManualRegionIds] = useState<number[]>([])
  const [lookupRegionId, setLookupRegionId] = useState<number | null>(null)

  const scopedRegionIds = useMemo(
    () => mergeOrgIds(regionIds, selectedRegionId ? [selectedRegionId] : []),
    [regionIds, selectedRegionId],
  )

  const regionLookupQuery = useRegion(lookupRegionId ?? 0, {
    enabled: canOpen && lookupRegionId !== null,
  })

  useEffect(() => {
    if (!regionLookupQuery.data) {
      return
    }

    saveRecentRegionId(regionLookupQuery.data.id)
    setRecentRegionIds(loadRecentRegionIds())
    setManualRegionIds((current) => mergeOrgIds(current, [regionLookupQuery.data?.id]))
    setLookupRegionId(null)
  }, [regionLookupQuery.data])

  const visibleRegionIds = useMemo(
    () => mergeOrgIds(scopedRegionIds, recentRegionIds, manualRegionIds),
    [manualRegionIds, recentRegionIds, scopedRegionIds],
  )

  const regionsQuery = useRegions(visibleRegionIds, {
    enabled: canOpen && visibleRegionIds.length > 0,
  })

  const rows = useMemo(
    () => [...regionsQuery.rows].sort((left, right) => left.name.localeCompare(right.name, 'vi')),
    [regionsQuery.rows],
  )

  const parentRegionIds = useMemo(
    () =>
      mergeOrgIds(
        rows
          .map((row) => row.parentRegionId)
          .filter((value): value is number => typeof value === 'number' && Number.isInteger(value) && value > 0),
      ),
    [rows],
  )

  const parentRegionsQuery = useRegions(parentRegionIds, {
    enabled: canOpen && parentRegionIds.length > 0,
  })

  const parentRegionLookup = useMemo(() => {
    return new Map(
      rows
        .concat(parentRegionsQuery.rows)
        .map((row) => [row.id, row] as const),
    )
  }, [parentRegionsQuery.rows, rows])

  const columns = useMemo<Array<DataTableColumn<OrgRegion>>>(
    () => [
      {
        key: 'region',
        header: 'Region',
        render: (region) => (
          <div className="page-stack" style={{ gap: '0.35rem' }}>
            <strong>{region.name}</strong>
            <span className="muted-text">{region.code}</span>
          </div>
        ),
      },
      {
        key: 'parent',
        header: 'Parent region',
        render: (region) => buildParentRegionLabel(region.parentRegionId, parentRegionLookup.get(region.parentRegionId ?? 0)),
      },
      {
        key: 'currency',
        header: 'Currency',
        render: (region) => region.currencyCode,
      },
      {
        key: 'timezone',
        header: 'Timezone',
        render: (region) => region.timezoneName,
      },
      {
        key: 'taxCode',
        header: 'Tax code',
        render: (region) => region.taxCode ?? 'No tax code',
      },
      {
        key: 'updatedAt',
        header: 'Updated at',
        render: (region) => formatOrgInstant(region.updatedAt),
      },
    ],
    [parentRegionLookup],
  )

  function submitLookup() {
    const parsed = Number(lookupInput)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setValidationError('Nhập region ID hợp lệ để tra cứu.')
      return
    }

    setValidationError(null)
    setLookupRegionId(parsed)
  }

  if (!canOpen) {
    return (
      <DashboardLayout title="Regions" description="Read-first organizational region administration surface.">
        <PermissionDeniedInline message="Bạn cần quyền org.region.read để mở danh sách region." />
      </DashboardLayout>
    )
  }

  const lookupError = regionLookupQuery.error
    ? getOrgErrorMessage(regionLookupQuery.error, 'Không thể tra cứu region theo ID.')
    : null
  const tableError = rows.length === 0 && regionsQuery.error
    ? getOrgErrorMessage(regionsQuery.error, 'Không thể tải region trong scope hiện tại.')
    : null

  return (
    <DashboardLayout
      title="Regions"
      description="Read-first region browse cho organizational hierarchy, timezone và currency reference."
    >
      <ReadonlyBanner message="Regions hiện publish ở chế độ read-first. Vì backend chưa có public list endpoint, trang này dùng scope hiện tại, recent history và ID lookup." />

      <Card title="Region lookup & scope context">
        <div className="field-grid">
          <Input
            label="Lookup region ID"
            onChange={(event) => setLookupInput(event.target.value)}
            placeholder="VD: 1"
            type="number"
            value={lookupInput}
          />
        </div>
        <div className="form-actions align-start">
          <Button onClick={submitLookup} size="sm">
            Lookup region
          </Button>
        </div>
        <div className="meta-grid">
          <span>Scoped region IDs: {scopedRegionIds.length}</span>
          <span>Recent region IDs: {recentRegionIds.length}</span>
          <span>Loaded rows: {rows.length}</span>
        </div>
        {regionLookupQuery.isLoading ? <p className="muted-text">Đang tra cứu region theo ID...</p> : null}
        {validationError ? <p className="error-text">{validationError}</p> : null}
        {lookupError ? <p className="error-text">{lookupError}</p> : null}
      </Card>

      <DataTable
        columns={columns}
        emptyDescription="Không có region nào trong scope hiện tại hoặc recent history. Dùng ô lookup để mở region theo ID."
        emptyTitle="No scoped regions yet"
        error={tableError}
        loading={regionsQuery.isLoading}
        loadingDescription="Đang tải region detail cho các ID có thể truy cập..."
        loadingTitle="Đang tải regions"
        onRetry={() => void regionsQuery.refresh()}
        onRowClick={(region) => navigate(`/org/regions/${region.id}`)}
        rowKey={(region) => region.id}
        rows={rows}
      />
    </DashboardLayout>
  )
}
