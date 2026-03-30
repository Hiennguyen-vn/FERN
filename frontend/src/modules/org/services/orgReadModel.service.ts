import type { OrgOutlet, OrgRegion } from '../model/org.types'

const instantFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
})

export function formatOrgInstant(value: string | null | undefined) {
  if (!value) {
    return 'N/A'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return instantFormatter.format(parsed)
}

export function formatOrgDate(value: string | null | undefined) {
  if (!value) {
    return 'N/A'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return dateFormatter.format(parsed)
}

export function normalizeOrgText(value: string | number | null | undefined) {
  return String(value ?? '').trim().toLowerCase()
}

export function matchesOrgSearch(values: Array<string | number | null | undefined>, search: string) {
  const normalizedSearch = normalizeOrgText(search)
  if (!normalizedSearch) {
    return true
  }

  return values.some((value) => normalizeOrgText(value).includes(normalizedSearch))
}

export function mergeOrgIds(...groups: Array<Array<number | null | undefined>>) {
  return Array.from(
    new Set(
      groups
        .flat()
        .filter((value): value is number => typeof value === 'number' && Number.isInteger(value) && value > 0),
    ),
  )
}

export function buildRegionLabel(regionId: number | null | undefined, region?: Pick<OrgRegion, 'code' | 'name'> | null) {
  if (!regionId) {
    return 'No region'
  }

  if (!region) {
    return `Region #${regionId}`
  }

  return `${region.code} · ${region.name}`
}

export function buildParentRegionLabel(
  parentRegionId: number | null | undefined,
  parentRegion?: Pick<OrgRegion, 'code' | 'name'> | null,
) {
  if (!parentRegionId) {
    return 'Root region'
  }

  return buildRegionLabel(parentRegionId, parentRegion)
}

export function buildOutletContactLabel(outlet: Pick<OrgOutlet, 'email' | 'phone'>) {
  return outlet.email ?? outlet.phone ?? 'No contact info'
}
