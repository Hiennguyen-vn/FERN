import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { permissionConstants } from '@core/permissions/permission.constants'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { clearTestStorage, resetTestStores, setAuthenticatedSession } from '@shared/test-utils/scopeTestHelpers'
import { InventoryReportPage } from '../routes/InventoryReportPage'

const mocks = vi.hoisted(() => ({
  useCreateExportJob: vi.fn(),
  useInventoryReport: vi.fn(),
}))

vi.mock('../hooks/useCreateExportJob', () => ({
  useCreateExportJob: mocks.useCreateExportJob,
}))

vi.mock('../hooks/useInventoryReport', () => ({
  useInventoryReport: mocks.useInventoryReport,
}))

describe('InventoryReportPage', () => {
  beforeEach(() => {
    clearTestStorage()
    resetTestStores()
  })

  it('renders inventory summary and table rows for a read-plus-export user', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read, permissionConstants.report.export],
        scopeRoots: { outlets: [101], regions: [1], system: false },
      },
    })
    mocks.useCreateExportJob.mockReturnValue({ isPending: false, mutateAsync: vi.fn() })
    mocks.useInventoryReport.mockReturnValue({
      balanceQuery: {
        data: {
          hasMore: false,
          items: [
            {
              ingredientId: 9,
              lastCountDate: null,
              outletId: 101,
              qtyAvailable: '15',
              qtyOnHand: '20',
              qtyReserved: '5',
              regionId: 1,
              unitCost: '12000',
            },
          ],
          page: 0,
          size: 25,
        },
        error: null,
        isLoading: false,
        refetch: vi.fn(),
      },
      summary: {
        availableQuantity: 15,
        balanceRows: 1,
        ingredients: 1,
        transactionRows: 1,
        txnQuantityDelta: -2,
      },
      transactionQuery: {
        data: {
          hasMore: false,
          items: [
            {
              businessDate: '2026-03-29',
              createdByUserId: 1,
              id: 1,
              ingredientId: 9,
              outletId: 101,
              qtyChange: '-2',
              regionId: 1,
              sourceReferenceId: 'SO-1',
              sourceReferenceType: 'SALE_ORDER',
              txnTime: '2026-03-29T11:00:00.000Z',
              txnType: 'ISSUE',
              unitCost: '12000',
            },
          ],
          page: 0,
          size: 25,
        },
        error: null,
        isLoading: false,
        refetch: vi.fn(),
      },
    })

    renderWithProviders(<InventoryReportPage />)

    expect(screen.getByRole('heading', { name: 'Inventory Report' })).toBeInTheDocument()
    expect(screen.getByText('Stock snapshot')).toBeInTheDocument()
    expect(screen.getAllByText('#9').length).toBeGreaterThan(0)
    expect(screen.getByText('Movement breakdown')).toBeInTheDocument()
    expect(screen.getByText('SO-1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Queue export' })).toBeEnabled()
  })

  it('allows read-only users to open inventory report but disables export creation', () => {
    setAuthenticatedSession({
      principal: {
        permissions: [permissionConstants.report.read],
      },
    })
    mocks.useCreateExportJob.mockReturnValue({ isPending: false, mutateAsync: vi.fn() })
    mocks.useInventoryReport.mockReturnValue({
      balanceQuery: { data: null, error: null, isLoading: false, refetch: vi.fn() },
      summary: { availableQuantity: 0, balanceRows: 0, ingredients: 0, transactionRows: 0, txnQuantityDelta: 0 },
      transactionQuery: { data: null, error: null, isLoading: false, refetch: vi.fn() },
    })

    renderWithProviders(<InventoryReportPage />)

    expect(screen.getByText(/không thể queue inventory export/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Queue export' })).toBeDisabled()
  })

  it('shows permission denied for export-only users', () => {
    setAuthenticatedSession({ principal: { permissions: [permissionConstants.report.export] } })
    mocks.useCreateExportJob.mockReturnValue({ isPending: false, mutateAsync: vi.fn() })
    mocks.useInventoryReport.mockReturnValue({
      balanceQuery: { data: null, error: null, isLoading: false, refetch: vi.fn() },
      summary: { availableQuantity: 0, balanceRows: 0, ingredients: 0, transactionRows: 0, txnQuantityDelta: 0 },
      transactionQuery: { data: null, error: null, isLoading: false, refetch: vi.fn() },
    })

    renderWithProviders(<InventoryReportPage />)

    expect(screen.getByText('Permission denied')).toBeInTheDocument()
    expect(screen.getByText('Bạn cần report.read để mở inventory report.')).toBeInTheDocument()
  })
})
