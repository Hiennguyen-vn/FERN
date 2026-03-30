export const orgQueryKeys = {
  regionDetail: (regionId: number) => ['org', 'regions', 'detail', regionId] as const,
  outletDetail: (outletId: number) => ['org', 'outlets', 'detail', outletId] as const,
}
