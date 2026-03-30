// Generic types for API responses mapping Backend standards
export interface ApiResponse<T> {
  data: T
  message?: string
  meta?: any
}

export interface ApiErrorResponse {
  error: string
  code: string
  details?: unknown
  message?: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  hasMore: boolean
}
