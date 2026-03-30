import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getReportInventoryTransactions, getReportStockBalances } from '../api/reports.api'
import { reportQueryKeys } from '../api/reports.queries'
import type { InventoryReportFilters } from '../model/inventoryReport.types'
import { buildInventoryReportSummary } from '../services/reportsReadModel.service'

interface UseInventoryReportOptions {
  enabled?: boolean
}

export function useInventoryReport(filters: InventoryReportFilters, options: UseInventoryReportOptions = {}) {
  const enabled = options.enabled ?? true
  const canQuery = enabled && Boolean(filters.outletId)

  const balanceQuery = useQuery({
    enabled: canQuery,
    queryFn: () => getReportStockBalances(filters),
    queryKey: reportQueryKeys.inventoryBalances({ ...filters }),
  })

  const transactionQuery = useQuery({
    enabled: canQuery,
    queryFn: () => getReportInventoryTransactions(filters),
    queryKey: reportQueryKeys.inventoryTransactions({ ...filters }),
  })

  const summary = useMemo(
    () => buildInventoryReportSummary(balanceQuery.data?.items ?? [], transactionQuery.data?.items ?? []),
    [balanceQuery.data?.items, transactionQuery.data?.items],
  )

  return {
    balanceQuery,
    summary,
    transactionQuery,
  }
}
