const RECENT_REGIONS_KEY = 'org.recent-regions'
const RECENT_OUTLETS_KEY = 'org.recent-outlets'
const MAX_ITEMS = 12

function readIds(storageKey: string) {
  if (typeof window === 'undefined' || !window.localStorage) {
    return []
  }

  const raw = window.localStorage.getItem(storageKey)
  if (!raw) {
    return []
  }

  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed)
      ? parsed.filter((value): value is number => Number.isInteger(value) && value > 0)
      : []
  } catch {
    return []
  }
}

function saveId(storageKey: string, id: number) {
  if (typeof window === 'undefined' || !window.localStorage || !Number.isInteger(id) || id <= 0) {
    return
  }

  const rows = readIds(storageKey).filter((value) => value !== id)
  rows.unshift(id)
  window.localStorage.setItem(storageKey, JSON.stringify(rows.slice(0, MAX_ITEMS)))
}

export function loadRecentRegionIds() {
  return readIds(RECENT_REGIONS_KEY)
}

export function saveRecentRegionId(regionId: number) {
  saveId(RECENT_REGIONS_KEY, regionId)
}

export function loadRecentOutletIds() {
  return readIds(RECENT_OUTLETS_KEY)
}

export function saveRecentOutletId(outletId: number) {
  saveId(RECENT_OUTLETS_KEY, outletId)
}
