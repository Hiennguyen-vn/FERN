import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { hasAllPermissions } from '@core/permissions/permission.checker'
import type { PermissionCode } from '@core/permissions/permission.types'
import { useAuthStore } from '@core/auth/auth.store'

interface RequirePermissionProps {
  permissions: PermissionCode[]
  children?: ReactNode
}

export function RequirePermission({ permissions, children }: RequirePermissionProps) {
  const principal = useAuthStore((state) => state.principal)

  if (!hasAllPermissions(principal, permissions)) {
    return <Navigate to="/unauthorized" replace />
  }

  return children ? <>{children}</> : <Outlet />
}
