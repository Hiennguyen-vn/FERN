import { gatewayClient } from '@core/api/gatewayClient'
import type { PageResponse } from '@core/types/api'
import type { CreateRegionPayload, OrgOutlet, OrgRegion, UpdateRegionPayload, CreateOutletPayload, UpdateOutletPayload } from '../model/org.types'

export const orgApi = {
  listRegions(params?: { search?: string; page?: number; size?: number }) {
    return gatewayClient.get<PageResponse<OrgRegion>>('/regions', { params }).then((response) => response.data)
  },

  createRegion(payload: CreateRegionPayload) {
    return gatewayClient.post<OrgRegion>('/regions', payload).then((response) => response.data)
  },

  getRegion(regionId: number) {
    return gatewayClient.get<OrgRegion>(`/regions/${regionId}`).then((response) => response.data)
  },

  updateRegion(regionId: number, payload: UpdateRegionPayload) {
    return gatewayClient.patch<OrgRegion>(`/regions/${regionId}`, payload).then((response) => response.data)
  },

  listOutlets(params?: { regionId?: number; search?: string; status?: string; page?: number; size?: number }) {
    return gatewayClient.get<PageResponse<OrgOutlet>>('/outlets', { params }).then((response) => response.data)
  },

  createOutlet(payload: CreateOutletPayload) {
    return gatewayClient.post<OrgOutlet>('/outlets', payload).then((response) => response.data)
  },

  getOutlet(outletId: number) {
    return gatewayClient.get<OrgOutlet>(`/outlets/${outletId}`).then((response) => response.data)
  },

  updateOutlet(outletId: number, payload: UpdateOutletPayload) {
    return gatewayClient.patch<OrgOutlet>(`/outlets/${outletId}`, payload).then((response) => response.data)
  },
}
