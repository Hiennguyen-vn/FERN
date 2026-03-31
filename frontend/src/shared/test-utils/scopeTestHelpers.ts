import { clearStoredTokens } from '@core/auth/token.service'
import { useAuthStore } from '@core/auth/auth.store'
import { useScopeContextStore } from '@core/scopes/scopeContext.store'
import type { AuthTokenResponse, AuthUser, FernPrincipal, ScopeRoots } from '@core/auth/auth.types'

function mergeScopeRoots(scopeRoots?: Partial<ScopeRoots>): ScopeRoots {
  return {
    system: scopeRoots?.system ?? false,
    regions: scopeRoots?.regions ?? [1],
    outlets: scopeRoots?.outlets ?? [101],
  }
}

export function createTestPrincipal(overrides: Partial<FernPrincipal> = {}): FernPrincipal {
  const scopeRoots = mergeScopeRoots(overrides.scopeRoots)
  const accessibleScope = overrides.accessibleScope ? mergeScopeRoots(overrides.accessibleScope) : scopeRoots

  return {
    userId: overrides.userId ?? 1,
    username: overrides.username ?? 'integration-user',
    displayName: overrides.displayName ?? 'Integration User',
    roles: overrides.roles ?? ['MANAGER'],
    permissions: overrides.permissions ?? [],
    scopeRoots,
    accessibleScope,
    policyVersion: overrides.policyVersion ?? 1,
    scopeVersion: overrides.scopeVersion ?? 1,
    jti: overrides.jti,
  }
}

export function createTestUser(overrides: Partial<AuthUser> = {}): AuthUser {
  const scopeRoots = mergeScopeRoots(overrides.scopeRoots)

  return {
    id: overrides.id ?? 1,
    username: overrides.username ?? 'integration-user',
    fullName: overrides.fullName ?? 'Integration User',
    email: overrides.email,
    phone: overrides.phone,
    roleCodes: overrides.roleCodes ?? ['MANAGER'],
    scopeRoots,
  }
}

export function createTestSession(overrides: {
  principal?: Partial<FernPrincipal>
  session?: Partial<AuthTokenResponse>
  user?: Partial<AuthUser>
} = {}) {
  const principal = createTestPrincipal(overrides.principal)
  const user = createTestUser({
    ...overrides.user,
    username: overrides.user?.username ?? principal.username,
    scopeRoots: overrides.user?.scopeRoots ?? principal.scopeRoots,
  })
  const session: AuthTokenResponse = {
    accessToken: overrides.session?.accessToken ?? 'access-token',
    refreshToken: overrides.session?.refreshToken ?? 'refresh-token',
    expiresAt: overrides.session?.expiresAt ?? '2030-01-01T00:00:00.000Z',
    user: overrides.session?.user ?? user,
  }

  return { principal, session, user }
}

export function setAuthenticatedSession(overrides: {
  principal?: Partial<FernPrincipal>
  session?: Partial<AuthTokenResponse>
  user?: Partial<AuthUser>
} = {}) {
  const { principal, session, user } = createTestSession(overrides)
  useAuthStore.getState().setSession(
    {
      ...session,
      user,
    },
    principal,
  )
  useScopeContextStore.getState().hydrateFromPrincipal(principal)

  return { principal, session, user }
}

export function resetTestStores() {
  clearStoredTokens()
  useAuthStore.getState().clearSession()
  useScopeContextStore.setState({
    selectedOutletId: null,
    selectedRegionId: null,
    outletIds: [],
    regionIds: [],
  })
}

export function clearTestStorage() {
  if (typeof window === 'undefined' || typeof window.localStorage === 'undefined') {
    return
  }

  const keys: string[] = []
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index)
    if (key) {
      keys.push(key)
    }
  }

  keys.forEach((key) => {
    window.localStorage.removeItem(key)
  })
}
