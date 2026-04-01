import { useState } from 'react'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Badge, Card, EmptyState, ErrorState, Input, PermissionDeniedInline } from '@design-system/index'
import { DataTable } from '@design-system/tables/DataTable'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useAuthStore } from '@core/auth/auth.store'
import { usePurchaseOrder } from '../hooks/usePurchaseOrder'
import { useGoodsReceipts } from '../hooks/useGoodsReceipt'
import { useSupplierInvoices } from '../hooks/useSupplierInvoice'
import { canReadPurchaseOrders } from '../services/procurementPermission.service'

interface MatchedLine {
  ingredientId: number
  poQty: string
  grQty: string
  invoiceQty: string
  poPrice: string | null
  grCost: string
  invoicePrice: string | null
  status: 'matched' | 'partial' | 'mismatch' | 'missing'
}

function computeMatchStatus(poQty: string, grQty: string, invoiceQty: string): MatchedLine['status'] {
  if (!grQty || grQty === '0') return 'missing'
  if (!invoiceQty || invoiceQty === '0') return 'missing'
  if (poQty === grQty && grQty === invoiceQty) return 'matched'
  if (poQty === grQty || grQty === invoiceQty) return 'partial'
  return 'mismatch'
}

function statusTone(status: MatchedLine['status']): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'matched': return 'success'
    case 'partial': return 'warning'
    case 'mismatch': return 'danger'
    case 'missing': return 'neutral'
  }
}

