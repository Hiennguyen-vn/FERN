import { httpClient } from '@core/api/httpClient'
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
    httpClient.get<PageResponse<IamUser>>('/users', { params }).then((r) => r.data),
  getUser: (userId: number) =>
    httpClient.get<IamUser>(`/users/${userId}`).then((r) => r.data),
  getRoles: () =>
    httpClient.get<IamRole[]>('/roles').then((r) => r.data),
  getPermissions: () =>
    httpClient.get<IamPermission[]>('/permissions').then((r) => r.data),
  getUserPermissionOverrides: (userId: number) =>
    httpClient.get<UserPermissionOverridesResponse>(`/users/${userId}/permission-overrides`).then((r) => r.data),
  getEffectiveAccess: (userId: number) =>
    httpClient.get<EffectiveAccessResponse>(`/users/${userId}/effective-access`).then((r) => r.data),

  // ─── Writes ────────────────────────────────────────────────────────────────
  createUser: (body: CreateUserRequest) =>
    httpClient.post<IamUser>('/users', body).then((r) => r.data),
  updateUser: (userId: number, body: UpdateUserRequest) =>
    httpClient.patch<IamUser>(`/users/${userId}`, body).then((r) => r.data),
  assignRoles: (userId: number, body: AssignUserRolesRequest) =>
    httpClient.post<IamUser>(`/users/${userId}/roles`, body).then((r) => r.data),
  assignScopes: (userId: number, body: AssignUserScopesRequest) =>
    httpClient.post<IamUser>(`/users/${userId}/scopes`, body).then((r) => r.data),
  replacePermissionOverrides: (userId: number, body: PutUserPermissionOverridesRequest) =>
    httpClient.put<UserPermissionOverridesResponse>(`/users/${userId}/permission-overrides`, body).then((r) => r.data),
}
