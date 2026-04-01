import { useQuery } from '@tanstack/react-query'
import { iamApi } from '../api/iam.api'

interface QueryOptions {
  enabled?: boolean
}

const KEYS = {
  users: (params: { search?: string; status?: string; page?: number; size?: number }) => ['iam', 'users', params] as const,
  user: (userId: number) => ['iam', 'users', userId] as const,
  roles: ['iam', 'roles'] as const,
  permissions: ['iam', 'permissions'] as const,
  permissionOverrides: (userId: number) => ['iam', 'users', userId, 'permission-overrides'] as const,
  effectiveAccess: (userId: number) => ['iam', 'users', userId, 'effective-access'] as const,
}

export function useIamUser(userId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.user(userId),
    queryFn: () => iamApi.getUser(userId),
    enabled: (options.enabled ?? true) && userId > 0,
  })
}

export function useIamUsers(params: { search?: string; status?: string; page?: number; size?: number }, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.users(params),
    queryFn: () => iamApi.listUsers(params),
    enabled: options.enabled ?? true,
  })
}

export function useIamRoles(options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.roles,
    queryFn: iamApi.getRoles,
    enabled: options.enabled ?? true,
    staleTime: 1000 * 60 * 5,
  })
}

export function useIamPermissions(options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.permissions,
    queryFn: iamApi.getPermissions,
    enabled: options.enabled ?? true,
    staleTime: 1000 * 60 * 5,
  })
}

export function useIamPermissionOverrides(userId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.permissionOverrides(userId),
    queryFn: () => iamApi.getUserPermissionOverrides(userId),
    enabled: (options.enabled ?? true) && userId > 0,
  })
}

export function useEffectiveAccess(userId: number, options: QueryOptions = {}) {
  return useQuery({
    queryKey: KEYS.effectiveAccess(userId),
    queryFn: () => iamApi.getEffectiveAccess(userId),
    enabled: (options.enabled ?? true) && userId > 0,
  })
}
