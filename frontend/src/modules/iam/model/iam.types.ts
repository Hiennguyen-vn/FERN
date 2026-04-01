import type { ScopeRoots } from '@core/auth/auth.types'

export type IamUserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'SUSPENDED'
export type IamRoleStatus = 'ACTIVE' | 'INACTIVE'
export type PermissionOverrideMode = 'GRANT' | 'DENY'

// ─── Write request types ──────────────────────────────────────────────────────
export interface CreateUserRequest {
  username: string
  password: string
  fullName: string
  email?: string | null
  phone?: string | null
  status?: IamUserStatus
}

export interface UpdateUserRequest {
  fullName?: string | null
  email?: string | null
  phone?: string | null
  status?: IamUserStatus
}

export interface AssignUserRolesRequest {
  roleCodes: string[]
}

export interface AssignUserScopesRequest {
  system?: boolean
  /** Backend field name is regionIds — not regions. */
  regionIds?: number[]
  /** Backend field name is outletIds — not outlets. */
  outletIds?: number[]
}

export interface PutUserPermissionOverridesRequest {
  overrides: Array<{
    permissionCode: string
    overrideMode: PermissionOverrideMode
    reason?: string | null
    expiresAt?: string | null
  }>
}

export interface IamUser {
  id: number
  username: string
  fullName: string | null
  email: string | null
  phone: string | null
  status: IamUserStatus
  roleCodes: string[]
  scopeRoots: ScopeRoots
}

export interface IamRole {
  id: number
  code: string
  name: string
  description: string | null
  status: IamRoleStatus
  permissionCodes: string[]
}

export interface IamPermission {
  code: string
  name: string
  description: string | null
}

export interface PermissionOverrideItem {
  permissionCode: string
  overrideMode: PermissionOverrideMode
  reason: string | null
  expiresAt: string | null
}

export interface UserPermissionOverridesResponse {
  userId: number
  overrides: PermissionOverrideItem[]
}

export interface EffectiveAccessResponse {
  userId: number
  roles: string[]
  grantedPermissions: string[]
  deniedPermissions: string[]
  effectivePermissions: string[]
  scopeRoots: ScopeRoots
  sources: Record<string, string[]>
}
