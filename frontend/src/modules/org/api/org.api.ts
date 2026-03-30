import { gatewayClient } from '@core/api/gatewayClient'
import type { OrgOutlet, OrgRegion } from '../model/org.types'

export const orgApi = {
  getRegion(regionId: number) {
    return gatewayClient.get<OrgRegion>(`/regions/${regionId}`).then((response) => response.data)
  },

  getOutlet(outletId: number) {
    return gatewayClient.get<OrgOutlet>(`/outlets/${outletId}`).then((response) => response.data)
  },
}
