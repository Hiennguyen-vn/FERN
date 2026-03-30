export function buildInventoryTransactionSummary(txnType: string, sourceReferenceType?: string | null) {
  if (sourceReferenceType) {
    return `${txnType} via ${sourceReferenceType}`
  }

  return txnType
}
