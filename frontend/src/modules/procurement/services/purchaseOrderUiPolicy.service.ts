export function canSubmitPurchaseOrder(status: string) {
  return status.toUpperCase() === 'DRAFT'
}

export function canApprovePurchaseOrder(status: string) {
  return status.toUpperCase() === 'SUBMITTED'
}

export function canIssuePurchaseOrder(status: string) {
  return status.toUpperCase() === 'APPROVED'
}

export function canCancelPurchaseOrder(status: string) {
  return !['ISSUED', 'CANCELLED'].includes(status.toUpperCase())
}
