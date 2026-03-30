import type { PropsWithChildren, ReactNode } from 'react'

interface FormSectionProps extends PropsWithChildren {
  actions?: ReactNode
  description?: string
  title: string
}

export function FormSection({ actions, children, description, title }: FormSectionProps) {
  return (
    <section className="surface-panel form-section">
      <div className="page-header">
        <div>
          <h2 className="card-title">{title}</h2>
          {description ? <p className="muted-text">{description}</p> : null}
        </div>
        {actions}
      </div>
      <div className="page-stack">{children}</div>
    </section>
  )
}
