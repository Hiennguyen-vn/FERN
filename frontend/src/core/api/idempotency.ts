import type { AxiosRequestConfig } from 'axios'

// Generates a stable idempotency key based on method + url + body hash
export function getIdempotencyKey(config: AxiosRequestConfig): string {
  const method = config.method?.toUpperCase() ?? 'POST'
  const url = config.url ?? ''
  const body = config.data ? JSON.stringify(config.data) : ''
  const raw = `${method}:${url}:${body}`

  // Simple djb2 hash — good enough for idempotency key uniqueness
  let hash = 5381
  for (let i = 0; i < raw.length; i++) {
    hash = (hash * 33) ^ raw.charCodeAt(i)
  }
  const hashStr = (hash >>> 0).toString(16)
  return `${Date.now().toString(36)}-${hashStr}`
}

// For explicit idempotency key in POS payment flows
export function generateIdempotencyKey(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
