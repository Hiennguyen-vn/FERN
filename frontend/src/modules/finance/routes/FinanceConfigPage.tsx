import { useState } from 'react'
import { AppIcon } from '@app/components/AppIcon'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import {
  Badge,
  Button,
  Card,
  EntityHeader,
  EmptyState,
  ErrorState,
  FormActions,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { permissionConstants } from '@core/permissions/permission.constants'
import { hasPermission } from '@core/permissions/permission.checker'
import {
  useNumberingRule,
  usePutNumberingRule,
  useSystemPolicy,
  usePutSystemPolicy,
} from '../hooks/useFinance'
import type { NumberingRule, SystemPolicy } from '../model/finance.types'
import { getFinanceErrorMessage } from '../services/financeError.service'

// Known document types from backend
const DOCUMENT_TYPES = ['PAYROLL_RUN', 'SUPPLIER_INVOICE', 'PURCHASE_ORDER', 'GOODS_RECEIPT']

// Known system policy keys from backend
const POLICY_KEYS = ['DEFAULT_CURRENCY', 'TAX_ROUNDING_MODE', 'PAYROLL_APPROVAL_REQUIRED', 'INVOICE_AUTO_MATCH']

type ConfigTab = 'numbering-rules' | 'system-policies'

// ── Numbering Rule Row ────────────────────────────────────────────────────────
function NumberingRuleRow({
  documentType,
  canWrite,
}: {
  documentType: string
  canWrite: boolean
}) {
  const query = useNumberingRule(documentType)
  const mutation = usePutNumberingRule(documentType)

  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({
    prefix: '',
    nextNumber: '',
    formatPattern: '',
    resetPeriod: '',
    active: true,
  })

  function startEdit(rule: NumberingRule) {
    setForm({
      prefix: rule.prefix ?? '',
      nextNumber: String(rule.nextNumber ?? 1),
      formatPattern: rule.formatPattern ?? '',
      resetPeriod: rule.resetPeriod ?? '',
      active: rule.active,
    })
    setEditing(true)
  }

  async function save() {
    await mutation.mutateAsync({
      prefix: form.prefix || null,
      nextNumber: Number(form.nextNumber) || null,
      formatPattern: form.formatPattern || null,
      resetPeriod: form.resetPeriod || null,
      active: form.active,
    })
    setEditing(false)
  }

  if (query.isLoading) {
    return (
      <tr>
        <td className="finance-config-cell finance-config-cell-loading" colSpan={6}>
          Loading {documentType}…
        </td>
      </tr>
    )
  }

  if (query.error) {
    return (
      <tr>
        <td className="finance-config-editor-cell" colSpan={6}>
          <ErrorState
            actionLabel="Retry"
            message={getFinanceErrorMessage(
              query.error,
              `Không thể tải numbering rule cho loại chứng từ ${documentType}.`,
            )}
            onAction={() => void query.refetch()}
            title={`Unable to load ${documentType}`}
          />
        </td>
      </tr>
    )
  }

  if (!query.data) {
    return (
      <tr>
        <td className="finance-config-editor-cell" colSpan={6}>
          <EmptyState
            description={`Chưa có cấu hình numbering cho loại chứng từ ${documentType}.`}
            title="Not configured"
          />
        </td>
      </tr>
    )
  }

  const rule = query.data
  return (
    <>
      <tr className="finance-config-row">
        <td className="finance-config-cell finance-config-cell-code">{rule.documentType}</td>
        <td className="finance-config-cell">{rule.prefix ?? '—'}</td>
        <td className="finance-config-cell">{rule.nextNumber}</td>
        <td className="finance-config-cell">{rule.formatPattern ?? '—'}</td>
        <td className="finance-config-cell">
          <Badge tone={rule.active ? 'success' : 'neutral'}>{rule.active ? 'Active' : 'Inactive'}</Badge>
        </td>
        <td className="finance-config-cell">
          {canWrite && !editing && (
            <Button id={`btn-edit-rule-${rule.documentType}`} onClick={() => startEdit(rule)} size="sm" variant="secondary">
              Edit
            </Button>
          )}
        </td>
      </tr>
      {editing && (
        <tr>
          <td className="finance-config-editor-cell" colSpan={6}>
            <div className="finance-config-editor-grid">
              <Input
                label="Prefix"
                onChange={(e) => setForm((p) => ({ ...p, prefix: e.target.value }))}
                placeholder="e.g. PAY-"
                value={form.prefix}
              />
              <Input
                label="Next Number"
                onChange={(e) => setForm((p) => ({ ...p, nextNumber: e.target.value }))}
                type="number"
                value={form.nextNumber}
              />
              <Input
                label="Format Pattern"
                onChange={(e) => setForm((p) => ({ ...p, formatPattern: e.target.value }))}
                placeholder="e.g. {prefix}{year}{seq:04}"
                value={form.formatPattern}
              />
              <Input
                label="Reset Period"
                onChange={(e) => setForm((p) => ({ ...p, resetPeriod: e.target.value }))}
                placeholder="MONTHLY | YEARLY | NEVER"
                value={form.resetPeriod}
              />
            </div>
            {mutation.error && (
              <p className="error-text field-hint">
                {mutation.error instanceof Error ? mutation.error.message : 'Failed to save'}
              </p>
            )}
            <FormActions
              primaryAction={
                <Button id={`btn-save-rule-${documentType}`} loading={mutation.isPending} onClick={() => void save()} variant="primary">
                  Save
                </Button>
              }
              secondaryAction={
                <Button onClick={() => setEditing(false)} variant="secondary">
                  Cancel
                </Button>
              }
            />
          </td>
        </tr>
      )}
    </>
  )
}

// ── System Policy Row ─────────────────────────────────────────────────────────
function SystemPolicyRow({ policyKey, canWrite }: { policyKey: string; canWrite: boolean }) {
  const query = useSystemPolicy(policyKey)
  const mutation = usePutSystemPolicy(policyKey)

  const [editing, setEditing] = useState(false)
  const [rawValue, setRawValue] = useState('')
  const [parseError, setParseError] = useState<string | null>(null)

  function startEdit(policy: SystemPolicy) {
    try {
      setRawValue(JSON.stringify(policy.policyValue, null, 2))
    } catch {
      setRawValue(String(policy.policyValue))
    }
    setParseError(null)
    setEditing(true)
  }

  async function save() {
    let parsed: unknown
    try {
      parsed = JSON.parse(rawValue)
      setParseError(null)
    } catch {
      setParseError('Invalid JSON. Please enter a valid JSON value.')
      return
    }
    await mutation.mutateAsync({ policyValue: parsed })
    setEditing(false)
  }

  if (query.isLoading) {
    return (
      <tr>
        <td className="finance-config-cell finance-config-cell-loading" colSpan={4}>
          Loading {policyKey}…
        </td>
      </tr>
    )
  }

  if (query.error) {
    return (
      <tr>
        <td className="finance-config-editor-cell" colSpan={4}>
          <ErrorState
            actionLabel="Retry"
            message={getFinanceErrorMessage(
              query.error,
              `Không thể tải system policy ${policyKey}.`,
            )}
            onAction={() => void query.refetch()}
            title={`Unable to load ${policyKey}`}
          />
        </td>
      </tr>
    )
  }

  if (!query.data) {
    return (
      <tr>
        <td className="finance-config-editor-cell" colSpan={4}>
          <EmptyState
            description={`Chưa có giá trị policy cho khóa ${policyKey}.`}
            title="Not configured"
          />
        </td>
      </tr>
    )
  }

  const policy = query.data
  const displayValue = (() => {
    try {
      return JSON.stringify(policy.policyValue)
    } catch {
      return String(policy.policyValue)
    }
  })()

  return (
    <>
      <tr className="finance-config-row">
        <td className="finance-config-cell finance-config-cell-code">{policy.policyKey}</td>
        <td className="finance-config-cell finance-config-cell-code finance-config-cell-truncate">{displayValue}</td>
        <td className="finance-config-cell finance-config-cell-muted">{policy.description ?? '—'}</td>
        <td className="finance-config-cell">
          {canWrite && !editing && (
            <Button id={`btn-edit-policy-${policyKey}`} onClick={() => startEdit(policy)} size="sm" variant="secondary">
              Edit
            </Button>
          )}
        </td>
      </tr>
      {editing && (
        <tr>
          <td className="finance-config-editor-cell" colSpan={4}>
            <div className="finance-config-editor-block">
              <label className="finance-config-editor-label">
                Policy Value (JSON)
              </label>
              <textarea
                className="finance-config-editor-textarea"
                onChange={(e) => setRawValue(e.target.value)}
                rows={4}
                value={rawValue}
              />
              {parseError && <p className="error-text">{parseError}</p>}
              {mutation.error && (
                <p className="error-text">
                  {mutation.error instanceof Error ? mutation.error.message : 'Failed to save'}
                </p>
              )}
            </div>
            <FormActions
              primaryAction={
                <Button id={`btn-save-policy-${policyKey}`} loading={mutation.isPending} onClick={() => void save()} variant="primary">
                  Save
                </Button>
              }
              secondaryAction={
                <Button onClick={() => setEditing(false)} variant="secondary">
                  Cancel
                </Button>
              }
            />
          </td>
        </tr>
      )}
    </>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────
export function FinanceConfigPage() {
  usePageTitle('Finance Configuration')
  const principal = usePrincipal()
  const canRead = hasPermission(principal, permissionConstants.finance.configRead)
  const canWrite = hasPermission(principal, permissionConstants.finance.configWrite)

  const [activeTab, setActiveTab] = useState<ConfigTab>('numbering-rules')

  if (!canRead) {
    return (
      <DashboardLayout description="Manage finance numbering rules and system policies." title="Finance Configuration">
        <PermissionDeniedInline message="You need finance.config.read permission to view this page." />
      </DashboardLayout>
    )
  }

  const activeTabLabel = activeTab === 'numbering-rules' ? 'Numbering Rules' : 'System Policies'

  return (
    <DashboardLayout
      description="Configure numbering rules for document codes and system-wide finance policies."
      title="Finance Configuration"
    >
      <ReadonlyBanner
        message={
          canWrite
            ? 'Finance configuration đang ở chế độ write-enabled. Mọi thay đổi numbering rule và system policy sẽ tác động trực tiếp lên workflow vận hành.'
            : 'Finance configuration hiện là read-only. Cần finance.config.write để chỉnh numbering rules hoặc policy values.'
        }
      />

      <EntityHeader
        eyebrow="Finance / Configuration"
        metadata={
          <>
            <span>Active workspace: {activeTabLabel}</span>
            <span>Document types: {DOCUMENT_TYPES.length}</span>
            <span>Policy keys: {POLICY_KEYS.length}</span>
            <span>Edit access: {canWrite ? 'Write enabled' : 'Read only'}</span>
          </>
        }
        title="Configuration control room"
      />

      <section className="surface-panel command-stage" aria-label="Finance configuration command stage">
        <div className="command-stage-copy">
          <div className="command-stage-meta">
            <span className="meta-chip">Admin workspace</span>
            <span className={canWrite ? 'meta-chip-success' : 'meta-chip'}>{canWrite ? 'Write enabled' : 'Read only'}</span>
            <span className="meta-chip">{activeTabLabel}</span>
          </div>
          <div className="state-panel-heading">
            <p className="eyebrow">Finance / Configuration</p>
            <strong className="action-summary-title">Govern numbering and policy defaults</strong>
            <p className="muted-text">
              Keep document sequencing, JSON policies, and change access visible from one finance
              control room so operational rules stay consistent across modules.
            </p>
          </div>
          <div className="meta-grid">
            <span>Workspace tab: {activeTabLabel}</span>
            <span>Editable document rules: {DOCUMENT_TYPES.length}</span>
            <span>System policy keys: {POLICY_KEYS.length}</span>
            <span>Change mode: {canWrite ? 'Direct update' : 'Review only'}</span>
          </div>
        </div>
        <aside className="command-stage-side">
          <div className="command-stage-note">
            <span className="eyebrow">Governance focus</span>
            <strong>High-impact finance defaults</strong>
            <p>
              Surface the active rule set, write permissions, and JSON policy posture before editing
              values that shape numbering or approval behavior.
            </p>
          </div>
          <div className="command-support-list">
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Current tab</strong>
                <span className="muted-text">The active area of the configuration workspace.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{activeTabLabel}</span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Write access</strong>
                <span className="muted-text">Controls whether rule and policy editors can be opened.</span>
              </div>
              <div className="command-support-stack">
                <span className={canWrite ? 'meta-chip-success' : 'meta-chip'}>
                  {canWrite ? 'Writable' : 'Read-only'}
                </span>
              </div>
            </article>
            <article className="command-support-item">
              <div className="command-support-copy">
                <strong>Governed surfaces</strong>
                <span className="muted-text">Numbering rules plus system-wide policy objects.</span>
              </div>
              <div className="command-support-stack">
                <span className="command-support-metric">{DOCUMENT_TYPES.length + POLICY_KEYS.length}</span>
              </div>
            </article>
          </div>
        </aside>
      </section>

      <section className="workspace-stats-grid" aria-label="Finance configuration summary">
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="tag" />
            </span>
            <span className="workspace-stat-badge">Rules</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Document numbering rules</span>
            <strong className="workspace-stat-value">{DOCUMENT_TYPES.length}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="data_object" />
            </span>
            <span className="workspace-stat-badge">Policies</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">System policy keys</span>
            <strong className="workspace-stat-value">{POLICY_KEYS.length}</strong>
          </div>
        </article>
        <article className="workspace-stat-card">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="settings" />
            </span>
            <span className="workspace-stat-badge">Workspace</span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Active tab</span>
            <strong className="workspace-stat-value">{activeTabLabel}</strong>
          </div>
        </article>
        <article className="workspace-stat-card warning">
          <div className="workspace-stat-topline">
            <span className="workspace-stat-icon">
              <AppIcon filled name="admin_panel_settings" />
            </span>
            <span className={canWrite ? 'workspace-stat-badge success' : 'workspace-stat-badge warning'}>
              Access
            </span>
          </div>
          <div className="compact-stack">
            <span className="workspace-stat-label">Editor mode</span>
            <strong className="workspace-stat-value">{canWrite ? 'Writable' : 'Review only'}</strong>
          </div>
        </article>
      </section>

      <div className="surface-grid">
        <div className="surface-grid-main">
          <section className="surface-panel command-table-stack">
            <div className="state-panel-heading">
              <span className="eyebrow">Workspace tabs</span>
              <h2 className="card-title">Configuration views</h2>
              <p className="muted-text">
                Switch between numbering rules and JSON-backed policies without leaving the finance
                control room.
              </p>
            </div>
            <div className="finance-config-tabs">
              {(
                [
                  { key: 'numbering-rules', label: 'Numbering Rules' },
                  { key: 'system-policies', label: 'System Policies' },
                ] as { key: ConfigTab; label: string }[]
              ).map((tab) => (
                <button
                  className={`finance-config-tab ${activeTab === tab.key ? 'is-active' : ''}`}
                  id={`tab-${tab.key}`}
                  key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  type="button"
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </section>

          {activeTab === 'numbering-rules' && (
            <Card title="Document Numbering Rules">
              <p className="muted-text finance-config-note">
                Configure prefix, sequence, and format patterns for document codes (payroll runs, invoices, etc.).
              </p>
              <div className="finance-config-table-wrap">
                <table className="finance-config-table">
                  <thead>
                    <tr className="finance-config-head">
                      <th className="finance-config-header-cell">Document Type</th>
                      <th className="finance-config-header-cell">Prefix</th>
                      <th className="finance-config-header-cell">Next #</th>
                      <th className="finance-config-header-cell">Format Pattern</th>
                      <th className="finance-config-header-cell">Status</th>
                      <th className="finance-config-header-cell">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {DOCUMENT_TYPES.map((dt) => (
                      <NumberingRuleRow canWrite={canWrite} documentType={dt} key={dt} />
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}

          {activeTab === 'system-policies' && (
            <Card title="System Policies">
              <p className="muted-text finance-config-note">
                Manage system-wide policies. Policy values are stored as JSON.
              </p>
              <div className="finance-config-table-wrap">
                <table className="finance-config-table">
                  <thead>
                    <tr className="finance-config-head">
                      <th className="finance-config-header-cell">Policy Key</th>
                      <th className="finance-config-header-cell">Value</th>
                      <th className="finance-config-header-cell">Description</th>
                      <th className="finance-config-header-cell">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {POLICY_KEYS.map((key) => (
                      <SystemPolicyRow canWrite={canWrite} key={key} policyKey={key} />
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}
        </div>

        <aside className="surface-grid-side">
          <section className="surface-panel command-table-stack">
            <div className="state-panel-heading">
              <span className="eyebrow">Change guidance</span>
              <h2 className="card-title">Admin notes</h2>
              <p className="muted-text">
                High-impact finance settings should be changed deliberately and reviewed with the
                current workspace context in view.
              </p>
            </div>
            <div className="command-support-list">
              <article className="command-support-item">
                <div className="command-support-copy">
                  <strong>Numbering patterns</strong>
                  <span className="muted-text">Use stable prefixes and sequence patterns to keep downstream references readable.</span>
                </div>
                <div className="command-support-stack">
                  <span className="meta-chip">Structured</span>
                </div>
              </article>
              <article className="command-support-item">
                <div className="command-support-copy">
                  <strong>Policy values</strong>
                  <span className="muted-text">System policies accept JSON, so malformed payloads should be avoided before save.</span>
                </div>
                <div className="command-support-stack">
                  <span className="meta-chip">{activeTab === 'system-policies' ? 'JSON focus' : 'Available'}</span>
                </div>
              </article>
              <article className="command-support-item">
                <div className="command-support-copy">
                  <strong>Write authority</strong>
                  <span className="muted-text">Editors only open when the finance configuration write permission is present.</span>
                </div>
                <div className="command-support-stack">
                  <span className={canWrite ? 'meta-chip-success' : 'meta-chip'}>
                    {canWrite ? 'Granted' : 'Restricted'}
                  </span>
                </div>
              </article>
            </div>
          </section>
        </aside>
      </div>
    </DashboardLayout>
  )
}
