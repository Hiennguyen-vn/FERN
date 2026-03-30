import { NavLink, Outlet } from 'react-router-dom'
import { logout } from '@core/auth/auth.service'
import { useAuthStore } from '@core/auth/auth.store'
import { Button } from '@design-system/index'
import { buildNavigation } from '@shared/navigation/navigation.builder'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { useUiStore } from '@app/store/ui.store'

export function AppShell() {
  const principal = useAuthStore((state) => state.principal)
  const { selectedOutletId, outletIds, setSelectedOutletId } = useScopeContext()
  const sidebarCollapsed = useUiStore((state) => state.sidebarCollapsed)
  const toggleSidebar = useUiStore((state) => state.toggleSidebar)
  const navigation = buildNavigation(principal)

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
            <strong>{principal?.displayName ?? principal?.username ?? 'Guest'}</strong>
          </div>
          <div className="topbar-actions">
            <label className="select-wrapper">
              <span>Outlet</span>
              <select
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
            </label>
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
