import { httpClient } from '@core/api/httpClient'
import type {
  EffectiveAccessResponse,
  IamPermission,
  IamRole,
  IamUser,
  UserPermissionOverridesResponse,
} from '../model/iam.types'

export const iamApi = {
  getUser: (userId: number) => httpClient.get<IamUser>(`/users/${userId}`).then((response) => response.data),
  getRoles: () => httpClient.get<IamRole[]>('/roles').then((response) => response.data),
  getPermissions: () => httpClient.get<IamPermission[]>('/permissions').then((response) => response.data),
  getUserPermissionOverrides: (userId: number) =>
    httpClient.get<UserPermissionOverridesResponse>(`/users/${userId}/permission-overrides`).then((response) => response.data),
  getEffectiveAccess: (userId: number) =>
    httpClient.get<EffectiveAccessResponse>(`/users/${userId}/effective-access`).then((response) => response.data),
}
