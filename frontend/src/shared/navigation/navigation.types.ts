import type { FernPrincipal } from '@core/auth/auth.types'

export interface NavigationItem {
  label: string
  to: string
  visible?: (principal: FernPrincipal | null) => boolean
}
