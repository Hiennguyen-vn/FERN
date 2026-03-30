export class ApiError extends Error {
  public status?: number
  public data?: unknown
  public code?: string

  constructor(status?: number, data?: unknown) {
    const errorData = (data ?? {}) as { message?: string; error?: string; code?: string }
    const errorMsg = errorData.message || errorData.error || 'Unknown API Error'
    super(errorMsg)
    Object.setPrototypeOf(this, ApiError.prototype)
    this.name = 'ApiError'
    this.status = status
    this.data = data
    this.code = errorData.code || 'UNKNOWN_ERROR'
  }

  get isNetworkError(): boolean {
    return this.status === undefined || this.status === 0
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isNotFound(): boolean {
    return this.status === 404
  }

  get isValidationError(): boolean {
    return this.status === 400
  }

  get isServerError(): boolean {
    return typeof this.status === 'number' && this.status >= 500
  }
}
