import type { IamUser } from '../model/iam.types'

const STORAGE_KEY = 'iam.recent-users'
const MAX_ITEMS = 10

export function loadRecentIamUsers(): IamUser[] {
  if (typeof window === 'undefined' || !window.localStorage) {
    return []
  }

  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }

  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveRecentIamUser(user: IamUser) {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }

  const items = loadRecentIamUsers().filter((item) => item.id !== user.id)
  items.unshift(user)
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_ITEMS)))
}

export function clearRecentIamUsers() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return
  }

  window.localStorage.removeItem(STORAGE_KEY)
}
