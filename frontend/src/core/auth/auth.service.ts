import { gatewayClient } from '@core/api/gatewayClient'
import { useAuthStore } from './auth.store'
import type { AuthTokenResponse } from './auth.types'
import {
  clearStoredTokens,
  parseJwtClaims,
  setStoredTokens,
  toPrincipal,
} from './token.service'

interface LoginCredentials {
  username: string
  password: string
}

export async function login(credentials: LoginCredentials): Promise<AuthTokenResponse> {
  const { data } = await gatewayClient.post<AuthTokenResponse>('/auth/login', credentials)
  const claims = parseJwtClaims(data.accessToken)
  const principal = toPrincipal(claims, data.user)

  setStoredTokens(data.accessToken, data.refreshToken)
  useAuthStore.getState().setSession(data, principal)

  return data
}

export async function logout(): Promise<void> {
  const state = useAuthStore.getState()

  try {
    if (state.refreshToken || state.accessToken) {
      await gatewayClient.post('/auth/logout', {
        refreshToken: state.refreshToken,
      })
    }
  } catch {
    // Ignore logout failures and clear client session regardless.
  } finally {
    clearStoredTokens()
    useAuthStore.getState().clearSession()
  }
}
