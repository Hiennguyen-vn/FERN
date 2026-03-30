import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { hasScopeAccess } from '@core/scopes/scope.checker'
import type { ScopeAccessRequirement } from '@core/scopes/scope.types'
import { useAuthStore } from '@core/auth/auth.store'

interface RequireScopeProps {
  requirement: ScopeAccessRequirement
  children?: ReactNode
}

export function RequireScope({ requirement, children }: RequireScopeProps) {
  const principal = useAuthStore((state) => state.principal)

  if (!hasScopeAccess(principal, requirement)) {
    return <Navigate to="/unauthorized" replace />
  }

  return children ? <>{children}</> : <Outlet />
}
