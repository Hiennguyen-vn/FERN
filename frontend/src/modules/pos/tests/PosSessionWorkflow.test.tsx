import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { PosSessionDetailPage } from '../routes/PosSessionDetailPage'
import { PosSessionsPage } from '../routes/PosSessionsPage'
import type { PosSession } from '../model/pos.types'

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
    id: overrides.id ?? 601,
    sessionCode: overrides.sessionCode ?? 'POS-601',
    regionId: overrides.regionId ?? 1,
    outletId: overrides.outletId ?? 101,
    terminalId: overrides.terminalId ?? null,
    currencyCode: overrides.currencyCode ?? 'VND',
    cashierUserId: overrides.cashierUserId ?? 10,
    managerUserId: overrides.managerUserId ?? 20,
    businessDate: overrides.businessDate ?? '2026-03-30',
    status: overrides.status ?? 'OPEN',
    note: overrides.note ?? null,
    openedAt: overrides.openedAt ?? '2026-03-30T08:00:00.000Z',
    closedAt: overrides.closedAt ?? null,
    reconciledAt: overrides.reconciledAt ?? null,
    expectedCashAmount: overrides.expectedCashAmount ?? '120000',
    countedCashAmount: overrides.countedCashAmount ?? '0',
    discrepancyAmount: overrides.discrepancyAmount ?? '0',
  }
}

describe('POS session workflows', () => {
  let sessions: PosSession[]

  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          'pos.session.read',
          'pos.session.open',
          'pos.session.close',
          'pos.session.reconcile',
        ],
      },
    })

    sessions = []

    posApi.listPosSessions.mockImplementation(async (filters) =>
      clone(
        sessions.filter((session) => {
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
        }),
      ),
    )
    posApi.openPosSession.mockImplementation(async (payload) => {
      const session = createSession({
        id: 601,
        sessionCode: 'POS-601',
        regionId: payload.regionId,
        outletId: payload.outletId,
        businessDate: payload.businessDate,
        note: payload.note ?? null,
      })
      sessions = [session]
      return {
        session: clone(session),
        sessionExisted: false,
      }
    })
    posApi.closePosSession.mockImplementation(async (sessionId) => {
      sessions = sessions.map((session) =>
        session.id === sessionId
          ? {
              ...session,
              status: 'CLOSED',
              closedAt: '2026-03-30T18:00:00.000Z',
            }
          : session,
      )

      return clone(sessions.find((session) => session.id === sessionId)!)
    })
    posApi.getPosSession.mockImplementation(async (sessionId) => clone(sessions.find((session) => session.id === sessionId)!))
    posApi.reconcilePosSession.mockImplementation(async (sessionId, payload) => {
      sessions = sessions.map((session) =>
        session.id === sessionId
          ? {
              ...session,
              status: 'RECONCILED',
              countedCashAmount: String(payload.countedCashAmount),
              discrepancyAmount: String(payload.countedCashAmount - Number(session.expectedCashAmount)),
              reconciledAt: '2026-03-30T18:15:00.000Z',
              note: payload.note ?? null,
            }
          : session,
      )

      return clone(sessions.find((session) => session.id === sessionId)!)
    })
  })

  it('opens and closes a POS session from the session control page', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <Routes>
        <Route path="/pos/sessions" element={<PosSessionsPage />} />
        <Route path="/pos/sessions/:sessionId" element={<PosSessionDetailPage />} />
      </Routes>,
      { route: '/pos/sessions' },
    )

    await user.click(screen.getByRole('button', { name: 'Open session' }))

    expect(await screen.findByText('POS-601')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Close' }))
    await user.click(await screen.findByRole('button', { name: 'Close session' }))

    expect(posApi.closePosSession).toHaveBeenCalledWith(601)
    expect(await screen.findByText('CLOSED')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
  })

  it('shows session control for outlet-only cashier when sessions provide region context', async () => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: ['pos.session.read', 'pos.session.open'],
        scopeRoots: { system: false, regions: [], outlets: [101] },
        accessibleScope: { system: false, regions: [], outlets: [101] },
      },
      user: {
        scopeRoots: { system: false, regions: [], outlets: [101] },
      },
    })

    sessions = [
      createSession({
        id: 701,
        sessionCode: 'POS-701',
        regionId: 14,
        outletId: 101,
        status: 'OPEN',
        businessDate: '2026-04-01',
      }),
    ]

    renderWithProviders(
      <Routes>
        <Route path="/pos/sessions" element={<PosSessionsPage />} />
      </Routes>,
      { route: '/pos/sessions' },
    )

    expect(await screen.findByText('POS-701')).toBeInTheDocument()
    expect(screen.getByDisplayValue('14')).toBeInTheDocument()
    expect(screen.queryByText('POS session context missing')).not.toBeInTheDocument()
  })

  it('reconciles a closed session from the detail page', async () => {
    const user = userEvent.setup()

    sessions = [
      createSession({
        id: 777,
        sessionCode: 'POS-777',
        status: 'CLOSED',
        closedAt: '2026-03-30T18:00:00.000Z',
        expectedCashAmount: '120000',
      }),
    ]

    renderWithProviders(
      <Routes>
        <Route path="/pos/sessions/:sessionId" element={<PosSessionDetailPage />} />
      </Routes>,
      { route: '/pos/sessions/777' },
    )

    expect(await screen.findByRole('heading', { name: 'POS-777' })).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Counted cash amount'))
    await user.type(screen.getByLabelText('Counted cash amount'), '118000')
    await user.type(screen.getByLabelText('Note'), 'Cash counted with shortfall')
    await user.click(screen.getByRole('button', { name: 'Reconcile session' }))

    await waitFor(() => {
      expect(posApi.reconcilePosSession).toHaveBeenCalledWith(777, {
        countedCashAmount: 118000,
        note: 'Cash counted with shortfall',
      })
    })
    expect(await screen.findByText('Session này đã được reconcile và hiện ở trạng thái chỉ đọc.')).toBeInTheDocument()
    expect(screen.getByText(/Cash counted with shortfall/)).toBeInTheDocument()
  })
})
