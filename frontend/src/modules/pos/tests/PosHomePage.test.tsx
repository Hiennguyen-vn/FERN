import { fireEvent, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@shared/test-utils/renderWithProviders'
import { PosHomePage } from '../routes/PosHomePage'
import { useCartStore } from '../state/cart.store'
import { usePosUiStore } from '../state/posUi.store'
import { getTodayBusinessDate } from '../services/posDate.service'

const openSessionMutateAsync = vi.fn()

vi.mock('@core/auth/auth.selectors', () => ({
  usePrincipal: () => ({
    userId: 7,
    username: 'cashier',
    roles: ['CASHIER'],
    permissions: ['pos.session.read', 'pos.session.open'],
    scopeRoots: { system: false, regions: [1], outlets: [101] },
    policyVersion: 1,
    scopeVersion: 1,
  }),
}))

vi.mock('@core/scopes/useScopeContext', () => ({
  useScopeContext: () => ({
    selectedOutletId: 101,
    selectedRegionId: 1,
    outletIds: [101],
    regionIds: [1],
    setSelectedOutletId: vi.fn(),
    setSelectedRegionId: vi.fn(),
  }),
}))

vi.mock('@shared/hooks/useNetworkStatus', () => ({
  useNetworkStatus: () => true,
}))

vi.mock('../hooks/usePosSession', () => ({
  usePosSessions: () => ({
    data: [],
    error: null,
    isLoading: false,
  }),
  useOpenPosSession: () => ({
    isPending: false,
    error: null,
    mutateAsync: openSessionMutateAsync,
  }),
  useClosePosSession: () => ({
    isPending: false,
    mutateAsync: vi.fn(),
  }),
}))

vi.mock('../hooks/usePosCatalog', () => ({
  usePosCatalog: () => ({
    data: [],
    error: null,
    isLoading: false,
  }),
}))

vi.mock('../hooks/usePosOrder', () => ({
  useCreatePosOrder: () => ({
    isPending: false,
    error: null,
    mutateAsync: vi.fn(),
  }),
}))

describe('PosHomePage', () => {
  beforeEach(() => {
    openSessionMutateAsync.mockReset()
    useCartStore.getState().clearAllDrafts()
    usePosUiStore.setState({
      businessDates: {},
      categoryFilters: {},
      orderTypes: {},
      reusedSessionCodes: {},
      searchTerms: {},
    })
  })

  it('renders open session form and submits live payload defaults', async () => {
    openSessionMutateAsync.mockResolvedValue({
      session: {
        id: 1,
        sessionCode: 'POS-001',
      },
      sessionExisted: false,
    })

    renderWithProviders(<PosHomePage />)

    expect(screen.getByText('Open POS session')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Open session' }))

    expect(openSessionMutateAsync).toHaveBeenCalledWith({
      regionId: 1,
      outletId: 101,
      businessDate: getTodayBusinessDate(),
      currencyCode: 'VND',
      note: undefined,
    })
  })
})
