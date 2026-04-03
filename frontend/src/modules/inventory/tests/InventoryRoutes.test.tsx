import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import {
  StockAdjustmentCreatePage,
  StockCountSessionDetailPage,
  StockCountSessionsPage,
  StockOverviewPage,
} from '../routes/inventoryRoutes.bundle'

const mocks = vi.hoisted(() => {
  const createAdjustmentMutateAsync = vi.fn()
  const postAdjustmentMutateAsync = vi.fn()
  const cancelAdjustmentMutateAsync = vi.fn()
  const startStockCountMutateAsync = vi.fn()
  const postStockCountMutateAsync = vi.fn()
  const cancelStockCountMutateAsync = vi.fn()
  const updateStockCountLinesMutateAsync = vi.fn()
  return {
    createAdjustmentMutateAsync,
    postAdjustmentMutateAsync,
    cancelAdjustmentMutateAsync,
    startStockCountMutateAsync,
    postStockCountMutateAsync,
    cancelStockCountMutateAsync,
    updateStockCountLinesMutateAsync,
    useStockBalances: vi.fn(),
    useStockCountSessionList: vi.fn(),
    useStockCountSessionDetail: vi.fn(),
    useIngredients: vi.fn(),
    useCreateStockAdjustment: vi.fn(),
    usePostStockAdjustment: vi.fn(),
    useCancelStockAdjustment: vi.fn(),
    useStartStockCountSession: vi.fn(),
    usePostStockCountSession: vi.fn(),
    useCancelStockCountSession: vi.fn(),
    useUpdateStockCountLines: vi.fn(),
  }
})

vi.mock('../hooks/useStockBalances', () => ({
  useStockBalances: mocks.useStockBalances,
}))

vi.mock('../hooks/useStockCountSessionList', () => ({
  useStockCountSessionList: mocks.useStockCountSessionList,
}))

vi.mock('../hooks/useStockCountSessionDetail', () => ({
  useStockCountSessionDetail: mocks.useStockCountSessionDetail,
}))

vi.mock('../hooks/useInventoryCommands', () => ({
  useCreateStockAdjustment: mocks.useCreateStockAdjustment,
  usePostStockAdjustment: mocks.usePostStockAdjustment,
  useCancelStockAdjustment: mocks.useCancelStockAdjustment,
  useStartStockCountSession: mocks.useStartStockCountSession,
  usePostStockCountSession: mocks.usePostStockCountSession,
  useCancelStockCountSession: mocks.useCancelStockCountSession,
  useUpdateStockCountLines: mocks.useUpdateStockCountLines,
}))

vi.mock('@modules/catalog/hooks/useIngredients', () => ({
  useIngredients: mocks.useIngredients,
}))

function InventoryRoutesHarness() {
  return (
    <Routes>
      <Route path="/inventory/stock-balances" element={<StockOverviewPage />} />
      <Route path="/inventory/stock-adjustments/new" element={<StockAdjustmentCreatePage />} />
      <Route path="/inventory/stock-count-sessions" element={<StockCountSessionsPage />} />
      <Route path="/inventory/stock-count-sessions/:sessionId" element={<StockCountSessionDetailPage />} />
    </Routes>
  )
}

