// Generic types for API responses mapping Backend standards
export interface ApiResponse<T> {
  data: T
  message?: string
  meta?: any
}

/**
 * Shape emitted by every downstream service's GlobalExceptionHandler.
 * The gateway itself writes a minimal subset: only `message` is guaranteed
 * (e.g. token errors, rate-limit responses).
 */
export interface ApiErrorResponse {
  code: string
  message: string
  timestamp?: string
  correlationId?: string
  details?: Record<string, unknown>
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  hasMore: boolean
  /** Present when the API returns total page count */
  totalPages?: number
  /** Present when the API returns total row count */
  totalItems?: number
}
