import type { ShellContext, ActionHub, ScopeOption, ModuleEntry } from '@/types/shell';

export const mockScopeTree: ScopeOption[] = [
  {
    id: 'system',
    name: 'All Regions',
    level: 'system',
    children: [
      {
        id: 'region-central',
        name: 'Central Region',
        level: 'region',
        parentId: 'system',
        children: [
          { id: 'outlet-001', name: 'Downtown Flagship', level: 'outlet', parentId: 'region-central' },
          { id: 'outlet-002', name: 'Riverside Branch', level: 'outlet', parentId: 'region-central' },
          { id: 'outlet-003', name: 'Mall Kiosk A', level: 'outlet', parentId: 'region-central' },
        ],
      },
      {
        id: 'region-north',
        name: 'North Region',
        level: 'region',
        parentId: 'system',
        children: [
          { id: 'outlet-004', name: 'Uptown Express', level: 'outlet', parentId: 'region-north' },
          { id: 'outlet-005', name: 'Station Café', level: 'outlet', parentId: 'region-north' },
        ],
      },
      {
        id: 'region-south',
        name: 'South Region',
        level: 'region',
        parentId: 'system',
        children: [
          { id: 'outlet-006', name: 'Harbor View', level: 'outlet', parentId: 'region-south' },
        ],
      },
    ],
  },
];

export const allModules: ModuleEntry[] = [
  { family: 'home', label: 'Dashboard', icon: 'LayoutDashboard', path: '/dashboard', visible: true },
  { family: 'pos', label: 'Point of Sale', icon: 'Monitor', path: '/pos', visible: true },
  { family: 'catalog', label: 'Catalog', icon: 'Package', path: '/catalog', visible: true },
  { family: 'inventory', label: 'Inventory', icon: 'Warehouse', path: '/inventory', visible: true },
  { family: 'procurement', label: 'Procurement', icon: 'ShoppingCart', path: '/procurement', visible: true },
  { family: 'crm', label: 'CRM & Loyalty', icon: 'Heart', path: '/crm', visible: true },
  { family: 'promotions', label: 'Khuyến mãi', icon: 'Percent', path: '/promotions', visible: true },
  { family: 'finance', label: 'Finance', icon: 'Landmark', path: '/finance', visible: true },
  { family: 'hr', label: 'Human Resources', icon: 'Users', path: '/hr', visible: true },
  { family: 'scheduling', label: 'Lịch ca làm', icon: 'CalendarClock', path: '/scheduling', visible: true },
  { family: 'workforce', label: 'Workforce', icon: 'Clock', path: '/workforce', visible: true },
  { family: 'org', label: 'Organization', icon: 'Building2', path: '/org', visible: true },
  { family: 'regional-ops', label: 'Regional Ops', icon: 'Map', path: '/regional-ops', visible: true },
  { family: 'reports', label: 'Reports', icon: 'BarChart3', path: '/reports', visible: true },
  { family: 'audit', label: 'Audit Trail', icon: 'ScrollText', path: '/audit', visible: true },
  { family: 'iam', label: 'Access Management', icon: 'Shield', path: '/iam', visible: true },
];

export const mockActionHub: ActionHub = {
  quickActions: [
    { id: 'qa-1', label: 'New Sale', icon: 'Plus', module: 'pos', path: '/pos/new', scope: ['outlet'] },
    { id: 'qa-2', label: 'Stock Check', icon: 'ClipboardCheck', module: 'inventory', path: '/inventory/check', scope: ['outlet', 'region'] },
    { id: 'qa-3', label: 'Daily Report', icon: 'FileText', module: 'reports', path: '/reports/daily', scope: ['outlet', 'region', 'system'] },
    { id: 'qa-4', label: 'Add Item', icon: 'PackagePlus', module: 'catalog', path: '/catalog/new', scope: ['system'] },
    { id: 'qa-5', label: 'Approve PO', icon: 'CheckCircle', module: 'procurement', path: '/procurement/pending', scope: ['region', 'system'] },
    { id: 'qa-6', label: 'Shift Schedule', icon: 'Clock', module: 'workforce', path: '/workforce/schedule', scope: ['outlet', 'region'] },
  ],
  recentItems: [
    { label: 'Sales Summary – Today', path: '/reports/sales/today', module: 'reports' },
    { label: 'PO-2024-0392', path: '/procurement/po/392', module: 'procurement' },
    { label: 'Menu: Lunch Specials', path: '/catalog/menu/lunch', module: 'catalog' },
  ],
};

export function getMockShellContext(scopeLevel: 'system' | 'region' | 'outlet'): ShellContext {
  const scopeMap = {
    system: {
      level: 'system' as const,
    },
    region: {
      level: 'region' as const,
      regionId: 'region-central',
      regionName: 'Central Region',
    },
    outlet: {
      level: 'outlet' as const,
      regionId: 'region-central',
      regionName: 'Central Region',
      outletId: 'outlet-001',
      outletName: 'Downtown Flagship',
    },
  };

  const personaMap = {
    system: { name: 'Sarah Chen', persona: 'System Administrator', email: 'sarah.chen@company.com', initials: 'SC' },
    region: { name: 'Marcus Rivera', persona: 'Regional Manager', email: 'marcus.r@company.com', initials: 'MR' },
    outlet: { name: 'Aisha Patel', persona: 'Outlet Manager', email: 'aisha.p@company.com', initials: 'AP' },
  };

  const p = personaMap[scopeLevel];

  // Outlet managers don't see IAM, org, regional-ops
  const visibleModules = scopeLevel === 'outlet'
    ? allModules.map(m => ({ ...m, visible: !['iam', 'org', 'regional-ops'].includes(m.family) }))
    : scopeLevel === 'region'
    ? allModules.map(m => ({ ...m, visible: m.family !== 'iam' }))
    : allModules;

  return {
    user: {
      id: `user-${scopeLevel}`,
      displayName: p.name,
      email: p.email,
      persona: p.persona,
      avatarInitials: p.initials,
    },
    scope: scopeMap[scopeLevel],
    availableScopes: mockScopeTree,
    modules: visibleModules.filter(m => m.visible),
    permissions: [],
  };
}
