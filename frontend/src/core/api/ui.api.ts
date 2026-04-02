import type { FernPrincipal } from '@core/auth/auth.types'
import { httpClient } from './httpClient'
import { buildNavigation } from '@shared/navigation/navigation.builder'
import type {
  ActionHubResponse,
  ShellContextResponse,
  UiAlertCard,
  UiKpiCard,
  UiModuleEntry,
  UiPersona,
  UiQuickAction,
  UiQueueCard,
} from '@core/contracts/ui.types'

interface UiContextInput {
  principal: FernPrincipal | null
  selectedOutletId?: number | null
  selectedRegionId?: number | null
}

const MODULE_DESCRIPTIONS: Record<string, string> = {
  '/pos': 'Live selling, session control, payment capture, and terminal operations.',
  '/catalog': 'Products, ingredients, recipes, pricing, and availability controls.',
  '/iam': 'Users, assignments, permission scopes, and effective access review.',
  '/audit': 'Security events, audit trails, and request trace investigation.',
  '/org': 'Regions, outlets, exchange rates, and organization structure governance.',
  '/regional-ops': 'Regional dashboards, outlet health, and operating oversight.',
  '/hr': 'Employees, contracts, attendance summaries, and payroll preparation.',
  '/finance': 'Suppliers, payment requests, payroll approvals, and finance configuration.',
  '/procurement': 'Purchase orders, goods receipts, invoices, and supplier payments.',
  '/inventory': 'Stock balances, inventory flows, waste, counts, and adjustments.',
  '/workforce': 'Attendance review, self-service flows, and workforce approvals.',
  '/reports': 'Dashboards, exports, payroll, revenue, and inventory reporting.',
}

function hasPermissionPrefix(principal: FernPrincipal | null, prefixes: string[]) {
  const permissions = principal?.permissions ?? []
  return permissions.some((permission) => prefixes.some((prefix) => permission.startsWith(prefix)))
}

function resolvePersona(principal: FernPrincipal | null): UiPersona {
  if (!principal) {
    return 'operations'
  }

  const normalizedRoles = principal.roles.map((role) => role.toLowerCase())
  if (
    principal.accessibleScope?.system ||
    principal.scopeRoots.system ||
    normalizedRoles.some((role) => role.includes('admin') || role.includes('system') || role.includes('bootstrap'))
  ) {
    return 'system_admin'
  }
  if (hasPermissionPrefix(principal, ['finance.', 'procurement.invoice', 'procurement.payment'])) {
    return 'finance'
  }
  if (hasPermissionPrefix(principal, ['pos.', 'inventory.', 'procurement.purchaseOrder', 'procurement.goodsReceipt'])) {
    return 'outlet_manager'
  }
  if (hasPermissionPrefix(principal, ['hr.attendance', 'hr.shift'])) {
    return 'staff'
  }
  return 'operations'
}

function titleCase(value: string) {
  return value
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ')
}

function resolveRoleLabel(principal: FernPrincipal | null, persona: UiPersona) {
  const primaryRole = principal?.roles[0]
  if (primaryRole) {
    return titleCase(primaryRole)
  }

  switch (persona) {
    case 'system_admin':
      return 'System Admin'
    case 'finance':
      return 'Finance Lead'
    case 'outlet_manager':
      return 'Outlet Operations'
    case 'staff':
      return 'Self-Service Staff'
    default:
      return 'Operations User'
  }
}

function buildScopeSummary({
  principal,
  selectedOutletId,
  selectedRegionId,
}: UiContextInput): ActionHubResponse['scopeSummary'] {
  const accessibleScope = principal?.accessibleScope ?? principal?.scopeRoots ?? { system: false, regions: [], outlets: [] }
  const chips = [
    accessibleScope.system ? 'Enterprise scope' : null,
    selectedRegionId ? `Region #${selectedRegionId}` : accessibleScope.regions.length > 0 ? `${accessibleScope.regions.length} region scopes` : 'No region selected',
    selectedOutletId ? `Outlet #${selectedOutletId}` : accessibleScope.outlets.length > 0 ? `${accessibleScope.outlets.length} outlet scopes` : 'No outlet selected',
  ].filter((value): value is string => Boolean(value))

  return {
    title: accessibleScope.system ? 'Full enterprise coverage' : 'Scoped operating context',
    subtitle:
      selectedRegionId || selectedOutletId
        ? 'Your action surfaces, queues, and quick links are aligned to the current working scope.'
        : 'Select a region or outlet to tighten operational context before acting on live workflows.',
    chips,
  }
}

