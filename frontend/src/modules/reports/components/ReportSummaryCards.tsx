import { SummaryCards } from '@design-system/index'
import type { SummaryCardItem } from '@design-system/index'

interface ReportSummaryCardsProps {
  items: SummaryCardItem[]
}

export function ReportSummaryCards({ items }: ReportSummaryCardsProps) {
  return <SummaryCards items={items} />
}
