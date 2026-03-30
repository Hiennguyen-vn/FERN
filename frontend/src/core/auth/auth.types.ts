export interface ScopeRoots {
  system: boolean
  regions: number[]
  outlets: number[]
}

export interface FernPrincipal {
  userId: number | null
  username: string
  displayName?: string
  roles: string[]
  permissions: string[]
  scopeRoots: ScopeRoots
  policyVersion: number
  scopeVersion: number
  jti?: string
}

export interface AuthUser {
  id: number
  username: string
  fullName?: string
  email?: string
  phone?: string
  roleCodes: string[]
  scopeRoots: ScopeRoots
}

export interface AuthTokenResponse {
  accessToken: string
  refreshToken: string
  expiresAt: string
  user: AuthUser
}

export interface JwtClaims {
  userId: number | null
  username: string
  roles: string[]
  permissions: string[]
  scopeRoots: ScopeRoots
  policyVersion: number
  scopeVersion: number
  jti?: string
  expiresAt?: number
}

export interface AuthState {
  isAuthenticated: boolean
  principal: FernPrincipal | null
  user: AuthUser | null
  accessToken: string | null
  refreshToken: string | null
  expiresAt: string | null
  setSession: (session: AuthTokenResponse, principal: FernPrincipal) => void
  clearSession: () => void
  setAccessToken: (token: string, principal: FernPrincipal, expiresAt?: string) => void
}
