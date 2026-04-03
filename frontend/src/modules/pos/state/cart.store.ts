import { create } from 'zustand'
import type { CartDraft, PosOrderType, ResolvedMenuItem } from '../model/pos.types'

interface CartState {
  drafts: Record<string, CartDraft>
  addItem: (outletId: number, orderType: PosOrderType, item: ResolvedMenuItem) => void
  clearAllDrafts: () => void
  clearDraft: (outletId: number, orderType: PosOrderType) => void
  clearOutletDrafts: (outletId: number) => void
  removeItem: (outletId: number, orderType: PosOrderType, productId: number) => void
  setOrderNote: (outletId: number, orderType: PosOrderType, orderNote: string) => void
  updateItemNote: (outletId: number, orderType: PosOrderType, productId: number, note: string) => void
  updateItemQty: (outletId: number, orderType: PosOrderType, productId: number, qty: string) => void
}

function getDraftKey(outletId: number, orderType: PosOrderType) {
  return `${outletId}:${orderType}`
}

function createEmptyCartDraft(outletId: number, orderType: PosOrderType): CartDraft {
  return {
    outletId,
    orderType,
    orderNote: '',
    items: [],
  }
}

export function selectCartDraft(drafts: Record<string, CartDraft>, outletId: number, orderType: PosOrderType) {
  return drafts[getDraftKey(outletId, orderType)] ?? createEmptyCartDraft(outletId, orderType)
}

export const useCartStore = create<CartState>((set) => ({
  drafts: {},
  addItem: (outletId, orderType, item) =>
    set((state) => {
      const draftKey = getDraftKey(outletId, orderType)
      const currentDraft = state.drafts[draftKey] ?? createEmptyCartDraft(outletId, orderType)
      const existingItem = currentDraft.items.find((draftItem) => draftItem.productId === item.productId)

      return {
        drafts: {
          ...state.drafts,
          [draftKey]: {
            ...currentDraft,
            items: existingItem
              ? currentDraft.items.map((draftItem) =>
                  draftItem.productId === item.productId
                    ? { ...draftItem, qty: String(Number(draftItem.qty) + 1) }
                    : draftItem,
                )
              : [
                  ...currentDraft.items,
                  {
                    productId: item.productId,
                    productCode: item.code,
                    productName: item.name,
                    categoryCode: item.categoryCode,
                    currencyCode: item.currencyCode,
                    unitPrice: String(item.priceValue),
                    qty: '1',
                    note: '',
                  },
                ],
          },
        },
      }
    }),
  clearAllDrafts: () => set({ drafts: {} }),
  clearDraft: (outletId, orderType) =>
    set((state) => {
      const nextDrafts = { ...state.drafts }
      delete nextDrafts[getDraftKey(outletId, orderType)]
      return { drafts: nextDrafts }
    }),
  clearOutletDrafts: (outletId) =>
    set((state) => ({
      drafts: Object.fromEntries(Object.entries(state.drafts).filter(([key]) => !key.startsWith(`${outletId}:`))),
    })),
  removeItem: (outletId, orderType, productId) =>
    set((state) => {
      const draftKey = getDraftKey(outletId, orderType)
      const currentDraft = state.drafts[draftKey] ?? createEmptyCartDraft(outletId, orderType)

      return {
        drafts: {
          ...state.drafts,
          [draftKey]: {
            ...currentDraft,
            items: currentDraft.items.filter((item) => item.productId !== productId),
          },
        },
      }
    }),
  setOrderNote: (outletId, orderType, orderNote) =>
    set((state) => {
      const draftKey = getDraftKey(outletId, orderType)
      const currentDraft = state.drafts[draftKey] ?? createEmptyCartDraft(outletId, orderType)

      return {
        drafts: {
          ...state.drafts,
          [draftKey]: {
            ...currentDraft,
            orderNote,
          },
        },
      }
    }),
  updateItemNote: (outletId, orderType, productId, note) =>
    set((state) => {
      const draftKey = getDraftKey(outletId, orderType)
      const currentDraft = state.drafts[draftKey] ?? createEmptyCartDraft(outletId, orderType)

      return {
        drafts: {
          ...state.drafts,
          [draftKey]: {
            ...currentDraft,
            items: currentDraft.items.map((item) => (item.productId === productId ? { ...item, note } : item)),
          },
        },
      }
    }),
  updateItemQty: (outletId, orderType, productId, qty) =>
    set((state) => {
      const draftKey = getDraftKey(outletId, orderType)
      const currentDraft = state.drafts[draftKey] ?? createEmptyCartDraft(outletId, orderType)

      return {
        drafts: {
          ...state.drafts,
          [draftKey]: {
            ...currentDraft,
            items: currentDraft.items.map((item) => (item.productId === productId ? { ...item, qty } : item)),
          },
        },
      }
    }),
}))
