export const regionalOpsQueryKeys = {
  outletDetail: (outletId: number) => ['regional-ops', 'outlets', 'detail', outletId] as const,
  regionDetail: (regionId: number) => ['regional-ops', 'regions', 'detail', regionId] as const,
}
