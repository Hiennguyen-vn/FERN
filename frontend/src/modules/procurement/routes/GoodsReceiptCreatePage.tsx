import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import { Button, Card, EmptyState, ErrorState, FormActions, FormSection, Input, PermissionDeniedInline } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useCreateGoodsReceipt } from '../hooks/useGoodsReceipt'
import { usePurchaseOrder } from '../hooks/usePurchaseOrder'
import { createDefaultGoodsReceiptLine } from '../services/procurementWorkflow.service'
import { canCreateGoodsReceipt } from '../services/procurementPermission.service'

function toOptionalNumber(value: string): number | undefined {
  if (!value) {
    return undefined
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

export function GoodsReceiptCreatePage() {
  usePageTitle('Goods Receipt Create')

  const principal = usePrincipal()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const canCreate = canCreateGoodsReceipt(principal)

  const initialPurchaseOrderId = searchParams.get('purchaseOrderId') ?? ''
  const [form, setForm] = useState({
    purchaseOrderId: initialPurchaseOrderId,
    receiptTime: new Date().toISOString().slice(0, 16),
    businessDate: new Date().toISOString().slice(0, 10),
    supplierLotNumber: '',
    note: '',
  })
  const [lines, setLines] = useState([createDefaultGoodsReceiptLine()])
  const purchaseOrderQuery = usePurchaseOrder(canCreate ? (toOptionalNumber(form.purchaseOrderId) ?? null) : null)
  const createMutation = useCreateGoodsReceipt()

  useEffect(() => {
    if (!purchaseOrderQuery.data) {
      return
    }

    setLines(
      purchaseOrderQuery.data.lines.map((line) => ({
        purchaseOrderLineId: String(line.id),
        ingredientId: String(line.ingredientId),
        uomCode: line.uomCode,
        qtyReceived: line.qtyOrdered,
        unitCost: line.expectedUnitPrice ?? '',
        note: line.note ?? '',
      })),
    )
  }, [purchaseOrderQuery.data])

  if (!canCreate) {
    return (
      <DashboardLayout description="Tạo goods receipt cho purchase order." title="Goods Receipt Create">
        <PermissionDeniedInline message="Bạn cần quyền procurement.gr.create để tạo goods receipt." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      description="Create a goods receipt for an existing purchase order."
      title="Goods Receipt Create"
    >
      {!form.purchaseOrderId ? (
        <EmptyState
          description="Nhập Purchase Order ID để prefill các line nhận hàng từ purchase order hiện có."
          title="Purchase order required"
        />
      ) : null}

      {purchaseOrderQuery.isLoading ? (
        <Card title="Loading purchase order">
          <p className="muted-text">Loading purchase order lines to prefill goods receipt...</p>
        </Card>
      ) : null}

      {purchaseOrderQuery.error ? (
        <ErrorState
          actionLabel="Retry"
          message={purchaseOrderQuery.error instanceof Error ? purchaseOrderQuery.error.message : 'Failed to load purchase order'}
          onAction={() => void purchaseOrderQuery.refetch()}
          title="Không thể tải purchase order"
        />
      ) : null}

      <FormSection title="Header">
        <div className="field-grid">
          <Input
            label="Purchase Order ID"
            onChange={(event) => setForm((current) => ({ ...current, purchaseOrderId: event.target.value }))}
            value={form.purchaseOrderId}
          />
          <Input
            label="Receipt Time"
            onChange={(event) => setForm((current) => ({ ...current, receiptTime: event.target.value }))}
            type="datetime-local"
            value={form.receiptTime}
          />
          <Input
            label="Business Date"
            onChange={(event) => setForm((current) => ({ ...current, businessDate: event.target.value }))}
            type="date"
            value={form.businessDate}
          />
          <Input
            label="Supplier Lot Number"
            onChange={(event) => setForm((current) => ({ ...current, supplierLotNumber: event.target.value }))}
            value={form.supplierLotNumber}
          />
          <Input
            label="Note"
            onChange={(event) => setForm((current) => ({ ...current, note: event.target.value }))}
            value={form.note}
          />
        </div>
      </FormSection>

      <FormSection
        actions={
          <Button
            onClick={() => setLines((current) => [...current, createDefaultGoodsReceiptLine()])}
            size="sm"
            variant="secondary"
          >
            Add line
          </Button>
        }
        title="Lines"
      >
        <div className="page-stack">
          {lines.map((line, index) => (
            <Card key={index} title={`Line ${index + 1}`}>
              <div className="field-grid">
                <Input
                  label="PO Line ID"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, purchaseOrderLineId: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.purchaseOrderLineId}
                />
                <Input
                  label="Ingredient ID"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, ingredientId: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.ingredientId}
                />
                <Input
                  label="UOM"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, uomCode: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.uomCode}
                />
                <Input
                  label="Qty Received"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, qtyReceived: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.qtyReceived}
                />
                <Input
                  label="Unit Cost"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, unitCost: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.unitCost}
                />
                <Input
                  label="Note"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, note: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.note}
                />
              </div>
            </Card>
          ))}
        </div>
        <FormActions
          primaryAction={
            <Button
              disabled={!form.purchaseOrderId}
              loading={createMutation.isPending}
              onClick={async () => {
                const goodsReceipt = await createMutation.mutateAsync({
                  purchaseOrderId: Number(form.purchaseOrderId),
                  receiptTime: new Date(form.receiptTime).toISOString(),
                  businessDate: form.businessDate,
                  supplierLotNumber: form.supplierLotNumber || undefined,
                  note: form.note || undefined,
                  lines: lines.map((line) => ({
                    purchaseOrderLineId: toOptionalNumber(line.purchaseOrderLineId),
                    ingredientId: Number(line.ingredientId),
                    uomCode: line.uomCode,
                    qtyReceived: Number(line.qtyReceived),
                    unitCost: Number(line.unitCost),
                    note: line.note || undefined,
                  })),
                })

                void navigate(`/procurement/goods-receipts/${goodsReceipt.id}`)
              }}
            >
              Create goods receipt
            </Button>
          }
        />
        {createMutation.error ? (
          <ErrorState
            message={createMutation.error instanceof Error ? createMutation.error.message : 'Failed to create goods receipt'}
            title="Không thể tạo goods receipt"
          />
        ) : null}
      </FormSection>
    </DashboardLayout>
  )
}