function buildModuleEntries(principal: FernPrincipal | null): UiModuleEntry[] {
  return buildNavigation(principal).map((item) => ({
    id: item.to,
    title: item.label,
    href: item.to,
    description: MODULE_DESCRIPTIONS[item.to] ?? 'Module workspace available in the current permission scope.',
  }))
}

function buildQuickActions(principal: FernPrincipal | null): UiQuickAction[] {
  const visibleModulePaths = new Set(buildNavigation(principal).map((item) => item.to))
  const actions: UiQuickAction[] = [
    {
      id: 'open-pos',
      href: '/pos',
      title: 'Open POS workspace',
      description: 'Launch the live terminal workspace, payment flows, and current session controls.',
      tone: 'primary',
    },
    {
      id: 'create-po',
      href: '/procurement',
      title: 'Create purchase order',
      description: 'Start a procurement request aligned to the active outlet or regional context.',
      tone: 'warning',
    },
    {
      id: 'check-stock',
      href: '/inventory',
      title: 'Check stock balances',
      description: 'Review live balances before ordering, transfer planning, or exception handling.',
      tone: 'success',
    },
    {
      id: 'review-attendance',
      href: '/workforce',
      title: 'Review attendance',
      description: 'Open the approval queue for workforce attendance and exception follow-up.',
      tone: 'neutral',
    },
    {
      id: 'open-reports',
      href: '/reports',
      title: 'Open reports center',
      description: 'Jump to analytics, export jobs, and executive reporting surfaces.',
      tone: 'neutral',
    },
    {
      id: 'investigate-audit',
      href: '/audit/events',
      title: 'Investigate audit trail',
      description: 'Inspect audit events and request traces when operations or security need explanation.',
      tone: 'danger',
    },
    {
      id: 'regional-outlets',
      href: '/regional-ops/outlets',
      title: 'Review regional outlets',
      description: 'Scan outlet readiness, anomalies, and escalation signals for the active region.',
      tone: 'neutral',
    },
  ]

  if (visibleModulePaths.has('/hr')) {
    actions.push({
      id: 'prepare-payroll',
      href: '/hr/payroll-preparation',
      title: 'Prepare payroll draft',
      description: 'Move from attendance outcomes into a payroll preparation run for the current scope.',
      tone: 'primary',
    })
  }
  if (visibleModulePaths.has('/iam')) {
    actions.push({
      id: 'open-iam',
      href: '/iam',
      title: 'Open IAM console',
      description: 'Inspect user assignments, scoped permissions, and effective access posture.',
      tone: 'neutral',
    })
  }
  if (visibleModulePaths.has('/finance')) {
    actions.push(
      {
        id: 'review-payroll-approvals',
        href: '/finance',
        title: 'Review payroll approvals',
        description: 'Review payroll decisions that are pending finance sign-off.',
        tone: 'warning',
      },
      {
        id: 'browse-suppliers',
        href: '/finance/suppliers',
        title: 'Browse suppliers',
        description: 'Open supplier master data and related payment context.',
        tone: 'neutral',
      },
      {
        id: 'manage-payroll-periods',
        href: '/finance/payroll-periods',
        title: 'Manage payroll periods',
        description: 'Create or advance payroll periods without leaving the finance workspace.',
        tone: 'neutral',
      },
    )
  }

  return actions.filter((action) => {
    const topLevelPath = `/${action.href.split('/').filter(Boolean)[0] ?? ''}`
    return visibleModulePaths.has(topLevelPath)
  })
}

function buildKpis(input: UiContextInput, modules: UiModuleEntry[], quickActions: UiQuickAction[]): UiKpiCard[] {
  const accessibleScope = input.principal?.accessibleScope ?? input.principal?.scopeRoots ?? { system: false, regions: [], outlets: [] }
  const isScoped = Boolean(input.selectedRegionId || input.selectedOutletId)

  return [
    {
      id: 'coverage',
      label: 'Coverage',
      value: accessibleScope.system ? 'Enterprise' : `${modules.length} modules`,
      tone: 'primary',
      detail: accessibleScope.system ? 'System-wide visibility enabled' : 'Modules visible in the current role scope',
    },
    {
      id: 'regions',
      label: 'Regional span',
      value: `${accessibleScope.regions.length}`,
      tone: 'success',
      detail: isScoped ? 'Current view is narrowed to the selected operational lens' : 'No region narrowed yet',
    },
    {
      id: 'outlets',
      label: 'Outlet span',
      value: `${accessibleScope.outlets.length}`,
      tone: 'warning',
      detail: input.selectedOutletId ? `Acting on outlet #${input.selectedOutletId}` : 'Outlet selection still broad',
    },
    {
      id: 'actions',
      label: 'Ready actions',
      value: `${quickActions.length}`,
      tone: 'neutral',
      detail: 'Role-aware shortcuts available from this surface',
    },
  ]
}

