import { Routes, Route } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PosHomePage } from '../routes/PosHomePage'
import { PosOrderDetailPage } from '../routes/PosOrderDetailPage'
import { useCartStore } from '../state/cart.store'
import { usePosUiStore } from '../state/posUi.store'
import type { CreateSaleOrderPayload, PosSession, SaleOrder, UpdateSaleOrderPayload } from '../model/pos.types'

const posApi = vi.hoisted(() => ({
  addSalePayment: vi.fn(),
  cancelSaleOrder: vi.fn(),
  closePosSession: vi.fn(),
  completeSaleOrder: vi.fn(),
  createSaleOrder: vi.fn(),
  getPosSession: vi.fn(),
  getProductAvailability: vi.fn(),
  getProductPrices: vi.fn(),
  getProducts: vi.fn(),
  getSaleOrder: vi.fn(),
  listPosSessions: vi.fn(),
  openPosSession: vi.fn(),
  reconcilePosSession: vi.fn(),
  updateSaleOrder: vi.fn(),
}))

vi.mock('../api/pos.api', () => posApi)

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function createSession(overrides: Partial<PosSession> = {}): PosSession {
  return {
    id: overrides.id ?? 501,
    sessionCode: overrides.sessionCode ?? 'POS-501',
    regionId: overrides.regionId ?? 1,
    outletId: overrides.outletId ?? 101,
    terminalId: overrides.terminalId ?? null,
    currencyCode: overrides.currencyCode ?? 'VND',
    cashierUserId: overrides.cashierUserId ?? 11,
    managerUserId: overrides.managerUserId ?? 12,
    businessDate: overrides.businessDate ?? '2026-03-30',
    status: overrides.status ?? 'OPEN',
    note: overrides.note ?? null,
    openedAt: overrides.openedAt ?? '2026-03-30T09:00:00.000Z',
    closedAt: overrides.closedAt ?? null,
    reconciledAt: overrides.reconciledAt ?? null,
    expectedCashAmount: overrides.expectedCashAmount ?? '0',
    countedCashAmount: overrides.countedCashAmount ?? '0',
    discrepancyAmount: overrides.discrepancyAmount ?? '0',
  }
}

function createOrder(overrides: Partial<SaleOrder> = {}): SaleOrder {
  return {
    id: overrides.id ?? 900,
    orderNumber: overrides.orderNumber ?? 'SO-900',
    regionId: overrides.regionId ?? 1,
    outletId: overrides.outletId ?? 101,
    posSessionId: overrides.posSessionId ?? 501,
    currencyCode: overrides.currencyCode ?? 'VND',
    orderType: overrides.orderType ?? 'DINE_IN',
    status: overrides.status ?? 'OPEN',
    paymentStatus: overrides.paymentStatus ?? 'UNPAID',
    subtotal: overrides.subtotal ?? '150000',
    discountAmount: overrides.discountAmount ?? '0',
    taxAmount: overrides.taxAmount ?? '0',
    totalAmount: overrides.totalAmount ?? '150000',
    note: overrides.note ?? 'Front counter order',
    createdAt: overrides.createdAt ?? '2026-03-30T09:15:00.000Z',
    completedAt: overrides.completedAt ?? null,
    lines: overrides.lines ?? [
      {
        lineNumber: 1,
        productId: 301,
        productCode: 'ICED-TEA',
        productNameSnapshot: 'Iced Tea',
        unitPrice: '150000',
        qty: '1',
        discountAmount: '0',
        taxAmount: '0',
        lineTotal: '150000',
        note: null,
      },
    ],
    payments: overrides.payments ?? [],
  }
}

