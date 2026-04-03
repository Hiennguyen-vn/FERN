import clsx from 'clsx'
import type { RecipeVersionStatus } from '../model/catalog.types'

const VERSION_STATUS_STYLE: Record<RecipeVersionStatus, { className: string; label: string }> = {
  DRAFT: { className: 'catalog-status-draft', label: 'Draft' },
  ACTIVE: { className: 'catalog-status-active', label: 'Active' },
  ARCHIVED: { className: 'catalog-status-archived', label: 'Archived' },
}

export function RecipeVersionStatusBadge({ status }: { status: RecipeVersionStatus }) {
  const { className, label } = VERSION_STATUS_STYLE[status]
  return (
    <span className={clsx('catalog-status-badge', className)}>
      <span className="catalog-status-dot" />
      {label}
    </span>
  )
}

export { VERSION_STATUS_STYLE }
