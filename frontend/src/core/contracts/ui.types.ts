export type UiPersona = 'system_admin' | 'finance' | 'outlet_manager' | 'staff' | 'operations'
export type UiTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger'

export interface UiScopeSummary {
  title: string
  subtitle: string
  chips: string[]
}

export interface UiKpiCard {
  id: string
  label: string
  value: string
  tone: UiTone
  detail?: string
}

export interface UiQueueCard {
  id: string
  title: string
  count: number
  description: string
  href: string
  tone: UiTone
}

export interface UiAlertCard {
  id: string
  title: string
  message: string
  tone: UiTone
  href?: string
}

export interface UiQuickAction {
  id: string
  title: string
  description: string
  href: string
  tone: UiTone
}

export interface UiModuleEntry {
  id: string
  title: string
  description: string
  href: string
}

export interface ActionHubResponse {
  persona: UiPersona
  scopeSummary: UiScopeSummary
  kpis: UiKpiCard[]
  queues: UiQueueCard[]
  alerts: UiAlertCard[]
  quickActions: UiQuickAction[]
  modules: UiModuleEntry[]
}

export interface UiScopeOption {
  value: number
  label: string
}

export interface ShellContextResponse {
  principalLabel: string
  roleLabel: string
  scopeChips: string[]
  availableRegions: UiScopeOption[]
  availableOutlets: UiScopeOption[]
}
