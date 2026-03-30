import { gatewayClient } from '@core/api/gatewayClient'
import type { RegionalOutlet, RegionalRegion } from '../model/regionalOps.types'

export const regionalOpsApi = {
  getOutlet(outletId: number) {
    return gatewayClient.get<RegionalOutlet>(`/outlets/${outletId}`).then((response) => response.data)
  },

  getRegion(regionId: number) {
    return gatewayClient.get<RegionalRegion>(`/regions/${regionId}`).then((response) => response.data)
  },
}
