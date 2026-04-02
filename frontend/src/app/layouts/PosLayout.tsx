import type { PropsWithChildren } from 'react'
import { AppIcon } from '@app/components/AppIcon'
import { useAuthStore } from '@core/auth/auth.store'
import { NetworkOfflineBanner } from '@design-system/index'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { OfflineIndicator } from '@modules/pos/offline/OfflineIndicator'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'

interface PosLayoutProps extends PropsWithChildren {
  title: string
}

export function PosLayout({ title, children }: PosLayoutProps) {
  const principal = useAuthStore((state) => state.principal)
  const { selectedOutletId, selectedRegionId } = useScopeContext()
  const isOnline = useNetworkStatus()

  return (
    <section className="page-stack pos-page">
      {!isOnline ? <NetworkOfflineBanner /> : null}
      <OfflineIndicator />
      <header className="page-header dashboard-page-header pos-shell-header">
        <div className="pos-shell-overview">
          <div className="page-heading">
            <p className="eyebrow">Precision Atelier</p>
            <h1>{title}</h1>
            <p className="muted-text">Outlet-first selling workspace with live session, guest context, and payment control.</p>
          </div>
          <div className="page-stack pos-shell-meta">
            <div className="stack-inline">
              {selectedRegionId ? (
                <span className="meta-chip">
                  <AppIcon name="map" size="sm" />
                  Region #{selectedRegionId}
                </span>
              ) : null}
              {selectedOutletId ? (
                <span className="meta-chip">
                  <AppIcon name="storefront" size="sm" />
                  Outlet #{selectedOutletId}
                </span>
              ) : null}
              <span className={isOnline ? 'meta-chip meta-chip-success' : 'meta-chip meta-chip-danger'}>
                <AppIcon name={isOnline ? 'wifi' : 'wifi_off'} size="sm" />
                {isOnline ? 'System online' : 'System offline'}
              </span>
            </div>
            <div className="meta-chip">
              <AppIcon name="person" size="sm" />
              {principal?.displayName ?? principal?.username ?? 'Unknown user'}
            </div>
          </div>
        </div>
      </header>
      {children}
    </section>
  )
}
