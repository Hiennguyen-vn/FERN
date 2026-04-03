import clsx from 'clsx'
import type { ProductStatus } from '../model/catalog.types'

export const STATUS_LABELS: Record<ProductStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  DISCONTINUED: 'Discontinued',
}

const STATUS_CLASSNAMES: Record<ProductStatus, string> = {
  DRAFT: 'catalog-status-draft',
  ACTIVE: 'catalog-status-active',
  INACTIVE: 'catalog-status-inactive',
  DISCONTINUED: 'catalog-status-discontinued',
}

export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  return (
    <span className={clsx('catalog-status-badge', STATUS_CLASSNAMES[status])}>
      <span className="catalog-status-dot" />
      {STATUS_LABELS[status]}
    </span>
  )
}
