import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useScopeContext } from '@core/scopes/useScopeContext'

interface RequireOutletContextProps {
  children?: ReactNode
}

export function RequireOutletContext({ children }: RequireOutletContextProps) {
  const { selectedOutletId, outletIds } = useScopeContext()

  if (!selectedOutletId && outletIds.length > 0) {
    return <Navigate to="/home" replace />
  }

  return children ? <>{children}</> : <Outlet />
}
