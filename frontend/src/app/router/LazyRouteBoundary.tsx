import type { ReactNode } from 'react'
import { Suspense } from 'react'
import { Outlet } from 'react-router-dom'
import { ModuleErrorBoundary } from '@core/errors/ModuleErrorBoundary'
import { Card } from '@design-system/index'

interface RouteLoadingFallbackProps {
  label?: string
}

interface LazyRouteBoundaryProps extends RouteLoadingFallbackProps {
  children?: ReactNode
  moduleName: string
}

export function RouteLoadingFallback({ label = 'Loading module' }: RouteLoadingFallbackProps) {
  return (
    <section className="page-stack">
      <Card title={label}>
        <p className="muted-text">Loading module resources...</p>
      </Card>
    </section>
  )
}

export function LazyRouteBoundary({ children, moduleName, label }: LazyRouteBoundaryProps) {
  return (
    <ModuleErrorBoundary moduleName={moduleName}>
      <Suspense fallback={<RouteLoadingFallback label={label} />}>{children ?? <Outlet />}</Suspense>
    </ModuleErrorBoundary>
  )
}
