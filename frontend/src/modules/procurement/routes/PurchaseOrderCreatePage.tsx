import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import { Button, Card, EmptyState, ErrorState, FormActions, FormSection, Input, PermissionDeniedInline, ReadonlyBanner, Select } from '@design-system/index'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useCreatePurchaseOrder } from '../hooks/usePurchaseOrder'
import { useSuppliers } from '../hooks/useSuppliers'
import type { Supplier } from '../model/procurement.types'
import { createDefaultPurchaseOrderLine } from '../services/procurementWorkflow.service'
import { canCreatePurchaseOrder } from '../services/procurementPermission.service'
import { validatePurchaseOrderLines } from '../services/procurementValidation.service'
import { toOptionalNumber } from '@shared/validators/parseInput'

export function PurchaseOrderCreatePage() {
  usePageTitle('Purchase Order Create')

  const principal = usePrincipal()
  const navigate = useNavigate()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canCreate = canCreatePurchaseOrder(principal)

  const suppliersQuery = useSuppliers()
  const createMutation = useCreatePurchaseOrder()
  const [form, setForm] = useState({
    supplierId: '',
    orderDate: new Date().toISOString().slice(0, 10),
    expectedDeliveryDate: '',
    note: '',
  })
  const [lines, setLines] = useState([createDefaultPurchaseOrderLine()])
  const [lineValidationError, setLineValidationError] = useState<string | null>(null)

  const supplierOptions = useMemo(
    () =>
      (suppliersQuery.data ?? []).map((supplier: Supplier) => ({
        label: `${supplier.supplierCode} - ${supplier.name}`,
        value: String(supplier.id),
      })),
    [suppliersQuery.data],
  )

  if (!canCreate) {
    return (
      <DashboardLayout description="Tạo purchase order mới." title="Purchase Order Create">
        <PermissionDeniedInline message="Bạn cần quyền procurement.po.create để tạo purchase order." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      description="Create a purchase order against the live procurement service."
      title="Purchase Order Create"
    >
      {!selectedRegionId || !selectedOutletId ? (
        <ReadonlyBanner message="Chọn region và outlet ở app shell trước khi tạo purchase order mới." />
      ) : null}

      {suppliersQuery.isLoading ? (
        <Card title="Loading suppliers">
          <p className="muted-text">Loading supplier master data before allowing PO creation...</p>
        </Card>
      ) : null}

      {suppliersQuery.error ? (
        <ErrorState
          actionLabel="Retry"
          message={suppliersQuery.error instanceof Error ? suppliersQuery.error.message : 'Failed to load suppliers'}
          onAction={() => void suppliersQuery.refetch()}
          title="Không thể tải supplier list"
        />
      ) : null}

      {!suppliersQuery.isLoading && !suppliersQuery.error && supplierOptions.length === 0 ? (
        <EmptyState
          description="Procurement chưa có supplier nào khả dụng nên chưa thể tạo purchase order mới."
          title="No suppliers available"
        />
      ) : null}

      <FormSection title="Header">
        <div className="field-grid">
          <Input label="Region ID" readOnly value={selectedRegionId ?? ''} />
          <Input label="Outlet ID" readOnly value={selectedOutletId ?? ''} />
          <Select
            disabled={suppliersQuery.isLoading || supplierOptions.length === 0}
            label="Supplier"
            onChange={(event) => setForm((current) => ({ ...current, supplierId: event.target.value }))}
            options={supplierOptions}
            placeholder="Select supplier"
            value={form.supplierId}
          />
          <Input
            label="Order Date"
            onChange={(event) => setForm((current) => ({ ...current, orderDate: event.target.value }))}
            type="date"
            value={form.orderDate}
          />
          <Input
            label="Expected Delivery"
            onChange={(event) => setForm((current) => ({ ...current, expectedDeliveryDate: event.target.value }))}
            type="date"
            value={form.expectedDeliveryDate}
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
            onClick={() => setLines((current) => [...current, createDefaultPurchaseOrderLine()])}
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
                  label="Qty Ordered"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, qtyOrdered: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.qtyOrdered}
                />
                <Input
                  label="Expected Unit Price"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, expectedUnitPrice: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.expectedUnitPrice}
                />
                <Input
                  label="Tax Percent"
                  onChange={(event) =>
                    setLines((current) =>
                      current.map((item, itemIndex) =>
                        itemIndex === index ? { ...item, taxPercent: event.target.value } : item,
                      ),
                    )
                  }
                  value={line.taxPercent}
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
        {lineValidationError && (
          <p className="error-text" style={{ margin: '0 0 0.5rem' }}>{lineValidationError}</p>
        )}
        <FormActions
          primaryAction={
            <Button
              disabled={!selectedRegionId || !selectedOutletId || !form.supplierId || supplierOptions.length === 0}
              loading={createMutation.isPending}
              onClick={async () => {
                if (!selectedRegionId || !selectedOutletId) {
                  return
                }

                const lineError = validatePurchaseOrderLines(lines)
                if (lineError) {
                  setLineValidationError(lineError)
                  return
                }
                setLineValidationError(null)

                const purchaseOrder = await createMutation.mutateAsync({
                  regionId: selectedRegionId,
                  outletId: selectedOutletId,
                  supplierId: Number(form.supplierId),
                  orderDate: form.orderDate,
                  expectedDeliveryDate: form.expectedDeliveryDate || undefined,
                  note: form.note || undefined,
                  lines: lines.map((line) => ({
                    ingredientId: Number(line.ingredientId),
                    uomCode: line.uomCode,
                    qtyOrdered: Number(line.qtyOrdered),
                    expectedUnitPrice: toOptionalNumber(line.expectedUnitPrice),
                    taxPercent: toOptionalNumber(line.taxPercent),
                    note: line.note || undefined,
                  })),
                })

                void navigate(`/procurement/purchase-orders/${purchaseOrder.id}`)
              }}
            >
              Create purchase order
            </Button>
          }
        />
        {createMutation.error ? (
          <ErrorState
            message={createMutation.error instanceof Error ? createMutation.error.message : 'Failed to create purchase order'}
            title="Không thể tạo purchase order"
          />
        ) : null}
      </FormSection>
    </DashboardLayout>
  )
}
