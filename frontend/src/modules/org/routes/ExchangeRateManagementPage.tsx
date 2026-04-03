import { useState } from 'react'
import { DashboardLayout } from '@shared/layouts/DashboardLayout'
import {
  Badge,
  Button,
  Card,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
} from '@design-system/index'

// Backend: UpsertExchangeRateRequest.rate @DecimalMin("0.00000001")
const MIN_EXCHANGE_RATE = 0.00000001
import { DataTable } from '@design-system/tables/DataTable'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { usePrincipal } from '@core/auth/auth.selectors'
import { permissionConstants } from '@core/permissions/permission.constants'
import { hasPermission } from '@core/permissions/permission.checker'
import { useExchangeRates, useUpsertExchangeRate, useDeleteExchangeRate } from '../hooks/useExchangeRates'
import type { ExchangeRate } from '../model/exchangeRate.types'

export function ExchangeRateManagementPage() {
  usePageTitle('Exchange Rate Management')
  const principal = usePrincipal()
  const canRead = hasPermission(principal, permissionConstants.finance.configRead)
  const canWrite = hasPermission(principal, permissionConstants.finance.configWrite)

  const [filters, setFilters] = useState({ fromCurrency: '', toCurrency: '' })
  const [showForm, setShowForm] = useState(false)
  const [confirmDeleteKey, setConfirmDeleteKey] = useState<string | null>(null)
  const [rateError, setRateError] = useState<string | null>(null)
  const [form, setForm] = useState({
    fromCurrencyCode: '',
    toCurrencyCode: '',
    rate: '',
    effectiveFrom: new Date().toISOString().slice(0, 10),
    effectiveTo: '',
  })

  const ratesQuery = useExchangeRates(
    canRead
      ? {
          fromCurrency: filters.fromCurrency || undefined,
          toCurrency: filters.toCurrency || undefined,
        }
      : undefined,
  )

  const upsertMutation = useUpsertExchangeRate()
  const deleteMutation = useDeleteExchangeRate()

  if (!canRead) {
    return (
      <DashboardLayout description="Manage currency exchange rates." title="Exchange Rate Management">
        <PermissionDeniedInline message="You need finance.config.read permission to view exchange rates." />
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout
      description="View and manage currency exchange rates for multi-currency operations."
      title="Exchange Rate Management"
    >
      <div className="page-stack">
        {/* Filters */}
        <Card title="Filters">
          <div className="field-grid">
            <Input
              label="From Currency"
              onChange={(e) => setFilters((prev) => ({ ...prev, fromCurrency: e.target.value.toUpperCase() }))}
              placeholder="e.g. USD"
              value={filters.fromCurrency}
            />
            <Input
              label="To Currency"
              onChange={(e) => setFilters((prev) => ({ ...prev, toCurrency: e.target.value.toUpperCase() }))}
              placeholder="e.g. VND"
              value={filters.toCurrency}
            />
          </div>
        </Card>

        {/* Create/Edit */}
        {canWrite && !showForm && (
          <Button id="btn-show-create-rate" onClick={() => setShowForm(true)} variant="primary">
            + Add exchange rate
          </Button>
        )}

        {canWrite && showForm && (
          <FormSection title="Add / Update Exchange Rate">
            <div className="field-grid">
              <Input
                label="From Currency Code"
                onChange={(e) => setForm((prev) => ({ ...prev, fromCurrencyCode: e.target.value.toUpperCase() }))}
                placeholder="USD"
                value={form.fromCurrencyCode}
              />
              <Input
                label="To Currency Code"
                onChange={(e) => setForm((prev) => ({ ...prev, toCurrencyCode: e.target.value.toUpperCase() }))}
                placeholder="VND"
                value={form.toCurrencyCode}
              />
              <Input
                label="Rate"
                onChange={(e) => {
                  setForm((prev) => ({ ...prev, rate: e.target.value }))
                  setRateError(null)
                }}
                placeholder="25350.00"
                type="number"
                value={form.rate}
              />
              <Input
                label="Effective From"
                onChange={(e) => setForm((prev) => ({ ...prev, effectiveFrom: e.target.value }))}
                type="date"
                value={form.effectiveFrom}
              />
              <Input
                label="Effective To (optional)"
                onChange={(e) => setForm((prev) => ({ ...prev, effectiveTo: e.target.value }))}
                type="date"
                value={form.effectiveTo}
              />
            </div>
            {rateError && (
              <p className="error-text error-text-compact">{rateError}</p>
            )}
            <FormActions
              primaryAction={
                <Button
                  disabled={!form.fromCurrencyCode || !form.toCurrencyCode || !form.rate || !form.effectiveFrom}
                  loading={upsertMutation.isPending}
                  onClick={async () => {
                    const parsedRate = Number(form.rate)
                    // Backend: @NotNull @DecimalMin("0.00000001")
                    if (!Number.isFinite(parsedRate) || parsedRate < MIN_EXCHANGE_RATE) {
                      setRateError(`Rate must be a number ≥ ${MIN_EXCHANGE_RATE}.`)
                      return
                    }
                    setRateError(null)
                    await upsertMutation.mutateAsync({
                      fromCurrencyCode: form.fromCurrencyCode,
                      toCurrencyCode: form.toCurrencyCode,
                      rate: parsedRate,
                      effectiveFrom: form.effectiveFrom,
                      effectiveTo: form.effectiveTo || null,
                    })
                    setShowForm(false)
                    setForm({
                      fromCurrencyCode: '',
                      toCurrencyCode: '',
                      rate: '',
                      effectiveFrom: new Date().toISOString().slice(0, 10),
                      effectiveTo: '',
                    })
                  }}
                >
                  Save
                </Button>
              }
              secondaryAction={
                <Button onClick={() => setShowForm(false)} variant="secondary">
                  Cancel
                </Button>
              }
            />
            {upsertMutation.error && (
              <ErrorState
                message={upsertMutation.error instanceof Error ? upsertMutation.error.message : 'Failed to save'}
                title="Error"
              />
            )}
          </FormSection>
        )}

        {/* Table */}
        <DataTable<ExchangeRate>
          columns={[
            { key: 'from', header: 'From', render: (row) => <Badge tone="neutral">{row.fromCurrencyCode}</Badge> },
            { key: 'to', header: 'To', render: (row) => <Badge tone="neutral">{row.toCurrencyCode}</Badge> },
            {
              key: 'rate',
              header: 'Rate',
              render: (row) => <strong>{row.rate.toLocaleString(undefined, { maximumFractionDigits: 8 })}</strong>,
            },
            { key: 'effectiveFrom', header: 'Effective From', render: (row) => <>{row.effectiveFrom}</> },
            { key: 'effectiveTo', header: 'Effective To', render: (row) => <>{row.effectiveTo ?? '∞ (ongoing)'}</> },
            ...(canWrite
              ? [
                  {
                    key: 'actions' as const,
                    header: 'Actions',
                    render: (row: ExchangeRate) => {
                      const rowKey = `${row.fromCurrencyCode}-${row.toCurrencyCode}-${row.effectiveFrom}`
                      const isPendingDelete = confirmDeleteKey === rowKey
                      return (
                        <div className="inline-action-row">
                          <Button
                            onClick={() => {
                              setForm({
                                fromCurrencyCode: row.fromCurrencyCode,
                                toCurrencyCode: row.toCurrencyCode,
                                rate: String(row.rate),
                                effectiveFrom: row.effectiveFrom,
                                effectiveTo: row.effectiveTo ?? '',
                              })
                              setShowForm(true)
                            }}
                            size="sm"
                            variant="secondary"
                          >
                            Edit
                          </Button>
                          {isPendingDelete ? (
                            <>
                              <span className="muted-text muted-caption">Confirm?</span>
                              <Button
                                loading={deleteMutation.isPending}
                                onClick={async () => {
                                  await deleteMutation.mutateAsync({
                                    fromCurrency: row.fromCurrencyCode,
                                    toCurrency: row.toCurrencyCode,
                                    effectiveFrom: row.effectiveFrom,
                                  })
                                  setConfirmDeleteKey(null)
                                }}
                                size="sm"
                                variant="danger"
                              >
                                Delete
                              </Button>
                              <Button
                                onClick={() => setConfirmDeleteKey(null)}
                                size="sm"
                                variant="secondary"
                              >
                                Cancel
                              </Button>
                            </>
                          ) : (
                            <Button
                              onClick={() => setConfirmDeleteKey(rowKey)}
                              size="sm"
                              variant="danger"
                            >
                              Delete
                            </Button>
                          )}
                        </div>
                      )
                    },
                  },
                ]
              : []),
          ]}
          emptyDescription="No exchange rates found. Add a new rate to get started."
          emptyTitle="No exchange rates"
          error={ratesQuery.error ? (ratesQuery.error instanceof Error ? ratesQuery.error.message : 'Failed to load rates') : null}
          loading={ratesQuery.isLoading}
          onRetry={() => void ratesQuery.refetch()}
          rowKey={(row) => `${row.fromCurrencyCode}-${row.toCurrencyCode}-${row.effectiveFrom}`}
          rows={ratesQuery.data ?? []}
        />
      </div>
    </DashboardLayout>
  )
}
