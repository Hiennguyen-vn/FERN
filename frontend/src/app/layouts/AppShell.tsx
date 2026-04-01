import { useEffect } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { logout } from '@core/auth/auth.service'
import { useAuthStore } from '@core/auth/auth.store'
import { useSessionGuard } from '@core/auth/useSessionGuard'
import { orgApi } from '@modules/org/api/org.api'
import { Button } from '@design-system/index'
import { buildNavigation } from '@shared/navigation/navigation.builder'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { useUiStore } from '@app/store/ui.store'

export function AppShell() {
  const principal = useAuthStore((state) => state.principal)
  const {
    selectedOutletId,
    selectedRegionId,
    outletIds,
    regionIds,
    setSelectedOutletId,
    setSelectedRegionId,
  } = useScopeContext()
  const sidebarCollapsed = useUiStore((state) => state.sidebarCollapsed)
  const toggleSidebar = useUiStore((state) => state.toggleSidebar)
  const navigation = buildNavigation(principal)

  // L-01: Proactively refresh token when tab regains focus after inactivity.
  useSessionGuard()

  // Auto-resolve region for outlet-only users:
  // If the user has no explicit region scope but has a selected outlet,
  // look up the outlet to infer its parent regionId and set it.
  useEffect(() => {
    if (regionIds.length > 0 || !selectedOutletId) {
      return
    }

    orgApi
      .getOutlet(selectedOutletId)
      .then((outlet) => {
        if (outlet.regionId) {
          setSelectedRegionId(outlet.regionId)
        }
      })
      .catch(() => {
        // Non-critical — user can still operate without auto-resolved region.
      })
  }, [selectedOutletId, regionIds.length, setSelectedRegionId])

  return (
    <div className="app-shell">
      <aside className={`sidebar ${sidebarCollapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-brand">
          <div className="sidebar-brand-copy">
            <p className="eyebrow">FERN</p>
            {!sidebarCollapsed ? <strong>Operations Console</strong> : null}
          </div>
          <button aria-label="Toggle sidebar" className="icon-button" onClick={toggleSidebar} type="button">
            {sidebarCollapsed ? '›' : '‹'}
          </button>
        </div>
        <nav className="nav-list" aria-label="Main navigation">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="shell-content">
        <header className="topbar">
          <div className="topbar-user">
            <p className="eyebrow">Signed in</p>
            <strong title={principal?.username}>{principal?.displayName ?? principal?.username ?? 'Guest'}</strong>
          </div>
          <div className="topbar-actions">
            {regionIds.length > 0 || selectedRegionId !== null ? (
              <div className="select-wrapper">
                <label htmlFor="topbar-region-select">Region</label>
                <select
                  id="topbar-region-select"
                  value={selectedRegionId ?? ''}
                  onChange={(event) =>
                    setSelectedRegionId(event.target.value ? Number(event.target.value) : null)
                  }
                >
                  <option value="">No region</option>
                  {(regionIds.length > 0 ? regionIds : selectedRegionId ? [selectedRegionId] : []).map((regionId) => (
                    <option key={regionId} value={regionId}>
                      Region #{regionId}
                    </option>
                  ))}
                </select>
              </div>
            ) : null}
            <div className="select-wrapper">
              <label htmlFor="topbar-outlet-select">Outlet</label>
              <select
                id="topbar-outlet-select"
                value={selectedOutletId ?? ''}
                onChange={(event) =>
                  setSelectedOutletId(event.target.value ? Number(event.target.value) : null)
                }
              >
                <option value="">No outlet</option>
                {outletIds.map((outletId) => (
                  <option key={outletId} value={outletId}>
                    Outlet #{outletId}
                  </option>
                ))}
              </select>
            </div>
            <Button onClick={() => void logout()} size="sm" variant="secondary">
              Logout
            </Button>
          </div>
        </header>
        <main className="shell-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
