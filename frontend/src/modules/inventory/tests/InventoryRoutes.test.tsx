import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { StockAdjustmentCreatePage, StockOverviewPage } from '../routes/inventoryRoutes.bundle'

const mocks = vi.hoisted(() => {
  const createAdjustmentMutateAsync = vi.fn()
  const postAdjustmentMutateAsync = vi.fn()
  const cancelAdjustmentMutateAsync = vi.fn()
  return {
    createAdjustmentMutateAsync,
    postAdjustmentMutateAsync,
    cancelAdjustmentMutateAsync,
    useStockBalances: vi.fn(),
    useIngredients: vi.fn(),
    useCreateStockAdjustment: vi.fn(),
    usePostStockAdjustment: vi.fn(),
    useCancelStockAdjustment: vi.fn(),
  }
})

vi.mock('../hooks/useStockBalances', () => ({
  useStockBalances: mocks.useStockBalances,
}))

vi.mock('../hooks/useInventoryCommands', () => ({
  useCreateStockAdjustment: mocks.useCreateStockAdjustment,
  usePostStockAdjustment: mocks.usePostStockAdjustment,
  useCancelStockAdjustment: mocks.useCancelStockAdjustment,
}))

vi.mock('@modules/catalog/hooks/useIngredients', () => ({
  useIngredients: mocks.useIngredients,
}))

function InventoryRoutesHarness() {
  return (
    <Routes>
      <Route path="/inventory/stock-balances" element={<StockOverviewPage />} />
      <Route path="/inventory/stock-adjustments/new" element={<StockAdjustmentCreatePage />} />
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
})
