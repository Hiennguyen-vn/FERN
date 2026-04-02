import { gatewayClient } from '@core/api/gatewayClient'
import type { PageResponse } from '@core/types/api'
import type {
  AssignUserRolesRequest,
  AssignUserScopesRequest,
  CreateUserRequest,
  EffectiveAccessResponse,
  IamPermission,
  IamRole,
  IamUser,
  PutUserPermissionOverridesRequest,
  UpdateUserRequest,
  UserPermissionOverridesResponse,
} from '../model/iam.types'

export const iamApi = {
  // ─── Reads ─────────────────────────────────────────────────────────────────
  listUsers: (params?: { search?: string; status?: string; page?: number; size?: number }) =>
    gatewayClient.get<PageResponse<IamUser>>('/users', { params }).then((r) => r.data),
  getUser: (userId: number) =>
    gatewayClient.get<IamUser>(`/users/${userId}`).then((r) => r.data),
  getRoles: () =>
    gatewayClient.get<IamRole[]>('/roles').then((r) => r.data),
  getPermissions: () =>
    gatewayClient.get<IamPermission[]>('/permissions').then((r) => r.data),
  getUserPermissionOverrides: (userId: number) =>
    gatewayClient.get<UserPermissionOverridesResponse>(`/users/${userId}/permission-overrides`).then((r) => r.data),
  getEffectiveAccess: (userId: number) =>
    gatewayClient.get<EffectiveAccessResponse>(`/users/${userId}/effective-access`).then((r) => r.data),

  // ─── Writes ────────────────────────────────────────────────────────────────
  createUser: (body: CreateUserRequest) =>
    gatewayClient.post<IamUser>('/users', body).then((r) => r.data),
  updateUser: (userId: number, body: UpdateUserRequest) =>
    gatewayClient.patch<IamUser>(`/users/${userId}`, body).then((r) => r.data),
  assignRoles: (userId: number, body: AssignUserRolesRequest) =>
    gatewayClient.post<IamUser>(`/users/${userId}/roles`, body).then((r) => r.data),
  assignScopes: (userId: number, body: AssignUserScopesRequest) =>
    gatewayClient.post<IamUser>(`/users/${userId}/scopes`, body).then((r) => r.data),
  replacePermissionOverrides: (userId: number, body: PutUserPermissionOverridesRequest) =>
    gatewayClient.put<UserPermissionOverridesResponse>(`/users/${userId}/permission-overrides`, body).then((r) => r.data),
}
