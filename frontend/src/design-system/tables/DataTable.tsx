import type { ReactNode } from 'react'
import clsx from 'clsx'
import { Card } from '../components/card'
import { EmptyState } from '../components/empty-state'
import { ErrorState } from '../components/error-state'
import { Pagination } from '../components/pagination'

export interface DataTableColumn<T> {
  key: string
  header: string
  render: (row: T) => ReactNode
}

interface DataTableProps<T> {
  canNext?: boolean
  canPrevious?: boolean
  columns: Array<DataTableColumn<T>>
  currentPage?: number
  emptyDescription?: string
  emptyTitle?: string
  error?: string | null
  errorTitle?: string
  loading?: boolean
  loadingDescription?: string
  loadingTitle?: string
  onNext?: () => void
  onRowClick?: (row: T) => void
  onPrevious?: () => void
  onRetry?: () => void
  rowClassName?: (row: T) => string | undefined
  rowKey?: (row: T, index: number) => string | number
  rows: T[]
}

export function DataTable<T>({
  columns,
  emptyDescription = 'No data available for the current selection.',
  emptyTitle = 'No rows',
  error,
  errorTitle = 'Unable to load data',
  loading,
  loadingDescription = 'Loading table data...',
  loadingTitle = 'Loading',
  canNext,
  canPrevious,
  currentPage,
  onNext,
  onPrevious,
  onRowClick,
  onRetry,
  rowClassName,
  rowKey,
  rows,
}: DataTableProps<T>) {
  if (error) {
    return <ErrorState actionLabel={onRetry ? 'Retry' : undefined} message={error} onAction={onRetry} title={errorTitle} />
  }

  if (loading) {
    return (
      <Card className="table-loading-card" title={loadingTitle}>
        <p className="muted-text">{loadingDescription}</p>
        <div aria-hidden="true" className="table-skeleton">
          {Array.from({ length: 4 }).map((_, index) => (
            <div className="table-skeleton-row" key={index}>
              <span />
              <span />
              <span />
            </div>
          ))}
        </div>
      </Card>
    )
  }

  if (rows.length === 0) {
    return <EmptyState description={emptyDescription} title={emptyTitle} />
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr
              className={clsx(onRowClick && 'table-row-clickable', rowClassName?.(row))}
              key={rowKey ? rowKey(row, index) : index}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
            >
              {columns.map((column) => (
                <td key={column.key}>{column.render(row)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {typeof currentPage === 'number' && onNext && onPrevious && typeof canNext === 'boolean' && typeof canPrevious === 'boolean' ? (
        <Pagination
          canNext={canNext}
          canPrevious={canPrevious}
          currentPage={currentPage}
          onNext={onNext}
          onPrevious={onPrevious}
        />
      ) : null}
    </div>
  )
}
