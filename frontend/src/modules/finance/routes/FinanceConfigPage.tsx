import { useState } from 'react'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormActions,
  Input,
  PermissionDeniedInline,
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
        <td colSpan={6} style={{ padding: '0.5rem 1rem', color: 'var(--color-text-muted)' }}>
          Loading {documentType}…
        </td>
      </tr>
    )
  }

  if (query.error) {
    return (
      <tr>
        <td colSpan={6} style={{ padding: '1rem', verticalAlign: 'top' }}>
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
        <td colSpan={6} style={{ padding: '1rem', verticalAlign: 'top' }}>
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
      <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
        <td style={{ padding: '0.5rem 1rem', fontFamily: 'monospace' }}>{rule.documentType}</td>
        <td style={{ padding: '0.5rem 1rem' }}>{rule.prefix ?? '—'}</td>
        <td style={{ padding: '0.5rem 1rem' }}>{rule.nextNumber}</td>
        <td style={{ padding: '0.5rem 1rem' }}>{rule.formatPattern ?? '—'}</td>
        <td style={{ padding: '0.5rem 1rem' }}>
          <Badge tone={rule.active ? 'success' : 'neutral'}>{rule.active ? 'Active' : 'Inactive'}</Badge>
        </td>
        <td style={{ padding: '0.5rem 1rem' }}>
          {canWrite && !editing && (
            <Button id={`btn-edit-rule-${rule.documentType}`} onClick={() => startEdit(rule)} size="sm" variant="secondary">
              Edit
            </Button>
          )}
        </td>
      </tr>
      {editing && (
        <tr>
          <td colSpan={6} style={{ padding: '1rem', background: 'var(--color-surface-raised)' }}>
            <div className="field-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.75rem' }}>
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
              <p className="error-text" style={{ marginTop: '0.5rem' }}>
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
        <td colSpan={4} style={{ padding: '0.5rem 1rem', color: 'var(--color-text-muted)' }}>
          Loading {policyKey}…
        </td>
      </tr>
    )
  }

  if (query.error) {
    return (
      <tr>
        <td colSpan={4} style={{ padding: '1rem', verticalAlign: 'top' }}>
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
        <td colSpan={4} style={{ padding: '1rem', verticalAlign: 'top' }}>
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
      <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
        <td style={{ padding: '0.5rem 1rem', fontFamily: 'monospace' }}>{policy.policyKey}</td>
        <td style={{ padding: '0.5rem 1rem', fontFamily: 'monospace', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{displayValue}</td>
        <td style={{ padding: '0.5rem 1rem', color: 'var(--color-text-muted)' }}>{policy.description ?? '—'}</td>
        <td style={{ padding: '0.5rem 1rem' }}>
          {canWrite && !editing && (
            <Button id={`btn-edit-policy-${policyKey}`} onClick={() => startEdit(policy)} size="sm" variant="secondary">
              Edit
            </Button>
          )}
        </td>
      </tr>
      {editing && (
        <tr>
          <td colSpan={4} style={{ padding: '1rem', background: 'var(--color-surface-raised)' }}>
            <div style={{ marginBottom: '0.5rem' }}>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: '0.25rem', fontSize: '0.875rem' }}>
                Policy Value (JSON)
              </label>
              <textarea
                onChange={(e) => setRawValue(e.target.value)}
                rows={4}
                style={{
                  width: '100%',
                  padding: '0.5rem',
                  fontFamily: 'monospace',
                  fontSize: '0.8125rem',
                  border: '1px solid var(--color-border)',
                  borderRadius: '0.375rem',
                  background: 'var(--color-surface)',
                  color: 'var(--color-text)',
                  resize: 'vertical',
                }}
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

  return (
    <DashboardLayout
      description="Configure numbering rules for document codes and system-wide finance policies."
      title="Finance Configuration"
    >
      <div className="page-stack">
        {/* Tab navigation */}
        <div style={{ display: 'flex', gap: '0.5rem', borderBottom: '2px solid var(--color-border)', paddingBottom: '0' }}>
          {(
            [
              { key: 'numbering-rules', label: 'Numbering Rules' },
              { key: 'system-policies', label: 'System Policies' },
            ] as { key: ConfigTab; label: string }[]
          ).map((tab) => (
            <button
              id={`tab-${tab.key}`}
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '0.5rem 1rem',
                border: 'none',
                background: 'transparent',
                cursor: 'pointer',
                fontWeight: activeTab === tab.key ? 700 : 400,
                color: activeTab === tab.key ? 'var(--color-primary)' : 'var(--color-text-muted)',
                borderBottom: activeTab === tab.key ? '2px solid var(--color-primary)' : '2px solid transparent',
                marginBottom: '-2px',
                fontSize: '0.9375rem',
                transition: 'color 0.15s, border-color 0.15s',
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Numbering Rules Tab */}
        {activeTab === 'numbering-rules' && (
          <Card title="Document Numbering Rules">
            <p className="muted-text" style={{ marginBottom: '1rem' }}>
              Configure prefix, sequence, and format patterns for document codes (payroll runs, invoices, etc.).
            </p>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                <thead>
                  <tr style={{ background: 'var(--color-surface-raised)', borderBottom: '2px solid var(--color-border)' }}>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Document Type</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Prefix</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Next #</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Format Pattern</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Status</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Actions</th>
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

        {/* System Policies Tab */}
        {activeTab === 'system-policies' && (
          <Card title="System Policies">
            <p className="muted-text" style={{ marginBottom: '1rem' }}>
              Manage system-wide policies. Policy values are stored as JSON.
            </p>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                <thead>
                  <tr style={{ background: 'var(--color-surface-raised)', borderBottom: '2px solid var(--color-border)' }}>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Policy Key</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Value</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Description</th>
                    <th style={{ padding: '0.5rem 1rem', textAlign: 'left' }}>Actions</th>
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
    </DashboardLayout>
  )
}