describe('Inventory route pages', () => {
  beforeEach(() => {
    resetTestStores()
    setAuthenticatedSession({
      principal: {
        permissions: [
          permissionConstants.inventory.balanceRead,
          permissionConstants.inventory.adjustmentWrite,
        ],
      },
    })

    mocks.useIngredients.mockReturnValue({
      data: [
        {
          id: 10,
          name: 'Flour',
          sku: 'FL-01',
          uom: 'KG',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ],
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    mocks.useStockBalances.mockReturnValue({
      data: {
        items: [
          {
            regionId: 1,
            outletId: 101,
            ingredientId: 10,
            qtyOnHand: 10,
            qtyReserved: 2,
            qtyAvailable: 3,
            unitCost: 100,
            lastCountDate: null,
          },
        ],
        page: 0,
        size: 20,
        hasMore: false,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })

    mocks.createAdjustmentMutateAsync.mockReset()
    mocks.postAdjustmentMutateAsync.mockReset()
    mocks.cancelAdjustmentMutateAsync.mockReset()
    mocks.startStockCountMutateAsync.mockReset()
    mocks.postStockCountMutateAsync.mockReset()
    mocks.cancelStockCountMutateAsync.mockReset()
    mocks.updateStockCountLinesMutateAsync.mockReset()

    mocks.useCreateStockAdjustment.mockReturnValue({
      mutateAsync: mocks.createAdjustmentMutateAsync,
      isPending: false,
      error: null,
    })
    mocks.usePostStockAdjustment.mockReturnValue({
      mutateAsync: mocks.postAdjustmentMutateAsync,
      isPending: false,
      error: null,
    })
    mocks.useCancelStockAdjustment.mockReturnValue({
      mutateAsync: mocks.cancelAdjustmentMutateAsync,
      isPending: false,
      error: null,
    })
    mocks.useStockCountSessionList.mockReturnValue({
      data: {
        items: [
          {
            id: 77,
            status: 'COUNTING',
            regionId: 1,
            outletId: 101,
            countDate: '2026-04-02',
            note: 'Evening count',
            startedAt: '2026-04-02T22:00:00Z',
            postedAt: null,
          },
        ],
        page: 0,
        size: 20,
        hasMore: false,
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useStockCountSessionDetail.mockReturnValue({
      data: {
        id: 77,
        status: 'POSTED',
        regionId: 1,
        outletId: 101,
        countDate: '2026-04-02',
        note: 'Evening count',
        startedAt: '2026-04-02T22:00:00Z',
        postedAt: '2026-04-02T22:45:00Z',
        lines: [
          {
            ingredientId: 10,
            systemQty: 12,
            actualQty: 11,
            varianceQty: -1,
            note: 'Minor variance',
          },
        ],
      },
      error: null,
      isLoading: false,
      refetch: vi.fn(),
    })
    mocks.useStartStockCountSession.mockReturnValue({
      mutateAsync: mocks.startStockCountMutateAsync,
      isPending: false,
      error: null,
    })
    mocks.usePostStockCountSession.mockReturnValue({
      mutateAsync: mocks.postStockCountMutateAsync,
      isPending: false,
      error: null,
    })
    mocks.useCancelStockCountSession.mockReturnValue({
      mutateAsync: mocks.cancelStockCountMutateAsync,
      isPending: false,
      error: null,
    })
    mocks.useUpdateStockCountLines.mockReturnValue({
      mutateAsync: mocks.updateStockCountLinesMutateAsync,
      isPending: false,
      error: null,
    })
  })

  it('StockOverviewPage renders stock balances table and low-stock summary badge', async () => {
    renderWithProviders(<InventoryRoutesHarness />, { route: '/inventory/stock-balances' })

    expect(await screen.findByRole('heading', { name: 'Stock Overview' })).toBeInTheDocument()

    expect(screen.getByRole('columnheader', { name: 'Ingredient' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Available' })).toBeInTheDocument()
    expect(screen.getByText('Flour')).toBeInTheDocument()

    const summary = screen.getByRole('region', { name: 'Stock summary' })
    expect(within(summary).getByText('Low stock lines')).toBeInTheDocument()
    expect(within(summary).getByText('Watch')).toBeInTheDocument()
  })

  it('StockAdjustmentCreatePage renders form and submits an adjustment', async () => {
    mocks.createAdjustmentMutateAsync.mockResolvedValue({
      id: 501,
      status: 'DRAFT',
      regionId: 1,
      outletId: 101,
      ingredientId: 55,
      adjustmentDirection: 'IN',
      qty: 2,
      businessDate: '2026-04-03',
      reason: 'Shrink',
      note: null,
      inventoryTransactionId: null,
      postedAt: null,
    })

    renderWithProviders(<InventoryRoutesHarness />, { route: '/inventory/stock-adjustments/new' })

    expect(await screen.findByRole('heading', { name: 'Stock Adjustment' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Create stock adjustment' })).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Ingredient ID/i), { target: { value: '55' } })
    fireEvent.change(screen.getByLabelText(/^Qty/i), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText(/Reason/i), { target: { value: 'Shrink' } })

    fireEvent.click(screen.getByRole('button', { name: 'Create adjustment' }))

    await waitFor(() => {
      expect(mocks.createAdjustmentMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          regionId: 1,
          outletId: 101,
          ingredientId: 55,
          adjustmentDirection: 'IN',
          qty: 2,
          reason: 'Shrink',
        }),
      )
    })

    expect(await screen.findByText('Adjustment #501')).toBeInTheDocument()
  })

  it('StockCountSessionsPage renders the outlet-scoped session list', async () => {
    renderWithProviders(<InventoryRoutesHarness />, { route: '/inventory/stock-count-sessions' })

    expect(await screen.findByRole('heading', { name: 'Stock count sessions' })).toBeInTheDocument()
    expect(screen.getByText('Outlet #101')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '#77' })).toBeInTheDocument()
    expect(screen.getByText('COUNTING')).toBeInTheDocument()
  })

  it('StockCountSessionDetailPage renders posted lines without mutation controls', async () => {
    renderWithProviders(<InventoryRoutesHarness />, { route: '/inventory/stock-count-sessions/77' })

    expect(await screen.findByRole('heading', { name: 'Stock count #77' })).toBeInTheDocument()
    expect(screen.getByText('Status: POSTED')).toBeInTheDocument()
    expect(screen.getByText(/#10: system 12, actual 11, variance -1/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Start count session' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save count lines' })).not.toBeInTheDocument()
  })
})
