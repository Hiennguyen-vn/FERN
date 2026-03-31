import axios from 'axios'
import { getCorrelationId } from '@core/api/correlation'
import { appConfig } from '@core/config/appConfig'
import { useAuthStore } from './auth.store'
import type { AuthTokenResponse } from './auth.types'
import {
  clearStoredTokens,
  getRefreshToken,
  parseJwtClaims,
  setStoredTokens,
  toPrincipal,
} from './token.service'

/**
 * Detects permission/scope revocation by comparing policy/scope versions.
 *
 * If either version decreases after a token refresh, the server has issued a
 * lower-privilege token — indicating a revocation or policy downgrade. In that
 * case we clear the session and redirect to /session-expired so the user is
 * forced to re-authenticate with a clean state.
 *
 * NOTE: A version increase is expected (server re-issued new policy) and is
 * allowed. Only a decrease is treated as a revocation signal.
 *
 * Backend gap: the server does not push revocation events proactively. This
 * check only fires on the next token refresh (i.e. after the current access
 * token expires). Mid-session revocations will not be detected until then.
 */
function detectRevocationAndRedirect(
  prevPolicyVersion: number,
  prevScopeVersion: number,
  nextPolicyVersion: number,
  nextScopeVersion: number,
): void {
  if (nextPolicyVersion < prevPolicyVersion || nextScopeVersion < prevScopeVersion) {
    clearStoredTokens()
    useAuthStore.getState().clearSession()
    window.location.href = '/session-expired'
  }
}

export async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()

  if (!refreshToken) {
    throw new Error('Missing refresh token')
  }

  const prevPrincipal = useAuthStore.getState().principal

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

  if (prevPrincipal) {
    detectRevocationAndRedirect(
      prevPrincipal.policyVersion,
      prevPrincipal.scopeVersion,
      principal.policyVersion,
      principal.scopeVersion,
    )
  }

  return data.accessToken
}
