import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
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
import { useCreateSupplierPayment } from '../hooks/useSupplierPayment'
import type { PaymentMethod } from '../model/procurement.types'
import { canRecordPayment } from '../services/procurementPermission.service'
import { createDefaultPaymentAllocation } from '../services/procurementWorkflow.service'
import { validateSupplierPaymentHeader, validatePaymentAllocations } from '../services/procurementValidation.service'

const paymentMethodOptions: SelectOption[] = [
  { label: 'Cash', value: 'CASH' },
  { label: 'Card', value: 'CARD' },
  { label: 'E-wallet', value: 'EWALLET' },
  { label: 'Bank transfer', value: 'BANK_TRANSFER' },
  { label: 'Cheque', value: 'CHEQUE' },
  { label: 'Voucher', value: 'VOUCHER' },
]

export function SupplierPaymentCreatePage() {
  usePageTitle('Record Supplier Payment')

  const principal = usePrincipal()
  const navigate = useNavigate()
  const canCreate = canRecordPayment(principal)
  const createMutation = useCreateSupplierPayment()

  const nowIso = new Date().toISOString().slice(0, 16)

  const [form, setForm] = useState({
    supplierId: '',
    currencyCode: 'VND',
    paymentMethod: 'BANK_TRANSFER' as PaymentMethod,
    amount: '',
    paymentTime: nowIso,
    transactionRef: '',
    note: '',
  })
  const [allocations, setAllocations] = useState([createDefaultPaymentAllocation()])
  const [validationError, setValidationError] = useState<string | null>(null)

  if (!canCreate) {
    return (
      <DashboardLayout description="Ghi nhận thanh toán cho nhà cung cấp." title="Record Supplier Payment">
        <PermissionDeniedInline message="Bạn cần quyền procurement.payment.record để ghi nhận thanh toán." />
      </DashboardLayout>
    )
  }

  function updateAllocation<K extends keyof ReturnType<typeof createDefaultPaymentAllocation>>(
    index: number,
    key: K,
    value: ReturnType<typeof createDefaultPaymentAllocation>[K],
  ) {
    setAllocations((current) =>
      current.map((alloc, i) => (i === index ? { ...alloc, [key]: value } : alloc)),
    )
  }

  const totalAllocated = allocations.reduce((sum, a) => sum + (Number(a.allocatedAmount) || 0), 0)
  const paymentAmount = Number(form.amount) || 0
  const allocationMismatch = paymentAmount > 0 && Math.abs(totalAllocated - paymentAmount) > 0.001

  async function handleSubmit() {
    const headerError = validateSupplierPaymentHeader(form)
    if (headerError) { setValidationError(headerError); return }
    const allocError = validatePaymentAllocations(allocations)
    if (allocError) { setValidationError(allocError); return }
    setValidationError(null)

    await createMutation.mutateAsync({
      supplierId: Number(form.supplierId),
      currencyCode: form.currencyCode,
      paymentMethod: form.paymentMethod,
      amount: Number(form.amount),
      paymentTime: new Date(form.paymentTime).toISOString(),
      transactionRef: form.transactionRef || null,
      note: form.note || null,
      invoiceAllocations: allocations.map((a) => ({
        supplierInvoiceId: Number(a.supplierInvoiceId),
        allocatedAmount: Number(a.allocatedAmount),
        note: a.note || null,
      })),
    })
    void navigate('/finance/supplier-payments')
  }

  return (
    <DashboardLayout
      description="Ghi nhận thanh toán nhà cung cấp và phân bổ vào các supplier invoices."
      title="Record Supplier Payment"
    >
      <FormSection title="Payment header">
        <div className="field-grid">
          <Input
            label="Supplier ID"
            onChange={(e) => setForm((f) => ({ ...f, supplierId: e.target.value }))}
            required
            value={form.supplierId}
          />
          <Input
            label="Currency"
            onChange={(e) => setForm((f) => ({ ...f, currencyCode: e.target.value }))}
            required
            value={form.currencyCode}
          />
          <Select
            label="Payment method"
            onChange={(e) => setForm((f) => ({ ...f, paymentMethod: e.target.value as PaymentMethod }))}
            options={paymentMethodOptions}
            required
            value={form.paymentMethod}
          />
          <Input
            label="Amount"
            onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
            required
            type="number"
            value={form.amount}
          />
          <Input
            label="Payment time"
            onChange={(e) => setForm((f) => ({ ...f, paymentTime: e.target.value }))}
            required
            type="datetime-local"
            value={form.paymentTime}
          />
          <Input
            label="Transaction ref"
            onChange={(e) => setForm((f) => ({ ...f, transactionRef: e.target.value }))}
            placeholder="Optional"
            value={form.transactionRef}
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
            onClick={() => setAllocations((a) => [...a, createDefaultPaymentAllocation()])}
            size="sm"
            variant="secondary"
          >
            Add allocation
          </Button>
        }
        title="Invoice allocations"
      >
        {allocationMismatch ? (
          <div className="inline-banner inline-banner-warning" role="alert">
            Tổng phân bổ ({totalAllocated.toLocaleString('vi-VN')}) chưa khớp với số tiền thanh toán ({paymentAmount.toLocaleString('vi-VN')}).
          </div>
        ) : null}
        <div className="page-stack">
          {allocations.map((alloc, index) => (
            <Card key={index} title={`Allocation ${index + 1}`}>
              <div className="field-grid">
                {allocations.length > 1 ? (
                  <div style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                      onClick={() => setAllocations((a) => a.filter((_, i) => i !== index))}
                      size="sm"
                      variant="danger"
                    >
                      Remove
                    </Button>
                  </div>
                ) : null}
                <Input
                  label="Invoice ID"
                  onChange={(e) => updateAllocation(index, 'supplierInvoiceId', e.target.value)}
                  required
                  value={alloc.supplierInvoiceId}
                />
                <Input
                  label="Allocated amount"
                  onChange={(e) => updateAllocation(index, 'allocatedAmount', e.target.value)}
                  required
                  type="number"
                  value={alloc.allocatedAmount}
                />
                <Input
                  label="Note"
                  onChange={(e) => updateAllocation(index, 'note', e.target.value)}
                  value={alloc.note}
                />
              </div>
            </Card>
          ))}
        </div>

        {validationError && (
          <p className="error-text" style={{ margin: '0 0 0.5rem' }}>{validationError}</p>
        )}
        <FormActions
          primaryAction={
            <Button
              disabled={
                !form.supplierId ||
                !form.amount ||
                allocations.some((a) => !a.supplierInvoiceId || !a.allocatedAmount)
              }
              loading={createMutation.isPending}
              onClick={() => void handleSubmit()}
            >
              Record payment
            </Button>
          }
        />
        {createMutation.error ? (
          <ErrorState
            message={
              createMutation.error instanceof Error
                ? createMutation.error.message
                : 'Failed to record supplier payment'
            }
            title="Không thể ghi nhận thanh toán"
          />
        ) : null}
      </FormSection>
    </DashboardLayout>
  )
}
