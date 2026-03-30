import type { PosSession } from '../model/pos.types'

export function canCloseSession(session: Pick<PosSession, 'status'>, isOnline: boolean) {
  return String(session.status).toUpperCase() === 'OPEN' && isOnline
}

export function canReconcileSession(session: Pick<PosSession, 'status'>, isOnline: boolean) {
  return String(session.status).toUpperCase() === 'CLOSED' && isOnline
}

export function isSessionReadonly(session: Pick<PosSession, 'status'>) {
  return String(session.status).toUpperCase() === 'RECONCILED'
}
