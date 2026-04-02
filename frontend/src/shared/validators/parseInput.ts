/**
 * NaN-safe form-input parsing utilities.
 *
 * These helpers are used when converting raw HTML form string values to the
 * numbers required by backend DTOs. They avoid the silent-NaN problem of a
 * bare `Number(value)` call and are the source-of-truth for numeric validation
 * across all published write flows.
 */

/**
 * Converts a string to a number only when the result is finite.
 * Returns `undefined` for empty strings, whitespace-only strings, and
 * non-numeric input (which `Number()` would silently convert to `NaN`).
 *
 * Accepts `string | undefined | null` so callers can pass optional form fields
 * directly without a separate null-check.
 */
export function toOptionalNumber(value: string | undefined | null): number | undefined {
  if (!value?.trim()) return undefined
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

/**
 * Parses a string as a **positive integer** (> 0, no decimal part).
 * Returns the number when valid, or `null` otherwise.
 *
 * Use for ID fields — backend `@NotNull Long` or `@Positive Long`.
 */
export function parsePositiveInt(value: string): number | null {
  const n = Number(value)
  return Number.isInteger(n) && n > 0 ? n : null
}

/**
 * Parses a string as a finite decimal that is **≥ minInclusive**.
 * Returns the number when valid, or `null` otherwise.
 *
 * Use for fields with backend `@DecimalMin("X")` (inclusive by default).
 */
export function parseDecimalMin(value: string, minInclusive: number): number | null {
  const n = Number(value)
  return Number.isFinite(n) && n >= minInclusive ? n : null
}

/**
 * Parses a string as a finite non-negative decimal (≥ 0).
 * Equivalent to `parseDecimalMin(value, 0)` but named for readability.
 *
 * Use for fields with backend `@DecimalMin("0.00")`.
 */
export function parseNonNegativeDecimal(value: string): number | null {
  return parseDecimalMin(value, 0)
}

/**
 * Returns `true` when a string can be parsed as a valid, finite `Date`.
 * Works for both `datetime-local` values (`"2026-04-01T10:00"`) and ISO
 * strings (`"2026-04-01T10:00:00.000Z"`).
 */
export function isValidDateTime(value: string): boolean {
  return value.trim().length > 0 && !isNaN(new Date(value).getTime())
}
