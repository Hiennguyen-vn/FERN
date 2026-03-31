import type { AuthUser, FernPrincipal, JwtClaims, ScopeRoots } from './auth.types'

const ACCESS_TOKEN_KEY = 'fern_access_token'
const REFRESH_TOKEN_KEY = 'fern_refresh_token'

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  return atob(padded)
}

function parseScopeRoots(value: unknown): ScopeRoots {
  const scopeRoots = (value ?? {}) as { system?: boolean; regions?: unknown[]; outlets?: unknown[] }

  return {
    system: Boolean(scopeRoots.system),
    regions: Array.isArray(scopeRoots.regions) ? scopeRoots.regions.map((item) => Number(item)).filter(Number.isFinite) : [],
    outlets: Array.isArray(scopeRoots.outlets) ? scopeRoots.outlets.map((item) => Number(item)).filter(Number.isFinite) : [],
  }
}

function parseOptionalScopeRoots(value: unknown): ScopeRoots | undefined {
  if (value == null) {
    return undefined
  }

  return parseScopeRoots(value)
}

function resolveAccessibleScope(claims: { accessibleScope?: ScopeRoots; scopeRoots: ScopeRoots }): ScopeRoots {
  return claims.accessibleScope ?? claims.scopeRoots
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setStoredTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearStoredTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function parseJwtClaims(token: string): JwtClaims {
  const [, payload] = token.split('.')

  if (!payload) {
    throw new Error('Invalid JWT token')
  }

  const parsed = JSON.parse(decodeBase64Url(payload)) as Record<string, unknown>

  return {
    userId: typeof parsed.user_id === 'number' ? parsed.user_id : null,
    username: typeof parsed.sub === 'string' ? parsed.sub : '',
    roles: Array.isArray(parsed.roles) ? parsed.roles.map(String) : [],
    permissions: Array.isArray(parsed.permissions) ? parsed.permissions.map(String) : [],
    scopeRoots: parseScopeRoots(parsed.scope_roots),
    accessibleScope: parseOptionalScopeRoots(parsed.accessible_scope),
    policyVersion: typeof parsed.policy_version === 'number' ? parsed.policy_version : 0,
    scopeVersion: typeof parsed.scope_version === 'number' ? parsed.scope_version : 0,
    jti: typeof parsed.jti === 'string' ? parsed.jti : undefined,
    expiresAt: typeof parsed.exp === 'number' ? parsed.exp : undefined,
  }
}

export function toPrincipal(claims: JwtClaims, user?: AuthUser | null): FernPrincipal {
  return {
    userId: claims.userId,
    username: claims.username,
    displayName: user?.fullName ?? claims.username,
    roles: claims.roles,
    permissions: claims.permissions,
    scopeRoots: claims.scopeRoots,
    accessibleScope: resolveAccessibleScope(claims),
    policyVersion: claims.policyVersion,
    scopeVersion: claims.scopeVersion,
    jti: claims.jti,
  }
}
