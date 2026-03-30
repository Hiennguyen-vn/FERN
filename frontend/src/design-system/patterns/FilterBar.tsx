import type { PropsWithChildren, ReactNode } from 'react'

interface FilterBarProps extends PropsWithChildren {
  actions?: ReactNode
  description?: string
  title?: string
}

export function FilterBar({ actions, children, description, title = 'Bộ lọc báo cáo' }: FilterBarProps) {
  return (
    <section className="surface-panel filter-bar">
      <div className="page-header">
        <div>
          <h2 className="card-title">{title}</h2>
          {description ? <p className="muted-text">{description}</p> : null}
        </div>
        {actions ? <div className="filter-bar-actions">{actions}</div> : null}
      </div>
      <div className="field-grid">{children}</div>
    </section>
  )
}
