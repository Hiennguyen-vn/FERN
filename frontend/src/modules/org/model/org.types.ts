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

export interface OrgOutlet {
  id: number
  regionId: number
  code: string
  name: string
  status: string
  address: string | null
  phone: string | null
  email: string | null
  openedAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
}
