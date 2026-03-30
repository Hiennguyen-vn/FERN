import { Button, Card, EmptyState, StatusBadge } from '@design-system/index'
import { formatMoney } from '@shared/formatters'
import type { ResolvedMenuItem } from '../model/pos.types'

interface PosCatalogGridProps {
  canAdd: boolean
  items: ResolvedMenuItem[]
  onAdd: (item: ResolvedMenuItem) => void
}

export function PosCatalogGrid({ canAdd, items, onAdd }: PosCatalogGridProps) {
  if (items.length === 0) {
    return (
      <EmptyState
        description="Không có món nào active, available và có effective price cho bộ lọc hiện tại."
        title="No menu items"
      />
    )
  }

  return (
    <div className="pos-catalog-grid">
      {items.map((item) => (
        <Card className="pos-catalog-card" key={item.productId} title={item.name}>
          <div className="page-stack">
            <div className="stack-inline">
              <StatusBadge status={item.priceType} />
              <span className="muted-text">{item.categoryCode}</span>
            </div>
            <p className="muted-text">{item.description || `Code: ${item.code}`}</p>
            <strong>{formatMoney(item.priceValue, item.currencyCode)}</strong>
            <Button disabled={!canAdd} onClick={() => onAdd(item)} size="sm">
              Add to cart
            </Button>
          </div>
        </Card>
      ))}
    </div>
  )
}
