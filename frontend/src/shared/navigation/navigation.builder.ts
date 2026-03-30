import type { FernPrincipal } from '@core/auth/auth.types'
import { navigationConfig } from './navigation.config'

export function buildNavigation(principal: FernPrincipal | null) {
  return navigationConfig.filter((item) => (item.visible ? item.visible(principal) : true))
}
