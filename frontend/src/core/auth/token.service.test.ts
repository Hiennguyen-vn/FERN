import { describe, expect, it } from 'vitest'
import { parseJwtClaims } from './token.service'

function createToken(payload: Record<string, unknown>) {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
  return `${header}.${body}.`
}

describe('parseJwtClaims', () => {
  it('maps backend jwt payload to frontend claims', () => {
    const token = createToken({
      user_id: 7,
      sub: 'manager',
      roles: ['OUTLET_MANAGER'],
      permissions: ['pos.session.open'],
      scope_roots: { system: false, regions: [11], outlets: [22] },
      policy_version: 3,
      scope_version: 5,
      exp: 1_900_000_000,
      jti: 'jwt-id',
    })

    expect(parseJwtClaims(token)).toEqual({
      userId: 7,
      username: 'manager',
      roles: ['OUTLET_MANAGER'],
      permissions: ['pos.session.open'],
      scopeRoots: { system: false, regions: [11], outlets: [22] },
      policyVersion: 3,
      scopeVersion: 5,
      expiresAt: 1_900_000_000,
      jti: 'jwt-id',
    })
  })
})
