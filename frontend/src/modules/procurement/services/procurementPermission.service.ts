/** Re-export procurement permission helpers from core so feature code stays on `@core` boundaries for routing/shell. */
export {
  canApproveInvoice,
  canCreateGoodsReceipt,
  canCreatePurchaseOrder,
  canDisputeInvoice,
  canReadGoodsReceipts,
  canReadInvoices,
  canReadPayments,
  canReadPurchaseOrders,
  canReadSuppliers,
  canWriteSuppliers,
  canRecordPayment,
  canReviewInvoice,
  canSeeProcurementNavigation,
} from '@core/permissions/procurement.permissions'
