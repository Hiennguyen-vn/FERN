import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { DashboardLayout } from '@app/layouts/DashboardLayout'
import {
  Button,
  Card,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  Select,
} from '@design-system/index'
import type { SelectOption } from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { useCreateSupplierInvoice } from '../hooks/useSupplierInvoice'
import type { InvoiceLineType } from '../model/procurement.types'
import { canReviewInvoice } from '../services/procurementPermission.service'
import { createDefaultInvoiceLine } from '../services/procurementWorkflow.service'

const lineTypeOptions: SelectOption[] = [
  { label: 'Stock (PO-matched)', value: 'STOCK' },
  { label: 'Partial match', value: 'PARTIAL_MATCH' },
  { label: 'Non-PO receipt', value: 'NON_PO_RECEIPT' },
  { label: 'Non-stock', value: 'NON_STOCK' },
]

function toOptionalNumber(value: string): number | undefined {
  if (!value) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

export function SupplierInvoiceCreatePage() {
  usePageTitle('Create Supplier Invoice')

  const principal = usePrincipal()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const canCreate = canReviewInvoice(principal)
  const createMutation = useCreateSupplierInvoice()

  const [form, setForm] = useState({
    supplierId: searchParams.get('supplierId') ?? '',
    regionId: selectedRegionId ? String(selectedRegionId) : '',
    outletId: selectedOutletId ? String(selectedOutletId) : '',
    currencyCode: 'VND',
    invoiceNumber: '',
    invoiceDate: new Date().toISOString().slice(0, 10),
    dueDate: '',
    note: '',
  })
  const [lines, setLines] = useState([createDefaultInvoiceLine()])

  if (!canCreate) {
    return (
      <DashboardLayout description="Tạo supplier invoice để xử lý thanh toán nhà cung cấp." title="Create Supplier Invoice">
        <PermissionDeniedInline message="Bạn cần quyền procurement.invoice.review để tạo supplier invoice." />
      </DashboardLayout>
    )
  }

  function updateLine<K extends keyof ReturnType<typeof createDefaultInvoiceLine>>(
    index: number,
    key: K,
    value: ReturnType<typeof createDefaultInvoiceLine>[K],
  ) {
    setLines((current) =>
      current.map((line, i) => (i === index ? { ...line, [key]: value } : line)),
    )
  }

  async function handleSubmit() {
    await createMutation.mutateAsync({
      supplierId: Number(form.supplierId),
      regionId: Number(form.regionId),
      outletId: Number(form.outletId),
      currencyCode: form.currencyCode,
      invoiceNumber: form.invoiceNumber,
      invoiceDate: form.invoiceDate,
      dueDate: form.dueDate || null,
      note: form.note || null,
      lines: lines.map((line) => ({
        lineType: line.lineType as InvoiceLineType,
        goodsReceiptLineId: toOptionalNumber(line.goodsReceiptLineId),
        description: line.description || null,
        qtyInvoiced: toOptionalNumber(line.qtyInvoiced),
        unitPrice: toOptionalNumber(line.unitPrice),
        taxPercent: toOptionalNumber(line.taxPercent),
        taxAmount: toOptionalNumber(line.taxAmount),
        lineTotal: Number(line.lineTotal),
        note: line.note || null,
      })),
    })
    void navigate('/finance/supplier-invoices')
  }

  return (
    <DashboardLayout
      description="Tạo supplier invoice để ghi nhận công nợ nhà cung cấp."
      title="Create Supplier Invoice"
    >
      <FormSection title="Invoice header">
        <div className="field-grid">
          <Input
            label="Supplier ID"
            onChange={(e) => setForm((f) => ({ ...f, supplierId: e.target.value }))}
            required
            value={form.supplierId}
          />
          <Input
            label="Region ID"
            onChange={(e) => setForm((f) => ({ ...f, regionId: e.target.value }))}
            required
            value={form.regionId}
          />
          <Input
            label="Outlet ID"
            onChange={(e) => setForm((f) => ({ ...f, outletId: e.target.value }))}
            required
            value={form.outletId}
          />
          <Input
            label="Currency"
            onChange={(e) => setForm((f) => ({ ...f, currencyCode: e.target.value }))}
            required
            value={form.currencyCode}
          />
          <Input
            label="Invoice number"
            onChange={(e) => setForm((f) => ({ ...f, invoiceNumber: e.target.value }))}
            required
            value={form.invoiceNumber}
          />
          <Input
            label="Invoice date"
            onChange={(e) => setForm((f) => ({ ...f, invoiceDate: e.target.value }))}
            required
            type="date"
            value={form.invoiceDate}
          />
          <Input
            label="Due date"
            onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))}
            type="date"
            value={form.dueDate}
          />
          <Input
            label="Note"
            onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
            value={form.note}
          />
        </div>
      </FormSection>

      <FormSection
        actions={
          <Button
            onClick={() => setLines((l) => [...l, createDefaultInvoiceLine()])}
            size="sm"
            variant="secondary"
          >
            Add line
          </Button>
        }
        title="Invoice lines"
      >
        <div className="page-stack">
          {lines.map((line, index) => (
            <Card key={index} title={`Line ${index + 1}`}>
              <div className="field-grid" style={{ marginBottom: lines.length > 1 ? '0.5rem' : undefined }}>
                {lines.length > 1 ? (
                  <div style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                      onClick={() => setLines((l) => l.filter((_, i) => i !== index))}
                      size="sm"
                      variant="danger"
                    >
                      Remove line
                    </Button>
                  </div>
                ) : null}
                <Select
                  label="Line type"
                  onChange={(e) => updateLine(index, 'lineType', e.target.value as InvoiceLineType)}
                  options={lineTypeOptions}
                  required
                  value={line.lineType}
                />
                <Input
                  label="GR Line ID"
                  onChange={(e) => updateLine(index, 'goodsReceiptLineId', e.target.value)}
                  placeholder="Optional — for STOCK type"
                  value={line.goodsReceiptLineId}
                />
                <Input
                  label="Description"
                  onChange={(e) => updateLine(index, 'description', e.target.value)}
                  value={line.description}
                />
                <Input
                  label="Qty invoiced"
                  onChange={(e) => updateLine(index, 'qtyInvoiced', e.target.value)}
                  type="number"
                  value={line.qtyInvoiced}
                />
                <Input
                  label="Unit price"
                  onChange={(e) => updateLine(index, 'unitPrice', e.target.value)}
                  type="number"
                  value={line.unitPrice}
                />
                <Input
                  label="Tax %"
                  onChange={(e) => updateLine(index, 'taxPercent', e.target.value)}
                  type="number"
                  value={line.taxPercent}
                />
                <Input
                  label="Tax amount"
                  onChange={(e) => updateLine(index, 'taxAmount', e.target.value)}
                  type="number"
                  value={line.taxAmount}
                />
                <Input
                  label="Line total"
                  onChange={(e) => updateLine(index, 'lineTotal', e.target.value)}
                  required
                  type="number"
                  value={line.lineTotal}
                />
                <Input
                  label="Note"
                  onChange={(e) => updateLine(index, 'note', e.target.value)}
                  value={line.note}
                />
              </div>
            </Card>
          ))}
        </div>

        <FormActions
          primaryAction={
            <Button
              disabled={
                !form.supplierId ||
                !form.regionId ||
                !form.outletId ||
                !form.invoiceNumber ||
                lines.some((l) => !l.lineTotal)
              }
              loading={createMutation.isPending}
              onClick={() => void handleSubmit()}
            >
              Create invoice
            </Button>
          }
        />
        {createMutation.error ? (
          <ErrorState
            message={
              createMutation.error instanceof Error
                ? createMutation.error.message
                : 'Failed to create supplier invoice'
            }
            title="Không thể tạo supplier invoice"
          />
        ) : null}
      </FormSection>
    </DashboardLayout>
  )
}
