import type { FernPrincipal } from '@core/auth/auth.types'

export interface NavigationItem {
  icon?: string
  label: string
  to: string
  visible?: (principal: FernPrincipal | null) => boolean
}
