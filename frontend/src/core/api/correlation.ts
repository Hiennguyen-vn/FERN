import { v4 as uuidv4 } from 'uuid'

/**
 * Generates a fresh correlation ID for every HTTP request.
 *
 * Previous implementation memoized a single UUID per session, which made it
 * impossible to correlate individual requests in backend logs. Now each call
 * returns a new UUID, matching the standard per-request correlation pattern.
 */
export function getCorrelationId(): string {
  return uuidv4()
}

/**
 * @deprecated No-op — kept for backward compatibility. Correlation IDs are now per-request.
 */
export function resetCorrelationId(): void {
  // no-op: correlation IDs are generated per-request
}
