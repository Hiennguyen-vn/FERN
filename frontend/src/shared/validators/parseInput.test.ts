import { describe, expect, it } from 'vitest'
import {
  isValidDateTime,
  parseDecimalMin,
  parseNonNegativeDecimal,
  parsePositiveInt,
  toOptionalNumber,
} from './parseInput'

describe('toOptionalNumber', () => {
  it('returns undefined for empty string', () => {
    expect(toOptionalNumber('')).toBeUndefined()
  })

  it('returns undefined for whitespace-only string', () => {
    expect(toOptionalNumber('   ')).toBeUndefined()
  })

  it('returns undefined for null', () => {
    expect(toOptionalNumber(null)).toBeUndefined()
  })

  it('returns undefined for undefined', () => {
    expect(toOptionalNumber(undefined)).toBeUndefined()
  })

  it('returns undefined for non-numeric string', () => {
    expect(toOptionalNumber('abc')).toBeUndefined()
  })

  it('returns the number for a valid integer string', () => {
    expect(toOptionalNumber('42')).toBe(42)
  })

  it('returns the number for a valid decimal string', () => {
    expect(toOptionalNumber('3.14')).toBe(3.14)
  })

  it('returns 0 for "0" (zero is finite)', () => {
    expect(toOptionalNumber('0')).toBe(0)
  })

  it('returns a negative number for negative input (no positivity constraint)', () => {
    expect(toOptionalNumber('-5')).toBe(-5)
  })
})

describe('parsePositiveInt', () => {
  it('returns the number for a valid positive integer', () => {
    expect(parsePositiveInt('1')).toBe(1)
    expect(parsePositiveInt('999')).toBe(999)
  })

  it('returns null for zero', () => {
    expect(parsePositiveInt('0')).toBeNull()
  })

  it('returns null for a negative integer', () => {
    expect(parsePositiveInt('-1')).toBeNull()
  })

  it('returns null for a decimal', () => {
    expect(parsePositiveInt('1.5')).toBeNull()
  })

  it('returns null for an empty string', () => {
    expect(parsePositiveInt('')).toBeNull()
  })

  it('returns null for a non-numeric string', () => {
    expect(parsePositiveInt('abc')).toBeNull()
  })

  // Key use case: backend ID fields @NotNull Long
  it('accepts stringified database IDs', () => {
    expect(parsePositiveInt('101')).toBe(101)
    expect(parsePositiveInt('99999')).toBe(99999)
  })
})

describe('parseDecimalMin', () => {
  it('returns the number when value >= min', () => {
    expect(parseDecimalMin('0.01', 0.01)).toBe(0.01)
    expect(parseDecimalMin('100', 0.01)).toBe(100)
  })

  it('returns the number at the exact minimum', () => {
    expect(parseDecimalMin('0.0001', 0.0001)).toBe(0.0001)
  })

  it('returns null when value < min', () => {
    expect(parseDecimalMin('0.009', 0.01)).toBeNull()
    expect(parseDecimalMin('0', 0.01)).toBeNull()
  })

  it('returns null for NaN input', () => {
    expect(parseDecimalMin('abc', 0)).toBeNull()
  })

  // Number('') = 0, which is valid for min=0; use toOptionalNumber first if "empty = absent" is needed
  it('returns 0 for empty string when min is 0 (Number("") === 0)', () => {
    expect(parseDecimalMin('', 0)).toBe(0)
  })

  it('returns null for empty string when min is 0.01', () => {
    expect(parseDecimalMin('', 0.01)).toBeNull()
  })

  it('returns null for negative value when min is 0', () => {
    expect(parseDecimalMin('-0.01', 0)).toBeNull()
  })

  // Backend @DecimalMin("0.00") — zero is valid
  it('allows zero when min is 0', () => {
    expect(parseDecimalMin('0', 0)).toBe(0)
  })

  // Backend @DecimalMin("0.01") — e.g. payment amount
  it('rejects 0 when min is 0.01', () => {
    expect(parseDecimalMin('0', 0.01)).toBeNull()
  })

  // Backend @DecimalMin("0.0001") — e.g. qty ordered
  it('accepts 0.0001 when min is 0.0001', () => {
    expect(parseDecimalMin('0.0001', 0.0001)).toBe(0.0001)
  })

  it('rejects 0.00009 when min is 0.0001', () => {
    expect(parseDecimalMin('0.00009', 0.0001)).toBeNull()
  })
})

describe('parseNonNegativeDecimal', () => {
  it('returns value for zero', () => {
    expect(parseNonNegativeDecimal('0')).toBe(0)
  })

  it('returns value for positive decimal', () => {
    expect(parseNonNegativeDecimal('50.00')).toBe(50)
  })

  it('returns null for negative', () => {
    expect(parseNonNegativeDecimal('-0.01')).toBeNull()
  })

  it('returns null for NaN', () => {
    expect(parseNonNegativeDecimal('xyz')).toBeNull()
  })

  // Number('') = 0; an empty field maps to 0, which passes the >= 0 constraint
  it('returns 0 for empty string (Number("") === 0, which is >= 0)', () => {
    expect(parseNonNegativeDecimal('')).toBe(0)
  })
})

describe('isValidDateTime', () => {
  it('returns true for a valid datetime-local value', () => {
    expect(isValidDateTime('2026-04-01T10:00')).toBe(true)
  })

  it('returns true for a valid ISO string', () => {
    expect(isValidDateTime('2026-04-01T10:00:00.000Z')).toBe(true)
  })

  it('returns true for a date-only string', () => {
    expect(isValidDateTime('2026-04-01')).toBe(true)
  })

  it('returns false for empty string', () => {
    expect(isValidDateTime('')).toBe(false)
  })

  it('returns false for whitespace-only string', () => {
    expect(isValidDateTime('   ')).toBe(false)
  })

  it('returns false for a non-date string', () => {
    expect(isValidDateTime('not-a-date')).toBe(false)
  })
})
