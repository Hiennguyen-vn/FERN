import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { getCorrelationId } from './correlation'
import { getIdempotencyKey } from './idempotency'
import { ApiError } from './apiError'
import { appConfig } from '@core/config/appConfig'
import { refreshAccessToken } from '@core/auth/refresh.service'
import { getAccessToken } from '@core/auth/token.service'

const IDEMPOTENT_METHODS = new Set(['POST', 'PUT', 'PATCH'])

function createHttpClient(baseURL: string): AxiosInstance {
  const client = axios.create({
    baseURL,
    timeout: 30_000,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
  })

  // ── Request interceptor ────────────────────────────────────
  client.interceptors.request.use((config) => {
    const headers = config.headers ?? {}
    const token = getAccessToken()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    headers['X-Correlation-Id'] = getCorrelationId()

    if (config.method && IDEMPOTENT_METHODS.has(config.method.toUpperCase())) {
      if (!headers['Idempotency-Key']) {
        headers['Idempotency-Key'] = getIdempotencyKey(config)
      }
    }

    config.headers = headers
    return config
  })

  // ── Response interceptor ───────────────────────────────────
  client.interceptors.response.use(
    (response: AxiosResponse) => response,
    async (error) => {
      const status = error.response?.status
      const data = error.response?.data

      // Token expired — attempt refresh
      if (status === 401 && !error.config?._retried && !String(error.config?.url ?? '').includes('/auth/refresh')) {
        error.config._retried = true
        try {
          const newToken = await refreshAccessToken()
          error.config.headers = error.config.headers ?? {}
          error.config.headers.Authorization = `Bearer ${newToken}`
          return client.request(error.config)
        } catch {
          window.location.href = '/session-expired'
          return Promise.reject(error)
        }
      }

      return Promise.reject(new ApiError(status, data))
    },
  )

  return client
}

export const httpClient = createHttpClient(
  appConfig.apiBaseUrl,
)

export type { AxiosRequestConfig }
