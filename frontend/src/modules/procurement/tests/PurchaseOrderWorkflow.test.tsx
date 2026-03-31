import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PurchaseOrderCreatePage } from '../routes/PurchaseOrderCreatePage'
import { PurchaseOrderDetailPage } from '../routes/PurchaseOrderDetailPage'
import type { CreatePurchaseOrderPayload, PurchaseOrder, Supplier } from '../model/procurement.types'

const procurementApi = vi.hoisted(() => ({
  createGoodsReceipt: vi.fn(),
  createPurchaseOrder: vi.fn(),
  getGoodsReceipt: vi.fn(),
  getPurchaseOrder: vi.fn(),
  goodsReceiptAction: vi.fn(),
  listSuppliers: vi.fn(),
  purchaseOrderAction: vi.fn(),
}))

vi.mock('../api/procurement.api', () => procurementApi)

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function createPurchaseOrder(overrides: Partial<PurchaseOrder> = {}): PurchaseOrder {
  return {
    id: overrides.id ?? 310,
    poNumber: overrides.poNumber ?? 'PO-310',
    regionId: overrides.regionId ?? 1,
    outletId: overrides.outletId ?? 101,
    supplierId: overrides.supplierId ?? 20,
    orderDate: overrides.orderDate ?? '2026-03-30',
    expectedDeliveryDate: overrides.expectedDeliveryDate ?? '2026-03-31',
    status: overrides.status ?? 'DRAFT',
    subtotalAmount: overrides.subtotalAmount ?? '200000',
    taxAmount: overrides.taxAmount ?? '0',
    totalAmount: overrides.totalAmount ?? '200000',
    note: overrides.note ?? 'Need restock for tea base',
    approvedAt: overrides.approvedAt ?? null,
    issuedAt: overrides.issuedAt ?? null,
    lines: overrides.lines ?? [
      {
        id: 1,
        lineNumber: 1,
        ingredientId: 9001,
        uomCode: 'KG',
        qtyOrdered: '5',
        qtyReceived: '0',
        expectedUnitPrice: '40000',
        taxPercent: '0',
        status: 'DRAFT',
        note: 'Urgent line',
      },
    ],
  }
}

describe('purchase order workflows', () => {
  let purchaseOrderState: PurchaseOrder

  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: ['procurement.po.read', 'procurement.po.create'] } })
    purchaseOrderState = createPurchaseOrder()

    const suppliers: Supplier[] = [
      {
        id: 20,
        supplierCode: 'SUP-20',
        name: 'Fresh Supply Co',
        taxCode: null,
        email: null,
        phone: null,
        address: null,
        defaultRegionId: 1,
        status: 'ACTIVE',
        approvedAt: null,
      },
    ]

    procurementApi.listSuppliers.mockResolvedValue(clone(suppliers))
    procurementApi.createPurchaseOrder.mockImplementation(async (payload: CreatePurchaseOrderPayload) => {
      purchaseOrderState = createPurchaseOrder({
        id: 310,
        poNumber: 'PO-310',
        regionId: payload.regionId,
        outletId: payload.outletId,
        supplierId: payload.supplierId,
        orderDate: payload.orderDate,
        expectedDeliveryDate: payload.expectedDeliveryDate ?? null,
        note: payload.note ?? null,
        lines: payload.lines.map((line: CreatePurchaseOrderPayload['lines'][number], index: number) => ({
          id: index + 1,
          lineNumber: index + 1,
          ingredientId: line.ingredientId,
          uomCode: line.uomCode,
          qtyOrdered: String(line.qtyOrdered),
          qtyReceived: '0',
          expectedUnitPrice: String(line.expectedUnitPrice ?? ''),
          taxPercent: String(line.taxPercent ?? ''),
          status: 'DRAFT',
          note: line.note ?? null,
        })),
      })

      return clone(purchaseOrderState)
    })
    procurementApi.getPurchaseOrder.mockImplementation(async () => clone(purchaseOrderState))
    procurementApi.purchaseOrderAction.mockImplementation(async (_id, action) => {
      purchaseOrderState = {
        ...purchaseOrderState,
        status:
          action === 'submit'
            ? 'SUBMITTED'
            : action === 'approve'
              ? 'APPROVED'
              : action === 'issue'
                ? 'ISSUED'
                : 'CANCELLED',
      }

      return clone(purchaseOrderState)
    })
  })

  it('creates a purchase order, submits it, and updates detail action state', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderWithProviders(
      <Routes>
        <Route path="/procurement/purchase-orders/new" element={<PurchaseOrderCreatePage />} />
        <Route path="/procurement/purchase-orders/:purchaseOrderId" element={<PurchaseOrderDetailPage />} />
      </Routes>,
      { route: '/procurement/purchase-orders/new' },
    )

    expect(await screen.findByRole('option', { name: 'SUP-20 - Fresh Supply Co' })).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Supplier'), '20')
    await user.clear(screen.getByLabelText('Ingredient ID'))
    await user.type(screen.getByLabelText('Ingredient ID'), '9001')
    await user.clear(screen.getByLabelText('Qty Ordered'))
    await user.type(screen.getByLabelText('Qty Ordered'), '5')
    await user.click(screen.getByRole('button', { name: 'Create purchase order' }))

    expect(await screen.findByRole('heading', { name: 'PO-310' })).toBeInTheDocument()
    expect(procurementApi.createPurchaseOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        outletId: 101,
        regionId: 1,
        supplierId: 20,
      }),
    )

    await user.click(screen.getByRole('button', { name: 'Submit' }))

    await waitFor(() => {
      expect(procurementApi.purchaseOrderAction).toHaveBeenCalledWith(310, 'submit')
    })
    expect(await screen.findByText('SUBMITTED')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument()
  })
})
