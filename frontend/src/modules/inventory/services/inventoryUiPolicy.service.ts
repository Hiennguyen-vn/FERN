export function getInventoryDirectionLabel(qtyChange: string | number) {
  return Number(qtyChange) >= 0 ? 'Inbound' : 'Outbound'
}

export function getInventoryTxnTone(txnType: string): 'neutral' | 'success' | 'warning' | 'danger' {
  switch (txnType.toUpperCase()) {
    case 'GR_POST':
    case 'ADJUSTMENT_IN':
      return 'success'
    case 'WASTE_POST':
    case 'SALE_CONSUME':
      return 'danger'
    case 'COUNT_POST':
      return 'warning'
    default:
      return 'neutral'
  }
}
