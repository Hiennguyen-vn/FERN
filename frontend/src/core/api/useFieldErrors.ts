import { useMemo } from 'react'
import { ApiError } from './apiError'

export type FieldErrors = Record<string, string>

/**
 * Extracts per-field validation messages from a backend validation_error response.
 *
 * Backend contract (GlobalExceptionHandler):
 *   { code: "validation_error", message: "...", details: { fieldName: "error text", ... } }
 *
 * Works on any thrown value: returns {} for non-ApiError throws and when no details are present.
 */
export function extractFieldErrors(error: unknown): FieldErrors {
  if (!(error instanceof ApiError)) return {}
  if (!error.data || typeof error.data !== 'object') return {}
  const data = error.data as Record<string, unknown>
  if (!data.details || typeof data.details !== 'object' || Array.isArray(data.details)) return {}
  const details = data.details as Record<string, unknown>
  return Object.fromEntries(
    Object.entries(details)
      .filter(([, value]) => typeof value === 'string')
      .map(([key, value]) => [key, value as string]),
  )
}

/**
 * Returns the backend field error message for a single field, or `undefined` if absent.
 * Use this for inline form field error display.
 */
export function getFieldError(error: unknown, field: string): string | undefined {
  return extractFieldErrors(error)[field]
}

/**
 * React hook that memoises field error extraction from a mutation/query error.
 * Re-runs only when `error` changes identity.
 *
 * @example
 * const { getError, hasFieldErrors } = useFieldErrors(createOutletMutation.error)
 * // In JSX:
 * {getError('status') ? <p className="error-text">{getError('status')}</p> : null}
 */
export function useFieldErrors(error: unknown): {
  fieldErrors: FieldErrors
  getError: (field: string) => string | undefined
  hasFieldErrors: boolean
} {
  const fieldErrors = useMemo(() => extractFieldErrors(error), [error])
  return {
    fieldErrors,
    getError: (field: string) => fieldErrors[field],
    hasFieldErrors: Object.keys(fieldErrors).length > 0,
  }
}
