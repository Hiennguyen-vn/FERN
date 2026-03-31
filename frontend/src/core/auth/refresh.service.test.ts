/**
 * refresh.service tests
 *
 * Covers permission freshness / revocation detection:
 * - successful refresh with same or higher policyVersion/scopeVersion → session updated
 * - refresh returns lower policyVersion → session cleared + redirect to /session-expired
 * - refresh returns lower scopeVersion → session cleared + redirect to /session-expired
 * - missing refresh token → throws immediately
 */
import axios from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './auth.store'
import { refreshAccessToken } from './refresh.service'
import { setStoredTokens, clearStoredTokens, parseJwtClaims, toPrincipal } from './token.service'
import type { AuthTokenResponse } from './auth.types'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

// Minimal valid JWT with the given policyVersion, scopeVersion, and permissions.
// We encode the payload as base64url — no real signature needed for tests.
function buildJwt(payload: Record<string, unknown>): string {
  const encoded = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return `header.${encoded}.sig`
}

function buildTokenResponse(policyVersion: number, scopeVersion: number): AuthTokenResponse {
  const token = buildJwt({
    sub: 'test-user',
    user_id: 1,
    roles: ['MANAGER'],
    permissions: ['hr.attendance.review'],
    scope_roots: { system: false, regions: [1], outlets: [101] },
    policy_version: policyVersion,
    scope_version: scopeVersion,
    exp: Math.floor(Date.now() / 1000) + 3600,
    jti: 'test-jti',
  })

  return {
    accessToken: token,
    refreshToken: 'new-refresh-token',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: {
      id: 1,
      username: 'test-user',
      fullName: 'Test User',
      roleCodes: ['MANAGER'],
      scopeRoots: { system: false, regions: [1], outlets: [101] },
    },
  }
}

function seedPrincipalInStore(policyVersion: number, scopeVersion: number) {
  const tokenResponse = buildTokenResponse(policyVersion, scopeVersion)
  const claims = parseJwtClaims(tokenResponse.accessToken)
  const principal = toPrincipal(claims, tokenResponse.user)
  useAuthStore.getState().setSession(tokenResponse, principal)
  setStoredTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
}

describe('refreshAccessToken — permission freshness', () => {
  const originalLocation = window.location

  beforeEach(() => {
    useAuthStore.getState().clearSession()
    clearStoredTokens()
    // Allow window.location.href to be set in tests
    Object.defineProperty(window, 'location', {
      value: { href: '/' },
      writable: true,
    })
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    })
    vi.clearAllMocks()
  })

  it('updates session normally when policyVersion is unchanged', async () => {
    seedPrincipalInStore(2, 3)
    const response = buildTokenResponse(2, 3)
    mockedAxios.post = vi.fn().mockResolvedValue({ data: response })

    await refreshAccessToken()

    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    expect(window.location.href).not.toBe('/session-expired')
  })

  it('updates session normally when policyVersion increases (expected re-issue)', async () => {
    seedPrincipalInStore(2, 3)
    const response = buildTokenResponse(3, 3)
    mockedAxios.post = vi.fn().mockResolvedValue({ data: response })

    await refreshAccessToken()

    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    expect(window.location.href).not.toBe('/session-expired')
  })

  it('redirects to /session-expired when policyVersion decreases (revocation)', async () => {
    seedPrincipalInStore(5, 3)
    const response = buildTokenResponse(4, 3) // policyVersion dropped
    mockedAxios.post = vi.fn().mockResolvedValue({ data: response })

    await refreshAccessToken()

    expect(window.location.href).toBe('/session-expired')
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('redirects to /session-expired when scopeVersion decreases (scope revocation)', async () => {
    seedPrincipalInStore(2, 5)
    const response = buildTokenResponse(2, 4) // scopeVersion dropped
    mockedAxios.post = vi.fn().mockResolvedValue({ data: response })

    await refreshAccessToken()

    expect(window.location.href).toBe('/session-expired')
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('throws and does not redirect when refresh token is missing', async () => {
    // No stored refresh token
    await expect(refreshAccessToken()).rejects.toThrow('Missing refresh token')
    expect(window.location.href).not.toBe('/session-expired')
  })
})
