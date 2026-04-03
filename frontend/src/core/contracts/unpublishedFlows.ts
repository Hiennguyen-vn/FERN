/**
 * SRS V1 capability map for the current frontend/backend alignment.
 *
 * This file is documentation-only. It is not imported at runtime.
 * Keep it small and accurate so future work starts from the true gap.
 *
 * Last audited: 2026-04-03
 */

export const INTENTIONAL_READ_ONLY_SURFACES = [
  'regional-ops:dashboard-and-oversight-pages',
  'audit:inspection-and-export-surfaces',
  'reports:most-analysis-pages-remain-read-first-by-design',
] as const

export const REMAINING_V1_GAPS = [
  'none-locked-here-track-a-focus-is-defect-fix-and-contract-alignment',
] as const

export const POST_V1_DEFERRED_CAPABILITIES = [
  'refunds-and-reversals',
  'inter-outlet-transfer',
  'central-warehouse-and-bin-management',
  'lot-expiry-traceability',
  'core-accounting-ledger-ap-ar-bank-rec',
  'omnichannel-delivery-aggregator-integration',
] as const
