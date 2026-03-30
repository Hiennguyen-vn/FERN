export function getTodayBusinessDate() {
  const now = new Date()
  const localMidnight = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return localMidnight.toISOString().slice(0, 10)
}

export function toDateTimeLocalValue(value: string | null | undefined) {
  if (!value) {
    return new Date().toISOString().slice(0, 16)
  }

  return new Date(value).toISOString().slice(0, 16)
}
