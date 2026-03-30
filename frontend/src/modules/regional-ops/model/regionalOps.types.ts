export interface RegionalRegion {
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

export interface RegionalOutlet {
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

export interface RegionalContextResolution {
  message: string | null
  resolvedRegionId: number | null
  status: 'invalid' | 'missing' | 'resolved' | 'system-unscoped'
}
