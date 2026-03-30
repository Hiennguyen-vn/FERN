import { ApiError } from '@core/api/apiError'

export type PosOfflineBlockedAction =
  | 'CANCEL_ORDER'
  | 'CLOSE_SESSION'
  | 'COMPLETE_ORDER'
  | 'OPEN_SESSION'
  | 'RECONCILE_SESSION'

const offlineBlockedMessages: Record<PosOfflineBlockedAction, string> = {
  OPEN_SESSION: 'Không thể mở session khi POS đang offline. Hãy kết nối lại rồi thử lại.',
  CLOSE_SESSION: 'Không thể đóng session khi POS đang offline. Hãy kết nối lại rồi thử lại.',
  RECONCILE_SESSION: 'Không thể reconcile session khi POS đang offline. Hãy kết nối lại rồi thử lại.',
  COMPLETE_ORDER: 'Không thể complete order khi POS đang offline. Hãy kết nối lại rồi thử lại.',
  CANCEL_ORDER: 'Không thể cancel order khi POS đang offline. Hãy kết nối lại rồi thử lại.',
}

export function getOfflineBlockedMessage(action: PosOfflineBlockedAction) {
  return offlineBlockedMessages[action]
}

export function assertPosActionOnline(action: PosOfflineBlockedAction, isOnline: boolean) {
  if (!isOnline) {
    throw new Error(getOfflineBlockedMessage(action))
  }
}

export function isRetryablePosQueueError(error: unknown) {
  if (error instanceof ApiError) {
    return error.isNetworkError || error.isServerError || error.status === 408 || error.status === 429
  }

  return error instanceof Error && /network|offline|timeout|failed to fetch/i.test(error.message)
}

export function toPosQueueErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }

  return 'Không thể đồng bộ POS action vào lúc này.'
}
