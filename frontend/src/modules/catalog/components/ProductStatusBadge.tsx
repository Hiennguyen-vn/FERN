import type { ProductStatus } from '../model/catalog.types'

// Backend ProductStatus: DRAFT, ACTIVE, INACTIVE, DISCONTINUED
const STATUS_COLORS: Record<ProductStatus, string> = {
  DRAFT: 'var(--color-primary-500)',
  ACTIVE: 'var(--color-success-500)',
  INACTIVE: 'var(--color-neutral-400)',
  DISCONTINUED: 'var(--color-danger-500)',
}

export const STATUS_LABELS: Record<ProductStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  DISCONTINUED: 'Discontinued',
}

export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  const color = STATUS_COLORS[status]
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '0.375rem',
        padding: '0.2rem 0.625rem',
        borderRadius: 'var(--radius-full)',
        fontSize: 'var(--text-xs)',
        fontWeight: 'var(--font-medium)',
        color,
        background: `${color}1a`,
        border: `1px solid ${color}33`,
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: color, flexShrink: 0 }} />
      {STATUS_LABELS[status]}
    </span>
  )
}
