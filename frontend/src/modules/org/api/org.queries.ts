export const orgQueryKeys = {
  regionList: (params: { search?: string; page?: number; size?: number }) => ['org', 'regions', 'list', params] as const,
  regionDetail: (regionId: number) => ['org', 'regions', 'detail', regionId] as const,
  outletList: (params: { regionId?: number; search?: string; status?: string; page?: number; size?: number }) =>
    ['org', 'outlets', 'list', params] as const,
  outletDetail: (outletId: number) => ['org', 'outlets', 'detail', outletId] as const,
}
