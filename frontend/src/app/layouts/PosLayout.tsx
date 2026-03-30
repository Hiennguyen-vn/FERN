import type { PropsWithChildren } from 'react'
import { useAuthStore } from '@core/auth/auth.store'
import { NetworkOfflineBanner } from '@design-system/index'
import { OfflineIndicator } from '@modules/pos/offline/OfflineIndicator'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'

interface PosLayoutProps extends PropsWithChildren {
  title: string
}

export function PosLayout({ title, children }: PosLayoutProps) {
  const principal = useAuthStore((state) => state.principal)
  const isOnline = useNetworkStatus()

  return (
    <section className="page-stack pos-page">
      {!isOnline ? <NetworkOfflineBanner /> : null}
      <OfflineIndicator />
      <header className="page-header dashboard-page-header pos-shell-header">
        <div className="page-heading">
          <p className="eyebrow">POS</p>
          <h1>{title}</h1>
        </div>
        <div className="meta-chip">{principal?.username ?? 'Unknown user'}</div>
      </header>
      {children}
    </section>
  )
}
