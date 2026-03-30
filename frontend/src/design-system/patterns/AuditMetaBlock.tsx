import type { ReactNode } from 'react'

interface AuditMetaItem {
  label: string
  value: ReactNode
}

interface AuditMetaBlockProps {
  items: AuditMetaItem[]
  title?: string
}

export function AuditMetaBlock({ items, title = 'Audit metadata' }: AuditMetaBlockProps) {
  return (
    <section className="surface-panel form-section audit-meta-block">
      <div className="page-stack">
        <h2 className="card-title">{title}</h2>
        <div className="meta-grid">
          {items.map((item) => (
            <span key={item.label}>
              <strong>{item.label}:</strong> {item.value}
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}
