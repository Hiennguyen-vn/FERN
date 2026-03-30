import type { ScopeRoots } from '@core/auth/auth.types'
import type {
  EffectiveAccessResponse,
  IamPermission,
  PermissionOverrideItem,
} from '../model/iam.types'

export interface EffectivePermissionRow {
  code: string
  description: string
  effectiveState: 'EFFECTIVE' | 'DENIED'
  granted: boolean
  denied: boolean
  sourceLabels: string[]
  overrideReason: string | null
}

export function formatScopeRoots(scopeRoots: ScopeRoots) {
  const parts = []

  if (scopeRoots.system) {
    parts.push('System')
  }

  if (scopeRoots.regions.length > 0) {
    parts.push(`Regions: ${scopeRoots.regions.map((id) => `#${id}`).join(', ')}`)
  }

  if (scopeRoots.outlets.length > 0) {
    parts.push(`Outlets: ${scopeRoots.outlets.map((id) => `#${id}`).join(', ')}`)
  }

  if (parts.length === 0) {
    return 'No scope assignments'
  }

  return parts.join(' • ')
}

export function humanizeAccessSource(source: string) {
  if (source.startsWith('ROLE:')) {
    return `Inherited from role ${source.replace('ROLE:', '')}`
  }

  if (source === 'DIRECT_GRANT') {
    return 'Direct grant override'
  }

  if (source === 'DIRECT_DENY') {
    return 'Direct deny override'
  }

  return source
}

export function buildEffectivePermissionRows(
  access: EffectiveAccessResponse,
  permissions: IamPermission[] = [],
  overrides: PermissionOverrideItem[] = [],
) {
  const permissionLookup = new Map(permissions.map((permission) => [permission.code, permission] as const))
  const overrideLookup = new Map(overrides.map((override) => [override.permissionCode, override] as const))
  const codes = Array.from(
    new Set([
      ...access.grantedPermissions,
      ...access.deniedPermissions,
      ...access.effectivePermissions,
      ...Object.keys(access.sources),
    ]),
  ).sort((left, right) => left.localeCompare(right))

  return codes.map<EffectivePermissionRow>((code) => {
    const permission = permissionLookup.get(code)
    const override = overrideLookup.get(code)
    const denied = access.deniedPermissions.includes(code)
    const granted = access.grantedPermissions.includes(code)
    const effective = access.effectivePermissions.includes(code)

    return {
      code,
      description: permission?.description ?? permission?.name ?? 'No permission metadata available.',
      effectiveState: effective ? 'EFFECTIVE' : 'DENIED',
      granted,
      denied,
      sourceLabels: (access.sources[code] ?? []).map(humanizeAccessSource),
      overrideReason: override?.reason ?? null,
    }
  })
}

export function formatOverrideMode(mode: PermissionOverrideItem['overrideMode']) {
  return mode === 'GRANT' ? 'Direct grant' : 'Direct deny'
}
