import { Card } from '../components/card'

export interface SummaryCardItem {
  description?: string
  label: string
  tone?: 'danger' | 'default' | 'success' | 'warning' | 'info'
  value: number | string
}

interface SummaryCardsProps {
  items: SummaryCardItem[]
}

export function SummaryCards({ items }: SummaryCardsProps) {
  return (
    <div className="summary-card-grid">
      {items.map((item) => (
        <Card
          className={`summary-card summary-card-${item.tone ?? 'default'}`}
          key={`${item.label}-${String(item.value)}`}
        >
          <div className="summary-card-label">{item.label}</div>
          <strong className="summary-card-value">{item.value}</strong>
          {item.description ? <p className="muted-text">{item.description}</p> : null}
        </Card>
      ))}
    </div>
  )
}
