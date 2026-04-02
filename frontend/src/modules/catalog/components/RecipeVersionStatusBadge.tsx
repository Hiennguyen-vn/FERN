import type { RecipeVersionStatus } from '../model/catalog.types'

const VERSION_STATUS_STYLE: Record<RecipeVersionStatus, { color: string; label: string }> = {
  DRAFT: { color: 'var(--color-warning-400)', label: 'Draft' },
  ACTIVE: { color: 'var(--color-success-500)', label: 'Active' },
  ARCHIVED: { color: 'var(--color-neutral-500)', label: 'Archived' },
}

export function RecipeVersionStatusBadge({ status }: { status: RecipeVersionStatus }) {
  const { color, label } = VERSION_STATUS_STYLE[status]
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.375rem', fontSize: 'var(--text-xs)', color, fontWeight: 'var(--font-medium)' }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: color }} />
      {label}
    </span>
  )
}

export { VERSION_STATUS_STYLE }
