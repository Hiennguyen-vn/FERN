export function canReceiveGoodsReceipt(status: string) {
  return status.toUpperCase() === 'DRAFT'
}

export function canPostGoodsReceipt(status: string) {
  return status.toUpperCase() === 'RECEIVED'
}

export function canCancelGoodsReceipt(status: string) {
  return !['POSTED', 'CANCELLED'].includes(status.toUpperCase())
}
