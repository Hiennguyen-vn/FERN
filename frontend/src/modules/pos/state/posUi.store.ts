import { create } from 'zustand'
import type { PosOrderType } from '../model/pos.types'

interface PosUiState {
  businessDates: Record<string, string>
  categoryFilters: Record<string, string>
  orderTypes: Record<string, PosOrderType>
  reusedSessionCodes: Record<string, string>
  searchTerms: Record<string, string>
  clearOutletUi: (outletId: number) => void
  setBusinessDate: (outletId: number, businessDate: string) => void
  setCategoryFilter: (outletId: number, categoryCode: string) => void
  setOrderType: (outletId: number, orderType: PosOrderType) => void
  setReusedSessionCode: (outletId: number, sessionCode: string | null) => void
  setSearchTerm: (outletId: number, searchTerm: string) => void
}

function getOutletKey(outletId: number) {
  return String(outletId)
}

export const usePosUiStore = create<PosUiState>((set) => ({
  businessDates: {},
  categoryFilters: {},
  orderTypes: {},
  reusedSessionCodes: {},
  searchTerms: {},
  clearOutletUi: (outletId) =>
    set((state) => {
      const outletKey = getOutletKey(outletId)
      const businessDates = { ...state.businessDates }
      const categoryFilters = { ...state.categoryFilters }
      const orderTypes = { ...state.orderTypes }
      const reusedSessionCodes = { ...state.reusedSessionCodes }
      const searchTerms = { ...state.searchTerms }

      delete businessDates[outletKey]
      delete categoryFilters[outletKey]
      delete orderTypes[outletKey]
      delete reusedSessionCodes[outletKey]
      delete searchTerms[outletKey]

      return { businessDates, categoryFilters, orderTypes, reusedSessionCodes, searchTerms }
    }),
  setBusinessDate: (outletId, businessDate) =>
    set((state) => ({
      businessDates: { ...state.businessDates, [getOutletKey(outletId)]: businessDate },
    })),
  setCategoryFilter: (outletId, categoryCode) =>
    set((state) => ({
      categoryFilters: { ...state.categoryFilters, [getOutletKey(outletId)]: categoryCode },
    })),
  setOrderType: (outletId, orderType) =>
    set((state) => ({
      orderTypes: { ...state.orderTypes, [getOutletKey(outletId)]: orderType },
    })),
  setReusedSessionCode: (outletId, sessionCode) =>
    set((state) => ({
      reusedSessionCodes: {
        ...state.reusedSessionCodes,
        [getOutletKey(outletId)]: sessionCode ?? '',
      },
    })),
  setSearchTerm: (outletId, searchTerm) =>
    set((state) => ({
      searchTerms: { ...state.searchTerms, [getOutletKey(outletId)]: searchTerm },
    })),
}))
