import { ApiError } from '@core/api/apiError'

/**
 * Translates audit API errors into user-friendly Vietnamese messages.
 * Distinguishes 401 (session/token issue) from 403 (permission denied) and
 * 5xx server errors, providing actionable guidance in each case.
 */
export function getAuditErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.isUnauthorized) {
      return 'Phiên đăng nhập đã hết hạn hoặc token không hợp lệ. Đăng xuất và đăng nhập lại để tiếp tục.'
    }
    if (error.isForbidden) {
      return 'Bạn không có quyền audit.read để truy cập audit events. Liên hệ admin để được cấp quyền.'
    }
    if (error.isServerError) {
      return `Audit service gặp lỗi server (${String(error.status ?? '5xx')}). Thử lại sau hoặc kiểm tra audit-service logs.`
    }
    if (error.isNetworkError) {
      return 'Không thể kết nối tới audit service. Kiểm tra kết nối mạng và gateway config.'
    }
    if (error.message.trim()) {
      return error.message
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallback
}