describe('POS order workflows', () => {
  let sessionState: PosSession
  let orderState: SaleOrder
  let nextPaymentId: number

  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    useCartStore.getState().clearAllDrafts()
    usePosUiStore.setState({
      businessDates: {},
      categoryFilters: {},
      orderTypes: {},
      reusedSessionCodes: {},
      searchTerms: {},
    })

    setAuthenticatedSession({
      principal: {
        permissions: [
          'catalog.price.read',
          'catalog.product.read',
          'pos.order.cancel',
          'pos.order.complete',
          'pos.order.create',
          'pos.order.read',
          'pos.order.update',
          'pos.session.close',
          'pos.session.open',
          'pos.session.read',
          'pos.session.reconcile',
        ],
      },
    })

    sessionState = createSession()
    orderState = createOrder()
    nextPaymentId = 1

    posApi.listPosSessions.mockImplementation(async (filters) => {
      const sessions = [sessionState].filter((session) => {
        if (filters.outletId && session.outletId !== filters.outletId) {
          return false
        }
        if (filters.businessDate && session.businessDate !== filters.businessDate) {
          return false
        }
        if (filters.status && session.status !== filters.status) {
          return false
        }
        return true
      })

      return clone(sessions)
    })
    posApi.getProducts.mockResolvedValue([
      {
        id: 301,
        code: 'ICED-TEA',
        name: 'Iced Tea',
        categoryCode: 'DRINK',
        status: 'ACTIVE',
        imageUrl: null,
        description: 'Fresh tea',
      },
    ])
    posApi.getProductPrices.mockResolvedValue([
      {
        id: 401,
        productId: 301,
        scopeType: 'OUTLET',
        scopeId: 101,
        priceType: 'DINE_IN',
        currencyCode: 'VND',
        priceValue: '150000',
        effectiveFrom: '2026-01-01T00:00:00.000Z',
        effectiveTo: null,
      },
    ])
    posApi.getProductAvailability.mockResolvedValue([
      {
        productId: 301,
        outletId: 101,
        available: true,
      },
    ])
    posApi.createSaleOrder.mockImplementation(async (payload: CreateSaleOrderPayload) => {
      orderState = createOrder({
        id: 900,
        orderNumber: 'SO-900',
        posSessionId: payload.posSessionId,
        orderType: payload.orderType,
        note: payload.note ?? null,
        lines: payload.lines.map((line: CreateSaleOrderPayload['lines'][number], index: number) => ({
          lineNumber: index + 1,
          productId: line.productId,
          productCode: 'ICED-TEA',
          productNameSnapshot: 'Iced Tea',
          unitPrice: '150000',
          qty: String(line.qty),
          discountAmount: '0',
          taxAmount: '0',
          lineTotal: '150000',
          note: line.note ?? null,
        })),
      })

      return clone(orderState)
    })
    posApi.getSaleOrder.mockImplementation(async () => clone(orderState))
    posApi.updateSaleOrder.mockImplementation(async (orderId: number, payload: UpdateSaleOrderPayload) => {
      orderState = {
        ...orderState,
        id: orderId,
        note: payload.note ?? null,
        lines: payload.lines.map((line: UpdateSaleOrderPayload['lines'][number], index: number) => ({
          ...orderState.lines[index],
          lineNumber: index + 1,
          productId: line.productId,
          qty: String(line.qty),
          note: line.note ?? null,
        })),
      }

      return clone(orderState)
    })
    posApi.addSalePayment.mockImplementation(async (orderId, payload) => {
      const totalPaid =
        orderState.payments.reduce((sum, payment) => sum + Number(payment.amount), 0) +
        (payload.status === 'SUCCESS' ? payload.amount : 0)

      orderState = {
        ...orderState,
        id: orderId,
        paymentStatus: totalPaid >= Number(orderState.totalAmount) ? 'PAID' : 'PARTIALLY_PAID',
        payments: [
          ...orderState.payments,
          {
            id: nextPaymentId++,
            paymentMethod: payload.paymentMethod,
            amount: String(payload.amount),
            status: payload.status ?? 'SUCCESS',
            paymentTime: payload.paymentTime ?? '2026-03-30T09:30:00.000Z',
            transactionRef: payload.transactionRef ?? null,
          },
        ],
      }

      return clone(orderState)
    })
    posApi.completeSaleOrder.mockImplementation(async (orderId) => {
      orderState = {
        ...orderState,
        id: orderId,
        status: 'COMPLETED',
        completedAt: '2026-03-30T09:45:00.000Z',
      }

      return clone(orderState)
    })
    posApi.cancelSaleOrder.mockImplementation(async (orderId) => {
      orderState = {
        ...orderState,
        id: orderId,
        status: 'CANCELLED',
      }

      return clone(orderState)
    })
  })

  it('creates an order from POS home, records payment, and completes the order', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <Routes>
        <Route path="/pos" element={<PosHomePage />} />
        <Route path="/pos/orders/:orderId" element={<PosOrderDetailPage />} />
      </Routes>,
      { route: '/pos' },
    )

    expect(await screen.findByText('Iced Tea')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add to cart' }))
    await user.click(screen.getByRole('button', { name: 'Create order' }))

    await waitFor(() => {
      expect(posApi.createSaleOrder).toHaveBeenCalledTimes(1)
    })
    expect(await screen.findByRole('heading', { name: 'SO-900' }, { timeout: 4000 })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Add payment' })).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'Add payment' }))

    await waitFor(() => {
      expect(posApi.addSalePayment).toHaveBeenCalledWith(
        900,
        expect.objectContaining({
          amount: 150000,
          paymentMethod: 'CASH',
          status: 'SUCCESS',
        }),
        expect.any(String),
      )
    })
    expect(await screen.findByRole('button', { name: 'Complete order' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Complete order' }))
    await user.click((await screen.findAllByRole('button', { name: 'Complete order' }))[1])

    expect(await screen.findByText('Order này không còn editable. Chỉ có thể xem trạng thái và lịch sử payment.')).toBeInTheDocument()
    expect(posApi.completeSaleOrder).toHaveBeenCalledWith(900)
  })

  it('cancels an open order without payments and switches the page to readonly', async () => {
    const user = userEvent.setup()

    orderState = createOrder({
      id: 901,
      orderNumber: 'SO-901',
      payments: [],
      paymentStatus: 'UNPAID',
      status: 'OPEN',
    })

    renderWithProviders(
      <Routes>
        <Route path="/pos/orders/:orderId" element={<PosOrderDetailPage />} />
      </Routes>,
      { route: '/pos/orders/901' },
    )

    expect(await screen.findByRole('heading', { name: 'SO-901' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cancel order' }))
    await user.click((await screen.findAllByRole('button', { name: 'Cancel order' }))[1])

    expect(await screen.findByText('Order này không còn editable. Chỉ có thể xem trạng thái và lịch sử payment.')).toBeInTheDocument()
    expect(posApi.cancelSaleOrder).toHaveBeenCalledWith(901)
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
  })
})
