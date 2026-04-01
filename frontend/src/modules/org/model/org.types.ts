export interface OrgRegion {
  id: number
  code: string
  parentRegionId: number | null
  currencyCode: string
  name: string
  taxCode: string | null
  timezoneName: string
  createdAt: string
  updatedAt: string
}

/** Exact enum values from backend OutletStatus. */
export type OrgOutletStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'CLOSED'

export interface OrgOutlet {
  id: number
  regionId: number
  code: string
  name: string
  status: OrgOutletStatus
  address: string | null
  phone: string | null
  email: string | null
  openedAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateRegionPayload {
  code: string
  parentRegionId?: number | null
  currencyCode: string
  name: string
  taxCode?: string | null
  timezoneName: string
}

export interface UpdateRegionPayload {
  parentRegionId?: number | null
  currencyCode?: string
  name?: string
  taxCode?: string | null
  timezoneName?: string
}

export interface CreateOutletPayload {
  regionId: number
  code: string
  name: string
  /** @NotNull on backend — omitting causes 400 validation_error. */
  status: OrgOutletStatus
  address?: string | null
  phone?: string | null
  email?: string | null
  /** LocalDate serialised as "YYYY-MM-DD" */
  openedAt?: string | null
  /** LocalDate serialised as "YYYY-MM-DD" */
  closedAt?: string | null
}

/**
 * Backend UpdateOutletRequest fields — `code` is immutable after creation and
 * is NOT accepted by the PATCH endpoint.
 */
export interface UpdateOutletPayload {
  regionId?: number
  name?: string
  /** OutletStatus — backend @NotNull only on create, optional on update. */
  status?: OrgOutletStatus
  address?: string | null
  phone?: string | null
  email?: string | null
  /** LocalDate serialised as "YYYY-MM-DD" */
  openedAt?: string | null
  /** LocalDate serialised as "YYYY-MM-DD" */
  closedAt?: string | null
}
