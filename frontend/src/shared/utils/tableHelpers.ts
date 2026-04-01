/**
 * Returns the emptyTitle and emptyDescription props for a DataTable whose
 * backend endpoint silently returns an empty list when the caller lacks a
 * specific permission (instead of a 403).
 *
 * Backend contract reference:
 *   Some endpoints (e.g. GET /security-events, GET /payroll-runs/:id employees)
 *   return [] rather than 403 for callers outside the required scope or
 *   missing the required permission. The UI must communicate this so users do
 *   not conclude the data genuinely does not exist.
 *
 * @param isMasked        True when backend is expected to return an empty list
 *                        due to a missing permission or scope.
 * @param maskedTitle     emptyTitle to show when masked (e.g. "Results masked by backend").
 * @param maskedDescription  emptyDescription when masked — should name the required permission.
 * @param normalTitle     emptyTitle for a genuine empty result.
 * @param normalDescription  emptyDescription for a genuine empty result.
 */
export function maskedEmptyState(
  isMasked: boolean,
  maskedTitle: string,
  maskedDescription: string,
  normalTitle: string,
  normalDescription: string,
): { emptyTitle: string; emptyDescription: string } {
  return isMasked
    ? { emptyTitle: maskedTitle, emptyDescription: maskedDescription }
    : { emptyTitle: normalTitle, emptyDescription: normalDescription }
}
