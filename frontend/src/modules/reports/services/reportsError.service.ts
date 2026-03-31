import { ApiError } from '@core/api/apiError'

export function isReportsPermissionDenied(error: unknown) {
  return error instanceof ApiError && error.isForbidden
}

export function getReportsPermissionDeniedMessage(resource: string) {
  return `Bạn không có quyền truy cập ${resource}.`
}

export function getReportsErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallback
}
