import type { ReactNode } from 'react'

interface EntityHeaderProps {
  actions?: ReactNode
  eyebrow?: string
  metadata?: ReactNode
  status?: ReactNode
  title: string
}

export function EntityHeader({ actions, eyebrow, metadata, status, title }: EntityHeaderProps) {
  return (
    <header className="surface-panel entity-header">
      <div className="page-header">
        <div className="page-stack">
          {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
          <div className="entity-header-title">
            <h2>{title}</h2>
            {status}
          </div>
          {metadata ? <div className="meta-grid">{metadata}</div> : null}
        </div>
        {actions ? <div className="form-actions align-start">{actions}</div> : null}
      </div>
    </header>
  )
}
