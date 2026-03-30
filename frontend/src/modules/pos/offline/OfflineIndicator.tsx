import { Badge, Button } from '@design-system/index'
import { useOfflineQueue } from './useOfflineQueue'

export function OfflineIndicator() {
  const {
    failedCount,
    flushQueue,
    isOnline,
    isSyncing,
    lastSyncSummary,
    pendingCount,
    retryableFailedCount,
    retryingCount,
    totalCount,
  } = useOfflineQueue()

  if (isOnline && totalCount === 0 && !isSyncing && !lastSyncSummary) {
    return null
  }

  const bannerClassName = !isOnline
    ? 'inline-banner inline-banner-warning'
    : failedCount > 0
      ? 'inline-banner inline-banner-danger'
      : 'inline-banner inline-banner-success'

  let message = 'POS queue đang rỗng.'

  if (!isOnline) {
    message =
      'POS đang offline. Payment mới sẽ được giữ ở hàng chờ và retry với cùng Idempotency-Key khi kết nối trở lại. Complete, cancel, open, close và reconcile vẫn bị chặn.'
  } else if (isSyncing || retryingCount > 0) {
    message = `Đang retry ${Math.max(retryingCount, pendingCount + retryableFailedCount)} payment queued sau khi kết nối được khôi phục.`
  } else if (failedCount > 0) {
    message =
      retryableFailedCount > 0
        ? `${failedCount} payment chưa sync xong. Hệ thống sẽ tiếp tục retry khi kết nối ổn định hoặc khi bạn bấm retry thủ công.`
        : `${failedCount} payment bị từ chối khi sync lại. Hãy kiểm tra lỗi backend trước khi thử lại bằng thao tác mới.`
  } else if (lastSyncSummary && lastSyncSummary.succeeded > 0) {
    message = `Đã sync thành công ${lastSyncSummary.succeeded} payment queued gần nhất.`
  }

  return (
    <div className={bannerClassName} role="status">
      <div className="stack-inline">
        {!isOnline ? <Badge tone="warning">Offline</Badge> : <Badge tone="success">Online</Badge>}
        {pendingCount > 0 ? <Badge tone="neutral">{pendingCount} pending</Badge> : null}
        {retryingCount > 0 || isSyncing ? <Badge tone="warning">{Math.max(retryingCount, 1)} retrying</Badge> : null}
        {failedCount > 0 ? <Badge tone={retryableFailedCount > 0 ? 'warning' : 'danger'}>{failedCount} failed</Badge> : null}
      </div>
      <p>{message}</p>
      {isOnline && retryableFailedCount + pendingCount > 0 && !isSyncing ? (
        <Button onClick={() => void flushQueue()} size="sm" variant="secondary">
          Retry queued payments
        </Button>
      ) : null}
    </div>
  )
}
