export interface ApiResponse<T> {
  data: T
  message?: string
  meta?: Record<string, unknown>
}

export interface ApiErrorResponse {
  error?: string
  code?: string
  details?: unknown
  message?: string
}
