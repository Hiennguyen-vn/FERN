import { usePrincipal } from '@core/auth/auth.selectors'
import { hasPermission } from './permission.checker'

export function usePermission(permission: string): boolean {
  const principal = usePrincipal()
  return hasPermission(principal, permission)
}
