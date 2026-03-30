import { useAuthStore } from './auth.store'
import { clearStoredTokens, getAccessToken, getRefreshToken, parseJwtClaims, toPrincipal } from './token.service'

export async function rehydrateSession(): Promise<void> {
  const accessToken = getAccessToken()
  const refreshToken = getRefreshToken()

  if (!accessToken || !refreshToken) {
    return
  }

  try {
    const claims = parseJwtClaims(accessToken)
    const currentState = useAuthStore.getState()
    const persistedUser = currentState.user

    if (!persistedUser) {
      currentState.clearSession()
      return
    }

    currentState.setSession(
      {
        accessToken,
        refreshToken,
        expiresAt: currentState.expiresAt ?? new Date().toISOString(),
        user: persistedUser,
      },
      toPrincipal(claims, persistedUser),
    )
  } catch {
    clearStoredTokens()
    useAuthStore.getState().clearSession()
  }
}
