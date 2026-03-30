import type { SummaryCardItem } from '@design-system/index'
import type { RegionalOutlet, RegionalRegion } from '../model/regionalOps.types'

const instantFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
})

export function formatRegionalInstant(value: string | null | undefined) {
  if (!value) {
    return 'N/A'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return instantFormatter.format(parsed)
}

export function formatRegionalDate(value: string | null | undefined) {
  if (!value) {
    return 'N/A'
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return dateFormatter.format(parsed)
}

export function matchesRegionalSearch(values: Array<string | number | null | undefined>, search: string) {
  const normalizedSearch = search.trim().toLowerCase()
  if (!normalizedSearch) {
    return true
  }

  return values.some((value) => String(value ?? '').toLowerCase().includes(normalizedSearch))
}

export function buildRegionalDashboardCards(region: RegionalRegion | null | undefined, outlets: RegionalOutlet[]): SummaryCardItem[] {
  const activeOutlets = outlets.filter((outlet) => outlet.status === 'ACTIVE').length
  const closedOutlets = outlets.filter((outlet) => outlet.status === 'CLOSED').length
  const missingContacts = outlets.filter((outlet) => !outlet.email && !outlet.phone).length

  return [
    { label: 'Region', tone: 'info', value: region?.code ?? 'N/A' },
    { label: 'Visible outlets', value: outlets.length },
    { label: 'Active outlets', tone: 'success', value: activeOutlets },
    { label: 'Missing contacts', tone: missingContacts > 0 ? 'warning' : 'default', value: missingContacts },
    ...(closedOutlets > 0 ? [{ label: 'Closed outlets', tone: 'danger' as const, value: closedOutlets }] : []),
  ]
}

export function buildOutletSummaryCards(outlets: RegionalOutlet[]): SummaryCardItem[] {
  const activeOutlets = outlets.filter((outlet) => outlet.status === 'ACTIVE').length
  const closedOutlets = outlets.filter((outlet) => outlet.status === 'CLOSED').length
  const withoutPhone = outlets.filter((outlet) => !outlet.phone).length
  const withoutEmail = outlets.filter((outlet) => !outlet.email).length

  return [
    { label: 'Outlets', value: outlets.length },
    { label: 'Active', tone: 'success', value: activeOutlets },
    { label: 'Closed', tone: closedOutlets > 0 ? 'danger' : 'default', value: closedOutlets },
    { label: 'No phone', tone: withoutPhone > 0 ? 'warning' : 'default', value: withoutPhone },
    { label: 'No email', tone: withoutEmail > 0 ? 'warning' : 'default', value: withoutEmail },
  ]
}

export function buildRegionalScopeLabel(regionId: number | null | undefined, region?: Pick<RegionalRegion, 'code' | 'name'> | null) {
  if (!regionId) {
    return 'No region context'
  }

  if (!region) {
    return `Region #${regionId}`
  }

  return `${region.code} · ${region.name}`
}

export function buildOutletContactLabel(outlet: Pick<RegionalOutlet, 'email' | 'phone'>) {
  return outlet.email ?? outlet.phone ?? 'No contact info'
}
