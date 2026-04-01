/**
 * Parses a backend timestamp value which may be:
 * - Unix epoch in SECONDS (float) from Java Instant  e.g. 1775029163.409
 * - ISO 8601 string e.g. "2026-04-01T08:00:00Z"
 * - null / undefined
 */
export function parseBackendInstant(value: unknown): Date | null {
  if (value == null) return null
  if (typeof value === 'number') {
    // Java serialises Instant as seconds (double). Multiply to get ms.
    return new Date(value * 1000)
  }
  if (typeof value === 'string' && value.length > 0) {
    const d = new Date(value)
    return isNaN(d.getTime()) ? null : d
  }
  return null
}

/**
 * Parses a backend LocalDate value which may be:
 * - Java array serialisation [year, month, day] e.g. [2026, 4, 1]
 * - ISO date string e.g. "2026-04-01"
 */
export function parseBackendDate(value: unknown): Date | null {
  if (value == null) return null
  if (Array.isArray(value) && value.length === 3) {
    // [year, month (1-based), day]
    return new Date(value[0] as number, (value[1] as number) - 1, value[2] as number)
  }
  if (typeof value === 'string' && value.length >= 8) {
    const d = new Date(value)
    return isNaN(d.getTime()) ? null : d
  }
  return null
}

export function formatDateTime(value: unknown): string {
  if (value == null || value === '') return 'N/A'
  const d = parseBackendInstant(value)
  if (!d || isNaN(d.getTime())) return 'N/A'
  return d.toLocaleString('vi-VN')
}

export function formatDate(value: unknown): string {
  if (value == null || value === '') return 'N/A'
  const d = parseBackendDate(value)
  if (!d || isNaN(d.getTime())) return 'N/A'
  return d.toLocaleDateString('vi-VN')
}
