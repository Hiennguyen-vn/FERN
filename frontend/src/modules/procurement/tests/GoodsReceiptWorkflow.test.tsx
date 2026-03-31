import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { GoodsReceiptCreatePage } from '../routes/GoodsReceiptCreatePage'
import { GoodsReceiptDetailPage } from '../routes/GoodsReceiptDetailPage'
import type { CreateGoodsReceiptPayload, GoodsReceipt, PurchaseOrder } from '../model/procurement.types'

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
    id: overrides.id ?? 410,
    poNumber: overrides.poNumber ?? 'PO-410',
    regionId: overrides.regionId ?? 1,
    outletId: overrides.outletId ?? 101,
    supplierId: overrides.supplierId ?? 20,
    orderDate: overrides.orderDate ?? '2026-03-30',
    expectedDeliveryDate: overrides.expectedDeliveryDate ?? '2026-03-31',
    status: overrides.status ?? 'ISSUED',
    subtotalAmount: overrides.subtotalAmount ?? '200000',
    taxAmount: overrides.taxAmount ?? '0',
    totalAmount: overrides.totalAmount ?? '200000',
    note: overrides.note ?? 'Issue for receiving test',
    approvedAt: overrides.approvedAt ?? null,
    issuedAt: overrides.issuedAt ?? '2026-03-30T08:00:00.000Z',
    lines: overrides.lines ?? [
      {
        id: 11,
        lineNumber: 1,
        ingredientId: 9001,
        uomCode: 'KG',
        qtyOrdered: '5',
        qtyReceived: '0',
        expectedUnitPrice: '40000',
        taxPercent: '0',
        status: 'ISSUED',
        note: 'Issued line',
      },
    ],
  }
}

function createGoodsReceipt(overrides: Partial<GoodsReceipt> = {}): GoodsReceipt {
  return {
    id: overrides.id ?? 510,
    receiptNumber: overrides.receiptNumber ?? 'GR-510',
    purchaseOrderId: overrides.purchaseOrderId ?? 410,
    regionId: overrides.regionId ?? 1,
    outletId: overrides.outletId ?? 101,
    supplierId: overrides.supplierId ?? 20,
    receiptTime: overrides.receiptTime ?? '2026-03-30T10:00:00.000Z',
    businessDate: overrides.businessDate ?? '2026-03-30',
    status: overrides.status ?? 'DRAFT',
    totalAmount: overrides.totalAmount ?? '200000',
    supplierLotNumber: overrides.supplierLotNumber ?? 'LOT-01',
    note: overrides.note ?? 'GR note',
    receivedAt: overrides.receivedAt ?? null,
    postedAt: overrides.postedAt ?? null,
    lines: overrides.lines ?? [
      {
        id: 21,
        purchaseOrderLineId: 11,
        ingredientId: 9001,
        uomCode: 'KG',
        qtyReceived: '5',
        unitCost: '40000',
        lineTotal: '200000',
        note: 'Received in full',
      },
    ],
  }
}

describe('goods receipt workflows', () => {
  let purchaseOrderState: PurchaseOrder
  let goodsReceiptState: GoodsReceipt

  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({ principal: { permissions: ['procurement.po.read', 'procurement.gr.read', 'procurement.gr.create'] } })
    purchaseOrderState = createPurchaseOrder()
    goodsReceiptState = createGoodsReceipt()

    procurementApi.getPurchaseOrder.mockImplementation(async () => clone(purchaseOrderState))
    procurementApi.createGoodsReceipt.mockImplementation(async (payload: CreateGoodsReceiptPayload) => {
      goodsReceiptState = createGoodsReceipt({
        id: 510,
        purchaseOrderId: payload.purchaseOrderId,
        receiptTime: payload.receiptTime,
        businessDate: payload.businessDate,
        supplierLotNumber: payload.supplierLotNumber ?? null,
        note: payload.note ?? null,
        lines: payload.lines.map((line: CreateGoodsReceiptPayload['lines'][number], index: number) => ({
          id: index + 21,
          purchaseOrderLineId: line.purchaseOrderLineId ?? null,
          ingredientId: line.ingredientId,
          uomCode: line.uomCode,
          qtyReceived: String(line.qtyReceived),
          unitCost: String(line.unitCost),
          lineTotal: String(line.qtyReceived * line.unitCost),
          note: line.note ?? null,
        })),
      })

      return clone(goodsReceiptState)
    })
    procurementApi.getGoodsReceipt.mockImplementation(async () => clone(goodsReceiptState))
    procurementApi.goodsReceiptAction.mockImplementation(async (_id, action) => {
      goodsReceiptState = {
        ...goodsReceiptState,
        status: action === 'receive' ? 'RECEIVED' : action === 'post' ? 'POSTED' : 'CANCELLED',
        receivedAt: action === 'receive' ? '2026-03-30T10:10:00.000Z' : goodsReceiptState.receivedAt,
        postedAt: action === 'post' ? '2026-03-30T10:20:00.000Z' : goodsReceiptState.postedAt,
      }

      return clone(goodsReceiptState)
    })
  })

  it('creates a goods receipt, receives it, and posts it', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderWithProviders(
      <Routes>
        <Route path="/procurement/goods-receipts/new" element={<GoodsReceiptCreatePage />} />
        <Route path="/procurement/goods-receipts/:goodsReceiptId" element={<GoodsReceiptDetailPage />} />
      </Routes>,
      { route: '/procurement/goods-receipts/new?purchaseOrderId=410' },
    )

    expect(await screen.findByDisplayValue('410')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create goods receipt' }))

    expect(await screen.findByRole('heading', { name: 'GR-510' })).toBeInTheDocument()
    expect(procurementApi.createGoodsReceipt).toHaveBeenCalledWith(
      expect.objectContaining({
        purchaseOrderId: 410,
      }),
    )

    await user.click(screen.getByRole('button', { name: 'Receive' }))

    await waitFor(() => {
      expect(procurementApi.goodsReceiptAction).toHaveBeenCalledWith(510, 'receive')
    })
    expect(await screen.findByText('RECEIVED')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Post' }))

    await waitFor(() => {
      expect(procurementApi.goodsReceiptAction).toHaveBeenCalledWith(510, 'post')
    })
    expect(await screen.findByText('POSTED')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Post' })).not.toBeInTheDocument()
  })
})
