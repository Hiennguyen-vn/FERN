import { describe, expect, it } from 'vitest'
import { ApiError } from './apiError'
import { extractFieldErrors, getFieldError } from './useFieldErrors'

/**
 * Tests for field-error extraction from backend GlobalExceptionHandler responses.
 *
 * Backend contract (GlobalExceptionHandler):
 *   HTTP 400 validation_error body:
 *   {
 *     "code": "validation_error",
 *     "message": "Validation failed",
 *     "timestamp": "...",
 *     "correlationId": "...",
 *     "details": {
 *       "fieldName": "error message",
 *       "otherField": "other message"
 *     }
 *   }
 *
 * The frontend must extract per-field messages from `details` and surface them
 * next to the relevant form inputs via useFieldErrors / getFieldError.
 */
describe('extractFieldErrors', () => {
  it('extracts field errors from a backend validation_error ApiError', () => {
    const error = new ApiError(400, {
      code: 'validation_error',
      message: 'Validation failed',
      details: {
        name: 'must not be blank',
        status: 'must be one of ACTIVE, INACTIVE',
      },
    })

    expect(extractFieldErrors(error)).toEqual({
      name: 'must not be blank',
      status: 'must be one of ACTIVE, INACTIVE',
    })
  })

  it('returns empty object for non-ApiError throws', () => {
    expect(extractFieldErrors(new Error('network error'))).toEqual({})
    expect(extractFieldErrors(null)).toEqual({})
    expect(extractFieldErrors(undefined)).toEqual({})
    expect(extractFieldErrors('string error')).toEqual({})
  })

  it('returns empty object when ApiError has no details', () => {
    const noDetails = new ApiError(400, { code: 'validation_error', message: 'bad request' })
    expect(extractFieldErrors(noDetails)).toEqual({})
  })

  it('returns empty object when details is an array (not field map)', () => {
    const arrayDetails = new ApiError(400, {
      code: 'validation_error',
      details: ['error1', 'error2'],
    })
    expect(extractFieldErrors(arrayDetails)).toEqual({})
  })

  it('ignores non-string values inside details', () => {
    const mixed = new ApiError(400, {
      code: 'validation_error',
      details: {
        name: 'must not be blank',
        nested: { inner: 'value' }, // non-string — ignored
        count: 5,                   // non-string — ignored
      },
    })
    expect(extractFieldErrors(mixed)).toEqual({ name: 'must not be blank' })
  })

  it('returns empty object for a 500 ApiError (no details)', () => {
    const serverError = new ApiError(500, { code: 'internal_error', message: 'Unexpected error' })
    expect(extractFieldErrors(serverError)).toEqual({})
  })
})

describe('getFieldError', () => {
  it('returns the specific field message when present', () => {
    const error = new ApiError(400, {
      code: 'validation_error',
      details: { regionId: 'must not be null' },
    })
    expect(getFieldError(error, 'regionId')).toBe('must not be null')
  })

  it('returns undefined for a field not in details', () => {
    const error = new ApiError(400, {
      code: 'validation_error',
      details: { regionId: 'must not be null' },
    })
    expect(getFieldError(error, 'outletId')).toBeUndefined()
  })

  it('returns undefined for non-ApiError', () => {
    expect(getFieldError(new Error('oops'), 'name')).toBeUndefined()
  })
})