function buildQueues(principal: FernPrincipal | null): UiQueueCard[] {
  const visibleModulePaths = new Set(buildNavigation(principal).map((item) => item.to))
  const queues: UiQueueCard[] = []

  if (visibleModulePaths.has('/procurement')) {
    queues.push({
      id: 'procurement',
      title: 'Procurement approvals',
      count: 3,
      description: 'Purchase orders, receipts, and invoice checkpoints waiting for action.',
      href: '/procurement',
      tone: 'warning',
    })
  }
  if (visibleModulePaths.has('/inventory')) {
    queues.push({
      id: 'inventory',
      title: 'Inventory exceptions',
      count: 2,
      description: 'Adjustments, waste, and count variances needing confirmation.',
      href: '/inventory',
      tone: 'success',
    })
  }
  if (visibleModulePaths.has('/workforce')) {
    queues.push({
      id: 'workforce',
      title: 'Attendance review',
      count: 4,
      description: 'Attendance records and schedule exceptions pending review.',
      href: '/workforce',
      tone: 'neutral',
    })
  }
  if (visibleModulePaths.has('/finance')) {
    queues.push({
      id: 'finance',
      title: 'Finance decisions',
      count: 2,
      description: 'Payroll and supplier payment items waiting for finance acknowledgement.',
      href: '/finance',
      tone: 'primary',
    })
  }
  if (visibleModulePaths.has('/iam')) {
    queues.push({
      id: 'iam',
      title: 'Access governance',
      count: 1,
      description: 'User access and policy review tasks surfaced for administrators.',
      href: '/iam',
      tone: 'danger',
    })
  }

  return queues
}

function buildAlerts(input: UiContextInput): UiAlertCard[] {
  const alerts: UiAlertCard[] = []
  const principal = input.principal

  if (!input.selectedRegionId && !input.selectedOutletId && !principal?.accessibleScope?.system) {
    alerts.push({
      id: 'scope',
      title: 'Scope selection recommended',
      message: 'Choose a region or outlet in the shell before executing operational workflows.',
      tone: 'warning',
    })
  }

  if (!principal || principal.permissions.length === 0) {
    alerts.push({
      id: 'permissions',
      title: 'No published permissions',
      message: 'This account has no published UI permissions yet. Most modules will remain hidden or read-only.',
      tone: 'danger',
    })
  } else {
    alerts.push({
      id: 'contracts',
      title: 'Capability boundary respected',
      message: 'Read-only and unpublished flows remain visually available but action-gated until backend contracts are published.',
      tone: 'neutral',
    })
  }

  return alerts
}

export function buildFallbackActionHub(input: UiContextInput): ActionHubResponse {
  const persona = resolvePersona(input.principal)
  const modules = buildModuleEntries(input.principal)
  const quickActions = buildQuickActions(input.principal)

  return {
    persona,
    scopeSummary: buildScopeSummary(input),
    kpis: buildKpis(input, modules, quickActions),
    queues: buildQueues(input.principal),
    alerts: buildAlerts(input),
    quickActions,
    modules,
  }
}

export function buildFallbackShellContext(input: UiContextInput): ShellContextResponse {
  const accessibleScope = input.principal?.accessibleScope ?? input.principal?.scopeRoots ?? { system: false, regions: [], outlets: [] }
  const persona = resolvePersona(input.principal)

  return {
    principalLabel: input.principal?.displayName ?? input.principal?.username ?? 'Guest',
    roleLabel: resolveRoleLabel(input.principal, persona),
    scopeChips: [
      accessibleScope.system ? 'Enterprise' : null,
      input.selectedRegionId ? `Region #${input.selectedRegionId}` : accessibleScope.regions.length > 0 ? `${accessibleScope.regions.length} regions` : 'No region',
      input.selectedOutletId ? `Outlet #${input.selectedOutletId}` : accessibleScope.outlets.length > 0 ? `${accessibleScope.outlets.length} outlets` : 'No outlet',
    ].filter((chip): chip is string => Boolean(chip)),
    availableRegions: accessibleScope.regions.map((regionId) => ({ value: regionId, label: `Region #${regionId}` })),
    availableOutlets: accessibleScope.outlets.map((outletId) => ({ value: outletId, label: `Outlet #${outletId}` })),
  }
}

export const uiApi = {
  async getActionHub(): Promise<ActionHubResponse> {
    const response = await httpClient.get<ActionHubResponse>('/ui/action-hub')
    return response.data
  },
  async getShellContext(): Promise<ShellContextResponse> {
    const response = await httpClient.get<ShellContextResponse>('/ui/shell-context')
    return response.data
  },
}
