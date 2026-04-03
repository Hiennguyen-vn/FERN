import { Link, useParams } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  PermissionDeniedInline,
  ReadonlyBanner,
  StatusBadge,
} from '@design-system/index'
import type { DataTableColumn } from '@design-system/index'
import { useConfirmAction } from '@shared/hooks/useConfirmAction'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { usePurchaseOrder, usePurchaseOrderAction } from '../hooks/usePurchaseOrder'
import { useSuppliers } from '../hooks/useSuppliers'
import type { PurchaseOrderLine } from '../model/procurement.types'
import {
  canApprovePurchaseOrder,
  canCancelPurchaseOrder,
  canIssuePurchaseOrder,
  canSubmitPurchaseOrder,
} from '../services/purchaseOrderUiPolicy.service'
import { canReadPurchaseOrders, canReadSuppliers } from '../services/procurementPermission.service'

function formatAmount(value: number | null) {
  if (value == null || !Number.isFinite(value)) {
    return '—'
  }

  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value)
}

export function PurchaseOrderDetailPage() {
  const principal = usePrincipal()
  const params = useParams<{ purchaseOrderId: string }>()
  const purchaseOrderId = params.purchaseOrderId ? Number(params.purchaseOrderId) : null
  const confirmAction = useConfirmAction()
  const query = usePurchaseOrder(purchaseOrderId)
  const actionMutation = usePurchaseOrderAction()
  const suppliersQuery = useSuppliers({
    enabled: canReadPurchaseOrders(principal) && canReadSuppliers(principal),
  })

  usePageTitle(purchaseOrderId ? `Purchase Order ${purchaseOrderId}` : 'Purchase Order Detail')

  if (!canReadPurchaseOrders(principal)) {
    return (
      <DashboardLayout
        description="Inspect supplier, outlet, line items, and workflow state for a purchase order."
        eyebrow="Procurement"
        title="Purchase Order Detail"
      >
        <PermissionDeniedInline message="You need procurement.po.read to inspect purchase order detail." />
      </DashboardLayout>
    )
  }

  const columns: Array<DataTableColumn<PurchaseOrderLine>> = [
    {
      key: 'ingredientId',
      header: 'Item',
      render: (row) => (
        <div className="cell-stack">
          <strong>Ingredient #{row.ingredientId}</strong>
          <span className="cell-subtitle">Line {row.lineNumber}</span>
        </div>
      ),
    },
    { key: 'qtyOrdered', header: 'Quantity', render: (row) => row.qtyOrdered },
    { key: 'uomCode', header: 'UOM', render: (row) => row.uomCode },
    { key: 'expectedUnitPrice', header: 'Unit Price', render: (row) => formatAmount(row.expectedUnitPrice) },
    { key: 'taxPercent', header: 'Tax %', render: (row) => row.taxPercent ?? '0' },
    { key: 'status', header: 'Line Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  const po = query.data
  const supplier = suppliersQuery.data?.find((item) => item.id === po?.supplierId)
  const isReadonly = po ? ['ISSUED', 'CANCELLED', 'COMPLETED'].includes(po.status.toUpperCase()) : false

  return (
    <DashboardLayout
      description="Inspect supplier, outlet, line items, and workflow state for a purchase order."
      eyebrow="Procurement"
      title="Purchase Order Detail"
    >
      {query.isLoading ? (
        <Card title="Loading purchase order">
          <p className="muted-text">Loading purchase order header, lines, and workflow state...</p>
        </Card>
      ) : null}
      {query.error ? (
        <ErrorState
          actionLabel="Retry"
          message={query.error instanceof Error ? query.error.message : 'Failed to load purchase order detail'}
          onAction={() => void query.refetch()}
          title="Unable to load purchase order"
        />
      ) : null}
      {!query.isLoading && !query.error && !po ? (
        <EmptyState
          description="This purchase order does not exist or is no longer visible in the current scope."
          title="Purchase order not found"
        />
      ) : null}
      {po ? (
        <>
          {isReadonly ? (
            <ReadonlyBanner
              label="Terminal state"
              message="This purchase order is locked because it has reached a terminal workflow state."
              title="Purchase order is now read-only"
              tone="danger"
            />
          ) : po.status.toUpperCase() === 'DRAFT' ? (
            <ReadonlyBanner
              icon="edit_note"
              label="Draft workflow"
              message="Complete the remaining workflow actions to move this draft through approval and issue."
              title="Purchase order draft is in progress"
            />
          ) : null}

          <section className="surface-grid">
            <div className="surface-grid-main">
              <Card title={po.poNumber}>
                <div className="page-header">
                  <div className="cell-stack">
                    <span className="cell-subtitle">Created for outlet #{po.outletId}</span>
                    <div className="entity-header-title">
                      <strong>{po.poNumber}</strong>
                      <StatusBadge status={po.status} />
                    </div>
                  </div>
                  <div className="form-actions align-start">
                    {canSubmitPurchaseOrder(po.status) ? (
                      <Button
                        loading={actionMutation.isPending}
                        onClick={() => {
                          if (confirmAction('Submit this purchase order?')) {
                            void actionMutation.mutateAsync({ id: po.id, action: 'submit' })
                          }
                        }}
                        size="sm"
                      >
                        Submit
                      </Button>
                    ) : null}
                    {canApprovePurchaseOrder(po.status) ? (
                      <Button
                        loading={actionMutation.isPending}
                        onClick={() => void actionMutation.mutateAsync({ id: po.id, action: 'approve' })}
                        size="sm"
                      >
                        Approve
                      </Button>
                    ) : null}
                    {canIssuePurchaseOrder(po.status) ? (
                      <Button
                        loading={actionMutation.isPending}
                        onClick={() => void actionMutation.mutateAsync({ id: po.id, action: 'issue' })}
                        size="sm"
                      >
                        Issue
                      </Button>
                    ) : null}
                    {canCancelPurchaseOrder(po.status) ? (
                      <Button
                        loading={actionMutation.isPending}
                        onClick={() => {
                          if (confirmAction('Cancel this purchase order?')) {
                            void actionMutation.mutateAsync({ id: po.id, action: 'cancel' })
                          }
                        }}
                        size="sm"
                        variant="danger"
                      >
                        Cancel
                      </Button>
                    ) : null}
                    <Button asChild size="sm" variant="secondary">
                      <Link to={`/procurement/goods-receipts/new?purchaseOrderId=${po.id}`}>Create goods receipt</Link>
                    </Button>
                  </div>
                </div>
              </Card>

              <div className="workspace-split-grid">
                <Card title="Supplier Information">
                  <div className="key-value-list">
                    <div className="key-value-row">
                      <span>Approved supplier</span>
                      <strong>{supplier?.name ?? `Supplier #${po.supplierId}`}</strong>
                    </div>
                    <div className="key-value-row">
                      <span>Supplier code</span>
                      <strong>{supplier?.supplierCode ?? 'Supplier directory unavailable'}</strong>
                    </div>
                    <div className="key-value-row">
                      <span>Payment note</span>
                      <strong>{po.note ?? 'No supplier note attached'}</strong>
                    </div>
                  </div>
                </Card>

                <Card title="Outlet Information">
                  <div className="key-value-list">
                    <div className="key-value-row">
                      <span>Destination outlet</span>
                      <strong>Outlet #{po.outletId}</strong>
                    </div>
                    <div className="key-value-row">
                      <span>Region</span>
                      <strong>Region #{po.regionId}</strong>
                    </div>
                    <div className="key-value-row">
                      <span>Expected delivery</span>
                      <strong>{po.expectedDeliveryDate ?? 'Not scheduled'}</strong>
                    </div>
                    <div className="key-value-row">
                      <span>Order date</span>
                      <strong>{po.orderDate}</strong>
                    </div>
                  </div>
                </Card>
              </div>

              <Card title="Order Items">
                <DataTable
                  columns={columns}
                  emptyDescription="This purchase order does not contain any order lines."
                  emptyTitle="No order items"
                  rowKey={(row) => row.id}
                  rows={po.lines}
                />
              </Card>

              <Card title="Internal Notes & Delivery Instructions">
                <div className="detail-summary-note">{po.note || 'No internal notes or delivery instructions were recorded.'}</div>
              </Card>
            </div>

            <aside className="surface-grid-side">
              <Card className="detail-side-card" title="Summary">
                <div className="detail-side-list">
                  <div className="detail-side-row">
                    <span>Subtotal</span>
                    <strong>{formatAmount(po.subtotalAmount)}</strong>
                  </div>
                  <div className="detail-side-row">
                    <span>Tax</span>
                    <strong>{formatAmount(po.taxAmount)}</strong>
                  </div>
                  <div className="detail-side-row">
                    <span>Total</span>
                    <strong>{formatAmount(po.totalAmount)}</strong>
                  </div>
                  <div className="detail-side-row">
                    <span>Approved at</span>
                    <strong>{po.approvedAt ?? 'Pending approval'}</strong>
                  </div>
                  <div className="detail-side-row">
                    <span>Issued at</span>
                    <strong>{po.issuedAt ?? 'Not issued yet'}</strong>
                  </div>
                </div>
              </Card>

              {actionMutation.error ? (
                <PermissionDeniedInline
                  message={actionMutation.error instanceof Error ? actionMutation.error.message : 'Unable to update the purchase order workflow.'}
                  title="Workflow update failed"
                />
              ) : null}

              <Card className="detail-side-card" title="Workflow Notes">
                <div className="detail-side-list">
                  <div className="detail-side-row">
                    <span>Goods receipt handoff</span>
                    <strong>{isReadonly ? 'Available for follow-up only' : 'Create goods receipt from this PO'}</strong>
                  </div>
                  <div className="detail-side-row">
                    <span>Current mode</span>
                    <strong>{isReadonly ? 'Read-only' : 'Actionable'}</strong>
                  </div>
                </div>
              </Card>
            </aside>
          </section>
        </>
      ) : null}
    </DashboardLayout>
  )
}
