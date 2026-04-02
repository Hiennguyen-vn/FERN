import { useEffect } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { AppIcon } from '@app/components/AppIcon'
import { logout } from '@core/auth/auth.service'
import { useAuthStore } from '@core/auth/auth.store'
import { useSessionGuard } from '@core/auth/useSessionGuard'
import { useShellContext } from '@core/api/ui.hooks'
import { orgApi } from '@modules/org/api/org.api'
import { Button } from '@design-system/index'
import { buildNavigation } from '@shared/navigation/navigation.builder'
import { useScopeContext } from '@core/scopes/useScopeContext'
import { useUiStore } from '@app/store/ui.store'

const NAV_DESCRIPTIONS: Record<string, string> = {
  '/home': 'Action hub',
  '/pos': 'Front of house',
  '/catalog': 'Products and pricing',
  '/iam': 'Identity control',
  '/audit': 'Forensic trail',
  '/org': 'Regions and outlets',
  '/regional-ops': 'Regional command',
  '/hr': 'People and payroll',
  '/finance': 'Disbursements and config',
  '/procurement': 'PO and goods receipt',
  '/inventory': 'Stock, count, waste',
  '/workforce': 'Attendance and shifts',
  '/reports': 'Exports and dashboards',
}

function toInitials(label: string) {
  return label
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

export function AppShell() {
  const principal = useAuthStore((state) => state.principal)
  const shellContextQuery = useShellContext()
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
  const shellContext = shellContextQuery.data
  const principalLabel = shellContext?.principalLabel ?? principal?.displayName ?? principal?.username ?? 'Guest'
  const roleLabel = shellContext?.roleLabel ?? 'Operations user'
  const principalInitials = toInitials(principalLabel)
  const regionOptions = [
    ...(shellContext?.availableRegions ?? []).map((option) => ({ ...option })),
    ...((selectedRegionId !== null &&
    !(shellContext?.availableRegions ?? []).some((option) => option.value === selectedRegionId))
      ? [{ value: selectedRegionId, label: `Region #${selectedRegionId}` }]
      : []),
  ]
  const outletOptions = [
    ...(shellContext?.availableOutlets ?? []).map((option) => ({ ...option })),
    ...((selectedOutletId !== null &&
    !(shellContext?.availableOutlets ?? []).some((option) => option.value === selectedOutletId))
      ? [{ value: selectedOutletId, label: `Outlet #${selectedOutletId}` }]
      : []),
  ]

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
        {/* Brand mark */}
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark" aria-hidden="true">
            <AppIcon filled name="restaurant_menu" size="sm" />
          </div>
          {!sidebarCollapsed ? (
            <div className="sidebar-brand-copy">
              <strong>FERN ERP</strong>
              <span className="eyebrow">F&amp;B Platform</span>
            </div>
          ) : null}
          <button
            aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className="icon-button"
            onClick={toggleSidebar}
            type="button"
            style={{ marginLeft: 'auto' }}
          >
            <AppIcon
              name={sidebarCollapsed ? 'keyboard_double_arrow_right' : 'keyboard_double_arrow_left'}
              size="sm"
            />
          </button>
        </div>

        {/* Scope summary */}
        {!sidebarCollapsed ? (
          <div className="shell-quick-summary">
            <p className="eyebrow">My scope</p>
            <strong style={{ fontSize: '0.8rem', color: 'var(--on-surface-variant)' }}>{roleLabel}</strong>
            {(shellContext?.scopeChips ?? []).length > 0 ? (
              <div className="scope-summary-grid" style={{ marginTop: '0.4rem' }}>
                {(shellContext?.scopeChips ?? []).slice(0, 4).map((chip) => (
                  <span className="meta-chip" key={chip} style={{ fontSize: '0.72rem' }}>
                    {chip}
                  </span>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}

        {/* Navigation */}
        <nav aria-label="Main navigation" className="nav-list">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              title={sidebarCollapsed ? item.label : undefined}
            >
              <span aria-hidden="true" className="nav-link-icon">
                <AppIcon name={item.icon ?? 'dashboard'} size="sm" />
              </span>
              {!sidebarCollapsed ? (
                <span className="nav-link-copy">
                  <span className="nav-link-label">{item.label}</span>
                  <span className="nav-link-kicker" style={{ fontSize: '0.7rem', color: 'var(--on-surface-variant)', opacity: 0.72 }}>
                    {NAV_DESCRIPTIONS[item.to] ?? 'Workspace'}
                  </span>
                </span>
              ) : null}
            </NavLink>
          ))}
        </nav>

        {!sidebarCollapsed ? (
          <div className="shell-nav-footer">
            <p className="eyebrow">Guardrails active</p>
            <p className="muted-text" style={{ fontSize: '0.75rem', lineHeight: 1.4 }}>
              Read-only and permission states are enforced across all modules.
            </p>
          </div>
        ) : null}
      </aside>

      <div className="shell-content">
        <header className="topbar">
          <div className="topbar-primary">
            <div aria-hidden="true" className="shell-search">
              <AppIcon name="search" size="sm" />
              <input placeholder="Search..." readOnly type="text" />
            </div>
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
                  <option value="">All regions</option>
                  {(regionOptions.length > 0
                    ? regionOptions
                    : (regionIds.length > 0 ? regionIds : selectedRegionId ? [selectedRegionId] : []).map(
                        (regionId) => ({ value: regionId, label: `Region #${regionId}` }),
                      )
                  ).map((region) => (
                    <option key={region.value} value={region.value}>
                      {region.label}
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
                <option value="">All outlets</option>
                {(outletOptions.length > 0
                  ? outletOptions
                  : outletIds.map((outletId) => ({ value: outletId, label: `Outlet #${outletId}` }))
                ).map((outlet) => (
                  <option key={outlet.value} value={outlet.value}>
                    {outlet.label}
                  </option>
                ))}
              </select>
            </div>
            <div aria-label="Utility actions" className="topbar-utility-buttons">
              <button className="icon-button" title="Notifications" type="button">
                <AppIcon name="notifications" size="sm" />
              </button>
              <button className="icon-button" title="Help" type="button">
                <AppIcon name="help" size="sm" />
              </button>
            </div>
            <div className="topbar-profile">
              <div className="topbar-profile-copy">
                <strong title={principal?.username}>{principalLabel}</strong>
                <span className="muted-text" style={{ fontSize: '0.72rem' }}>{roleLabel}</span>
              </div>
              <div className="topbar-avatar" aria-hidden="true">{principalInitials || 'G'}</div>
            </div>
            <Button onClick={() => void logout()} size="sm" variant="secondary">
              Sign out
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
