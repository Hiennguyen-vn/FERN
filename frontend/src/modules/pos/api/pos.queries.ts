import type { PosCatalogFilters, PosSessionFilters } from '../model/pos.types'

export const posQueryKeys = {
  catalog: (filters: PosCatalogFilters) => ['pos', 'catalog', filters] as const,
  order: (orderId: number) => ['pos', 'order', orderId] as const,
  session: (sessionId: number) => ['pos', 'session', sessionId] as const,
  sessions: (filters: PosSessionFilters) => ['pos', 'sessions', filters] as const,
}

export const posMutationKeys = {
  addPayment: ['pos', 'order', 'payment'] as const,
  cancelOrder: ['pos', 'order', 'cancel'] as const,
  closeSession: ['pos', 'session', 'close'] as const,
  completeOrder: ['pos', 'order', 'complete'] as const,
  createOrder: ['pos', 'order', 'create'] as const,
  openSession: ['pos', 'session', 'open'] as const,
  reconcileSession: ['pos', 'session', 'reconcile'] as const,
  updateOrder: ['pos', 'order', 'update'] as const,
}
