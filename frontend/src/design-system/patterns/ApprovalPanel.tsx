import type { ReactNode } from 'react'

interface ApprovalPanelProps {
  actions?: ReactNode
  children?: ReactNode
  description?: string
  status?: ReactNode
  title?: string
}

export function ApprovalPanel({
  actions,
  children,
  description,
  status,
  title = 'Approval workspace',
}: ApprovalPanelProps) {
  return (
    <section className="surface-panel form-section">
      <div className="page-header">
        <div className="page-stack" style={{ gap: '0.35rem' }}>
          <div className="entity-header-title">
            <h2 className="card-title">{title}</h2>
            {status}
          </div>
          {description ? <p className="muted-text">{description}</p> : null}
        </div>
        {actions ? <div className="form-actions align-start">{actions}</div> : null}
      </div>
      <div className="page-stack">{children}</div>
    </section>
  )
}
