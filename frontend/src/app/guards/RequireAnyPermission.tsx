import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { hasAnyPermissions } from '@core/permissions/permission.checker'
import type { PermissionCode } from '@core/permissions/permission.types'
import { useAuthStore } from '@core/auth/auth.store'

interface RequireAnyPermissionProps {
  permissions: PermissionCode[]
  children?: ReactNode
}

/**
 * Route guard that passes if the principal holds at least one of the given
 * permissions. Use this for module-level guards where several distinct roles
 * can all access the module (e.g. attendance.write OR attendance.review).
 *
 * For routes that require ALL listed permissions use RequirePermission instead.
 */
export function RequireAnyPermission({ permissions, children }: RequireAnyPermissionProps) {
  const principal = useAuthStore((state) => state.principal)

  if (!hasAnyPermissions(principal, permissions)) {
    return <Navigate to="/unauthorized" replace />
  }

  return children ? <>{children}</> : <Outlet />
}
