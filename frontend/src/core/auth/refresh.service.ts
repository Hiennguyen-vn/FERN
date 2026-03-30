import axios from 'axios'
import { getCorrelationId } from '@core/api/correlation'
import { appConfig } from '@core/config/appConfig'
import { useAuthStore } from './auth.store'
import type { AuthTokenResponse } from './auth.types'
import {
  getRefreshToken,
  parseJwtClaims,
  setStoredTokens,
  toPrincipal,
} from './token.service'

export async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()

  if (!refreshToken) {
    throw new Error('Missing refresh token')
  }

  const { data } = await axios.post<AuthTokenResponse>(
    '/auth/refresh',
    { refreshToken },
    {
      baseURL: appConfig.apiBaseUrl,
      timeout: 30_000,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-Correlation-Id': getCorrelationId(),
      },
    },
  )

  const claims = parseJwtClaims(data.accessToken)
  const principal = toPrincipal(claims, data.user)

  setStoredTokens(data.accessToken, data.refreshToken)
  useAuthStore.getState().setSession(data, principal)

  return data.accessToken
}