export function ThreeWayMatchingPage() {
  usePageTitle('Three-Way Matching')
  const principal = useAuthStore((state) => state.principal)
  const canRead = canReadPurchaseOrders(principal)

  const [poId, setPoId] = useState('')
  const poIdNum = poId ? Number(poId) : null

  const poQuery = usePurchaseOrder(canRead && poIdNum && Number.isFinite(poIdNum) ? poIdNum : null)
  const grQuery = useGoodsReceipts(canRead ? { purchaseOrderId: poIdNum ?? undefined } : undefined)
  const invoiceQuery = useSupplierInvoices(canRead ? {} : undefined)

  if (!canRead) {
    return (
      <DashboardLayout description="Three-way matching of PO, GR, and Invoice." title="Three-Way Matching">
        <PermissionDeniedInline message="You need procurement.po.read permission." />
      </DashboardLayout>
    )
  }

  // Build matched lines when data is available
  const matchedLines: MatchedLine[] = []
  if (poQuery.data && grQuery.data) {
    const po = poQuery.data
    const grs = grQuery.data
    
    // Group GR lines by ingredientId
    const grByIngredient = new Map<number, { qty: number; cost: number }>()
    for (const gr of grs) {
      for (const line of gr.lines) {
        const prev = grByIngredient.get(line.ingredientId) ?? { qty: 0, cost: 0 }
        grByIngredient.set(line.ingredientId, {
          qty: prev.qty + Number(line.qtyReceived),
          cost: Number(line.unitCost),
        })
      }
    }

    // Group invoice lines by GR line → ingredient
    const invoiceByIngredient = new Map<number, { qty: number; price: number }>()
    if (invoiceQuery.data) {
      for (const inv of invoiceQuery.data) {
        if (inv.supplierId !== po.supplierId) continue
        for (const line of inv.lines) {
          if (line.goodsReceiptLineId && line.qtyInvoiced) {
            // Find the ingredient via GR
            for (const gr of grs) {
              const grLine = gr.lines.find((l) => l.id === line.goodsReceiptLineId)
              if (grLine) {
                const prev = invoiceByIngredient.get(grLine.ingredientId) ?? { qty: 0, price: 0 }
                invoiceByIngredient.set(grLine.ingredientId, {
                  qty: prev.qty + Number(line.qtyInvoiced),
                  price: Number(line.unitPrice ?? 0),
                })
              }
            }
          }
        }
      }
    }

    for (const poLine of po.lines) {
      const gr = grByIngredient.get(poLine.ingredientId)
      const inv = invoiceByIngredient.get(poLine.ingredientId)
      const grQty = gr ? String(gr.qty) : '0'
      const invoiceQty = inv ? String(inv.qty) : '0'

      matchedLines.push({
        ingredientId: poLine.ingredientId,
        poQty: poLine.qtyOrdered,
        grQty,
        invoiceQty,
        poPrice: poLine.expectedUnitPrice,
        grCost: gr ? String(gr.cost) : '0',
        invoicePrice: inv ? String(inv.price) : null,
        status: computeMatchStatus(poLine.qtyOrdered, grQty, invoiceQty),
      })
    }
  }

  const matchedCount = matchedLines.filter((l) => l.status === 'matched').length
  const mismatchCount = matchedLines.filter((l) => l.status === 'mismatch').length

  return (
    <DashboardLayout
      description="Compare Purchase Order, Goods Receipt, and Supplier Invoice line by line."
      title="Three-Way Matching (PO ↔ GR ↔ Invoice)"
    >
      <div className="page-stack">
        <Card title="Select Purchase Order">
          <div className="field-grid">
            <Input
              label="Purchase Order ID"
              onChange={(e) => setPoId(e.target.value)}
              type="number"
              value={poId}
            />
          </div>
        </Card>

        {!poId && (
          <EmptyState
            description="Enter a Purchase Order ID to begin three-way matching."
            title="Enter PO ID"
          />
        )}

        {poQuery.isLoading && <Card title="Loading"><p className="muted-text">Loading PO data...</p></Card>}
        {poQuery.error && (
          <ErrorState
            actionLabel="Retry"
            message={poQuery.error instanceof Error ? poQuery.error.message : 'Failed to load PO'}
            onAction={() => void poQuery.refetch()}
            title="Error loading PO"
          />
        )}

        {poQuery.data && (
          <Card title={`PO: ${poQuery.data.poNumber} — Supplier #${poQuery.data.supplierId}`}>
            <div className="field-grid">
              <div><strong>Status:</strong> <Badge tone={poQuery.data.status === 'ISSUED' ? 'success' : 'neutral'}>{poQuery.data.status}</Badge></div>
              <div><strong>Order Date:</strong> {poQuery.data.orderDate}</div>
              <div><strong>Total:</strong> {poQuery.data.totalAmount}</div>
              <div><strong>GRs found:</strong> {grQuery.data?.length ?? 0}</div>
            </div>
          </Card>
        )}

        {matchedLines.length > 0 && (
          <>
            <Card title="Match Summary">
              <div className="field-grid">
                <div><Badge tone="success">✓ Matched: {matchedCount}</Badge></div>
                <div><Badge tone="warning">⚠ Partial: {matchedLines.filter((l) => l.status === 'partial').length}</Badge></div>
                <div><Badge tone="danger">✗ Mismatch: {mismatchCount}</Badge></div>
                <div><Badge tone="neutral">? Missing: {matchedLines.filter((l) => l.status === 'missing').length}</Badge></div>
              </div>
            </Card>

            <DataTable<MatchedLine>
              columns={[
                { key: 'ingredientId', header: 'Ingredient', render: (row) => <>#{row.ingredientId}</> },
                { key: 'poQty', header: 'PO Qty', render: (row) => <>{row.poQty}</> },
                { key: 'grQty', header: 'GR Qty', render: (row) => <>{row.grQty}</> },
                { key: 'invoiceQty', header: 'Invoice Qty', render: (row) => <>{row.invoiceQty}</> },
                { key: 'poPrice', header: 'PO Price', render: (row) => <>{row.poPrice ?? '—'}</> },
                { key: 'grCost', header: 'GR Cost', render: (row) => <>{row.grCost}</> },
                { key: 'invoicePrice', header: 'Invoice Price', render: (row) => <>{row.invoicePrice ?? '—'}</> },
                {
                  key: 'status',
                  header: 'Match',
                  render: (row) => <Badge tone={statusTone(row.status)}>{row.status.toUpperCase()}</Badge>,
                },
              ]}
              rowKey={(row, index) => `${row.ingredientId}-${index}`}
              rows={matchedLines}
            />
          </>
        )}
      </div>
    </DashboardLayout>
  )
}
