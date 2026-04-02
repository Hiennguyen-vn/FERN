import type { PropsWithChildren, ReactNode } from 'react'

interface DashboardLayoutProps extends PropsWithChildren {
  title: string
  description?: string
  eyebrow?: string
  /** Optional right-side action slot (buttons, links etc.) */
  actions?: ReactNode
}

export function DashboardLayout({ title, description, eyebrow, actions, children }: DashboardLayoutProps) {
  return (
    <section className="page-stack page-layout">
      <header className="page-header dashboard-page-header">
        <div className="page-heading">
          {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
          <h1>{title}</h1>
          {description ? <p className="muted-text">{description}</p> : null}
        </div>
        {actions ? <div className="page-actions">{actions}</div> : null}
      </header>
      {children}
    </section>
  )
}
