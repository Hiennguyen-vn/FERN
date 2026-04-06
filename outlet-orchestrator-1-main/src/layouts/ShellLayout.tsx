import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { AppSidebar } from '@/components/shell/AppSidebar';
import { TopBar } from '@/components/shell/TopBar';
import { ScopeSelector } from '@/components/shell/ScopeSelector';
import { QuickActionsPanel } from '@/components/shell/QuickActionsPanel';
import { NotificationPanel } from '@/components/shell/NotificationPanel';
import { getMockShellContext, mockScopeTree, mockActionHub } from '@/data/mock-shell';
import type { ShellScope, ScopeLevel, ModuleFamily } from '@/types/shell';

const ROUTE_META: Record<string, { title: string; breadcrumbs: string[] }> = {
  '/dashboard': { title: 'Outlet Control Center', breadcrumbs: ['Home', 'Dashboard'] },
  '/pos': { title: 'Point of Sale', breadcrumbs: ['POS'] },
  '/inventory': { title: 'Inventory', breadcrumbs: ['Operations', 'Inventory'] },
  '/procurement': { title: 'Procurement', breadcrumbs: ['Operations', 'Procurement'] },
  '/catalog': { title: 'Catalog', breadcrumbs: ['Operations', 'Catalog'] },
  '/reports': { title: 'Reports', breadcrumbs: ['Insights', 'Reports'] },
  '/audit': { title: 'Audit Trail', breadcrumbs: ['Insights', 'Audit'] },
  '/iam': { title: 'Access Management', breadcrumbs: ['Administration', 'IAM'] },
  '/finance': { title: 'Finance', breadcrumbs: ['Finance & People', 'Finance'] },
  '/hr': { title: 'Human Resources', breadcrumbs: ['Finance & People', 'HR'] },
  '/settings': { title: 'Settings', breadcrumbs: ['Administration', 'Settings'] },
};

const FAMILY_TO_PATH: Record<string, string> = {
  home: '/dashboard',
  pos: '/pos',
  inventory: '/inventory',
  procurement: '/procurement',
  catalog: '/catalog',
  reports: '/reports',
  audit: '/audit',
  iam: '/iam',
  finance: '/finance',
  hr: '/hr',
};

const PATH_TO_FAMILY: Record<string, string> = Object.fromEntries(
  Object.entries(FAMILY_TO_PATH).map(([k, v]) => [v, k])
);

export default function ShellLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [scopeLevel, setScopeLevel] = useState<ScopeLevel>('outlet');
  const [customScope, setCustomScope] = useState<ShellScope | null>(null);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [scopeOpen, setScopeOpen] = useState(false);
  const [quickActionsOpen, setQuickActionsOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const shellContext = getMockShellContext(scopeLevel);
  const currentScope = customScope || shellContext.scope;

  // Find meta based on current path
  const basePath = '/' + location.pathname.split('/')[1];
  const meta = ROUTE_META[basePath] || { title: 'OpsCenter', breadcrumbs: [] };
  const activeFamily = PATH_TO_FAMILY[basePath];

  const handleScopeChange = (newScope: ShellScope) => {
    setCustomScope(newScope);
    setScopeLevel(newScope.level);
  };

  const handleNavigate = (family: ModuleFamily) => {
    const path = FAMILY_TO_PATH[family];
    if (path) navigate(path);
  };

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <AppSidebar
        modules={shellContext.modules}
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
        onNavigate={handleNavigate}
        activeFamily={activeFamily}
      />

      <div className="flex-1 flex flex-col min-w-0">
        <TopBar
          pageTitle={meta.title}
          breadcrumbs={meta.breadcrumbs}
          scope={currentScope}
          user={shellContext.user}
          sidebarCollapsed={sidebarCollapsed}
          onToggleSidebar={() => setSidebarCollapsed(!sidebarCollapsed)}
          onOpenScope={() => setScopeOpen(true)}
          onOpenQuickActions={() => setQuickActionsOpen(true)}
          onOpenNotifications={() => setNotificationsOpen(true)}
          onLogout={() => navigate('/login')}
          notificationCount={3}
        />

        <main className="flex-1 overflow-y-auto flex flex-col">
          <Outlet context={{ scope: currentScope, user: shellContext.user }} />
        </main>
      </div>

      <ScopeSelector
        open={scopeOpen}
        onClose={() => setScopeOpen(false)}
        currentScope={currentScope}
        scopeTree={mockScopeTree}
        onScopeChange={handleScopeChange}
      />
      <QuickActionsPanel
        open={quickActionsOpen}
        onClose={() => setQuickActionsOpen(false)}
        actionHub={mockActionHub}
        scope={currentScope}
      />
      <NotificationPanel
        open={notificationsOpen}
        onClose={() => setNotificationsOpen(false)}
      />
    </div>
  );
}
