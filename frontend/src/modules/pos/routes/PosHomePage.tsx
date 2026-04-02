import { useDeferredValue, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePrincipal } from '@core/auth/auth.selectors'
import { useScopeContext } from '@core/scopes/useScopeContext'
import {
  Button,
  Card,
  ConfirmActionDialog,
  EmptyState,
  ErrorState,
  FormActions,
  FormSection,
  Input,
  PermissionDeniedInline,
  ReadonlyBanner,
  Select,
  Textarea,
} from '@design-system/index'
import { usePageTitle } from '@shared/hooks/usePageTitle'
import { useNetworkStatus } from '@shared/hooks/useNetworkStatus'
import { ApiError } from '@core/api/apiError'
import { formatDate } from '@shared/formatters'
import { PosCatalogGrid } from '../components/PosCatalogGrid'
import { PosCartPanel } from '../components/PosCartPanel'
import { PosSessionSummaryCard } from '../components/PosSessionSummaryCard'
import { useRegion } from '@modules/org/hooks/useOrg'
import { usePosCatalog } from '../hooks/usePosCatalog'
import { useCreatePosOrder } from '../hooks/usePosOrder'
import { useClosePosSession, useOpenPosSession, usePosSessions } from '../hooks/usePosSession'
import type { PosOrderType } from '../model/pos.types'
import { selectCartDraft, useCartStore } from '../state/cart.store'
import { usePosUiStore } from '../state/posUi.store'
import { buildCreateOrderPayload } from '../services/orderPayload.mapper'
import {
  canCreateOrderFromCart,
  canOpenSession,
  canReadCatalog,
  canReadSessions,
  canCloseSessionAction,
} from '../services/posUiPolicy.service'
import { getTodayBusinessDate } from '../services/posDate.service'
import { canCloseSession } from '../services/sessionUiPolicy.service'

export function PosHomePage() {
  usePageTitle('POS Home')

  const navigate = useNavigate()
  const principal = usePrincipal()
  const isOnline = useNetworkStatus()
  const { selectedOutletId, selectedRegionId, outletIds, setSelectedOutletId } = useScopeContext()
  const previousOutletId = useRef<number | null>(null)
  const [openSessionNote, setOpenSessionNote] = useState('')
  const [sessionNoteError, setSessionNoteError] = useState<string | null>(null)
  const [closeDialogOpen, setCloseDialogOpen] = useState(false)

  const businessDate = usePosUiStore((state) =>
    selectedOutletId ? state.businessDates[String(selectedOutletId)] ?? getTodayBusinessDate() : getTodayBusinessDate(),
  )
  const orderType = usePosUiStore((state) =>
    selectedOutletId ? state.orderTypes[String(selectedOutletId)] ?? 'DINE_IN' : 'DINE_IN',
  )
  const searchTerm = usePosUiStore((state) => (selectedOutletId ? state.searchTerms[String(selectedOutletId)] ?? '' : ''))
  const categoryFilter = usePosUiStore((state) =>
    selectedOutletId ? state.categoryFilters[String(selectedOutletId)] ?? '' : '',
  )
  const reusedSessionCode = usePosUiStore((state) =>
    selectedOutletId ? state.reusedSessionCodes[String(selectedOutletId)] ?? '' : '',
  )
  const setBusinessDate = usePosUiStore((state) => state.setBusinessDate)
  const setOrderType = usePosUiStore((state) => state.setOrderType)
  const setSearchTerm = usePosUiStore((state) => state.setSearchTerm)
  const setCategoryFilter = usePosUiStore((state) => state.setCategoryFilter)
  const setReusedSessionCode = usePosUiStore((state) => state.setReusedSessionCode)
  const clearOutletUi = usePosUiStore((state) => state.clearOutletUi)

  const drafts = useCartStore((state) => state.drafts)
  const cartDraft = selectedOutletId ? selectCartDraft(drafts, selectedOutletId, orderType as PosOrderType) : null
  const addItem = useCartStore((state) => state.addItem)
  const clearDraft = useCartStore((state) => state.clearDraft)
  const clearOutletDrafts = useCartStore((state) => state.clearOutletDrafts)
  const setOrderNote = useCartStore((state) => state.setOrderNote)
  const updateItemNote = useCartStore((state) => state.updateItemNote)
  const updateItemQty = useCartStore((state) => state.updateItemQty)
  const removeItem = useCartStore((state) => state.removeItem)

  useEffect(() => {
    if (previousOutletId.current !== null && previousOutletId.current !== selectedOutletId) {
      clearOutletDrafts(previousOutletId.current)
      clearOutletUi(previousOutletId.current)
    }
    previousOutletId.current = selectedOutletId
  }, [clearOutletDrafts, clearOutletUi, selectedOutletId])

  const regionQuery = useRegion(selectedRegionId ?? 0, { enabled: !!selectedRegionId })
  // Use the region's configured currency code; fall back to 'VND' only while the
  // region is still loading, so the payload is always a valid non-empty string.
  const regionCurrencyCode = regionQuery.data?.currencyCode ?? 'VND'

  const canReadSessionsPermission = canReadSessions(principal)
  const canReadCatalogPermission = canReadCatalog(principal)
  const canOpenSessionPermission = canOpenSession(principal, isOnline)
  const canCloseSessionPermission = canCloseSessionAction(principal)

  const openSessionsQuery = usePosSessions(
    selectedOutletId && canReadSessionsPermission
      ? {
          outletId: selectedOutletId,
          status: 'OPEN',
        }
      : null,
    canReadSessionsPermission,
  )
  const currentSession = openSessionsQuery.data?.[0] ?? null
  const effectiveRegionId = selectedRegionId ?? currentSession?.regionId ?? null

  // Normalize BackendDate ([year,month,day] or ISO string) → 'YYYY-MM-DD' string for API
  const sessionBusinessDateStr = (() => {
    const raw = currentSession?.businessDate
    if (!raw) return businessDate
    if (Array.isArray(raw) && raw.length === 3) {
      const [y, m, d] = raw as number[]
      return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    }
    return String(raw)
  })()

  const catalogQuery = usePosCatalog(
    currentSession && selectedOutletId
      ? {
          outletId: selectedOutletId,
          regionId: effectiveRegionId ?? undefined,
          businessDate: sessionBusinessDateStr,
          orderType: orderType as PosOrderType,
        }
      : null,
    Boolean(currentSession && canReadCatalogPermission),
  )
  const openSessionMutation = useOpenPosSession()
  const closeSessionMutation = useClosePosSession()
  const createOrderMutation = useCreatePosOrder()
  const deferredSearchTerm = useDeferredValue(searchTerm)

  const filteredMenuItems = (catalogQuery.data ?? []).filter((item) => {
    const matchesSearch =
      deferredSearchTerm.length === 0 ||
      item.name.toLowerCase().includes(deferredSearchTerm.toLowerCase()) ||
      item.code.toLowerCase().includes(deferredSearchTerm.toLowerCase())
    const matchesCategory = !categoryFilter || item.categoryCode === categoryFilter

    return matchesSearch && matchesCategory
  })
  const categories = Array.from(new Set((catalogQuery.data ?? []).map((item) => item.categoryCode))).sort()

  if (!selectedOutletId || !effectiveRegionId) {
    return (
      <section className="page-stack pos-home-page">
        <ReadonlyBanner message="POS needs an outlet context to resolve sessions and create orders. Choose an outlet below or from the shell." />
        <EmptyState
          description="Choose an outlet below to begin the front-of-house selling workspace."
          title="No outlet selected"
        >
          {outletIds.length > 0 ? (
            <div style={{ marginTop: '1rem', maxWidth: '320px' }}>
              <Select
                label="Choose operating outlet"
                onChange={(event) => {
                  if (event.target.value) {
                    setSelectedOutletId(Number(event.target.value))
                  }
                }}
                options={outletIds.map((id) => ({ label: `Outlet #${id}`, value: String(id) }))}
                placeholder="-- Choose outlet --"
                value={selectedOutletId ? String(selectedOutletId) : ''}
              />
            </div>
          ) : (
            <p className="muted-text" style={{ marginTop: '0.5rem' }}>
              This account has not been assigned an outlet scope. Contact a system administrator to continue.
            </p>
          )}
        </EmptyState>
      </section>
    )
  }

  if (!canReadSessionsPermission) {
    return (
      <section className="page-stack pos-home-page">
        <PermissionDeniedInline
          message="You need `pos.session.read` to inspect the current session and unlock the POS workspace."
          title="Unable to open POS workspace"
        />
      </section>
    )
  }

  if (openSessionsQuery.error) {
    return (
      <section className="page-stack">
        <ErrorState
          actionLabel="Retry"
          message={openSessionsQuery.error instanceof Error ? openSessionsQuery.error.message : 'Failed to load POS session'}
          onAction={() => void openSessionsQuery.refetch()}
          title="Unable to load POS session"
        />
      </section>
    )
  }

  return (
    <section className="page-stack pos-home-page">
      {reusedSessionCode ? (
        <div className="inline-banner inline-banner-success">
          Reused the current open session: <strong>{reusedSessionCode}</strong>
        </div>
      ) : null}

      {canReadSessionsPermission && openSessionsQuery.isLoading ? (
        <Card title="Loading current session">
          <p className="muted-text">Checking whether the selected outlet already has an open POS session...</p>
        </Card>
      ) : currentSession ? (
        <PosSessionSummaryCard
          actions={
            canCloseSessionPermission && canCloseSession(currentSession, isOnline) ? (
              <Button
                loading={closeSessionMutation.isPending}
                onClick={() => setCloseDialogOpen(true)}
                size="sm"
                variant="danger"
              >
                Close session
              </Button>
            ) : undefined
          }
          session={currentSession}
        />
      ) : (
        <FormSection description="Open a selling session before creating orders or accepting payment." title="Open POS session">
          {!isOnline ? (
            <ReadonlyBanner message="The terminal is offline, so a new session cannot be opened yet. Reconnect to continue." />
          ) : !canOpenSessionPermission ? (
            <PermissionDeniedInline
              message="You need `pos.session.open` to open a new selling session at the current outlet."
              title="Unable to open session"
            />
          ) : null}
          <div className="field-grid">
            <Input label="Region ID" readOnly value={effectiveRegionId} />
            <Input label="Outlet ID" readOnly value={selectedOutletId} />
            <Input
              label="Business date"
              onChange={(event) => setBusinessDate(selectedOutletId, event.target.value)}
              type="date"
              value={businessDate}
            />
            <Input label="Currency" readOnly value={regionCurrencyCode} />
          </div>
          <Textarea
            label="Note"
            onChange={(event) => setOpenSessionNote(event.target.value)}
            placeholder="Optional opening note"
            rows={3}
            value={openSessionNote}
          />
          {sessionNoteError ? <p className="error-text">{sessionNoteError}</p> : null}
          <FormActions
            primaryAction={
              <Button
                disabled={!canOpenSessionPermission}
                loading={openSessionMutation.isPending}
                onClick={async () => {
                  setSessionNoteError(null)
                  try {
                    const result = await openSessionMutation.mutateAsync({
                      regionId: effectiveRegionId,
                      outletId: selectedOutletId,
                      businessDate,
                      currencyCode: regionCurrencyCode,
                      note: openSessionNote || undefined,
                    })
                    setReusedSessionCode(selectedOutletId, result.sessionExisted ? result.session.sessionCode : null)
                    setOpenSessionNote('')
                  } catch (error) {
                    setSessionNoteError(error instanceof Error ? error.message : 'Failed to open POS session')
                  }
                }}
              >
                Open session
              </Button>
            }
          />
          {openSessionMutation.error ? (
            <ErrorState
              actionLabel="Try again"
              message={openSessionMutation.error instanceof Error ? openSessionMutation.error.message : 'Failed to open POS session'}
              onAction={() =>
                void openSessionMutation.mutateAsync({
                  regionId: effectiveRegionId,
                  outletId: selectedOutletId,
                  businessDate,
                  currencyCode: regionCurrencyCode,
                  note: openSessionNote || undefined,
                })
              }
              title="Unable to open session"
            />
          ) : null}
        </FormSection>
      )}

      {closeSessionMutation.error ? (
        <ErrorState
          actionLabel="Retry close"
          message={closeSessionMutation.error instanceof Error ? closeSessionMutation.error.message : 'Failed to close POS session'}
          onAction={() => setCloseDialogOpen(true)}
          title="Unable to close session"
        />
      ) : null}

      {currentSession ? (
        <>
          {!isOnline ? (
            <ReadonlyBanner message="POS is offline. Creating new orders and closing the session remain locked until the connection returns." />
          ) : null}

          {!canReadCatalogPermission ? (
            <PermissionDeniedInline
              message="POS needs both `catalog.product.read` and `catalog.price.read` to resolve outlet menu, availability, and effective price."
              title="Unable to load catalog"
            />
          ) : null}

          <div className="pos-grid pos-workspace-grid">
            <section className="page-stack">
              <Card className="pos-filter-panel" title="Catalog filters">
                <div className="field-grid">
                  <Input
                    label="Search"
                    onChange={(event) => setSearchTerm(selectedOutletId, event.target.value)}
                    placeholder="Search by name or code"
                    value={searchTerm}
                  />
                  <Select
                    label="Category"
                    onChange={(event) => setCategoryFilter(selectedOutletId, event.target.value)}
                    options={categories.map((category) => ({ label: category, value: category }))}
                    placeholder="All categories"
                    value={categoryFilter}
                  />
                  <Select
                    label="Order type"
                    onChange={(event) => setOrderType(selectedOutletId, event.target.value as PosOrderType)}
                    options={[
                      { label: 'Dine in', value: 'DINE_IN' },
                      { label: 'Takeaway', value: 'TAKEAWAY' },
                    ]}
                    value={orderType}
                  />
                  <Input label="Business date" readOnly value={formatDate(currentSession.businessDate)} />
                </div>
              </Card>

              {catalogQuery.error ? (
                <ErrorState
                  actionLabel="Retry"
                  message={
                    catalogQuery.error instanceof ApiError && catalogQuery.error.isForbidden
                      ? 'Current user cannot read catalog data required for POS.'
                      : catalogQuery.error instanceof Error
                        ? catalogQuery.error.message
                        : 'Failed to load product catalog'
                  }
                  onAction={() => void catalogQuery.refetch()}
                  title="Unable to load catalog"
                />
              ) : null}

              {catalogQuery.isLoading ? (
                <Card title="Loading catalog">
                  <p className="muted-text">Loading products, prices and outlet availability...</p>
                </Card>
              ) : null}

              {!catalogQuery.isLoading && !catalogQuery.error && canReadCatalogPermission ? (
                <PosCatalogGrid
                  canAdd={canCreateOrderFromCart(principal, currentSession, 1, isOnline)}
                  items={filteredMenuItems}
                  onAdd={(item) => addItem(selectedOutletId, orderType as PosOrderType, item)}
                />
              ) : null}
            </section>

            <PosCartPanel
              canSubmit={canCreateOrderFromCart(principal, currentSession, cartDraft?.items.length ?? 0, isOnline)}
              draft={cartDraft ?? { outletId: selectedOutletId, orderType: 'DINE_IN', orderNote: '', items: [] }}
              isSubmitting={createOrderMutation.isPending}
              onCreate={async () => {
                if (!cartDraft || !currentSession) {
                  return
                }

                const order = await createOrderMutation.mutateAsync(buildCreateOrderPayload(cartDraft, currentSession.id))
                clearDraft(selectedOutletId, cartDraft.orderType)
                navigate(`/pos/orders/${order.id}`)
              }}
              onItemNoteChange={(productId, note) => updateItemNote(selectedOutletId, orderType as PosOrderType, productId, note)}
              onOrderNoteChange={(note) => setOrderNote(selectedOutletId, orderType as PosOrderType, note)}
              onOrderTypeChange={(nextOrderType) => setOrderType(selectedOutletId, nextOrderType)}
              onQtyChange={(productId, qty) => updateItemQty(selectedOutletId, orderType as PosOrderType, productId, qty)}
              onRemove={(productId) => removeItem(selectedOutletId, orderType as PosOrderType, productId)}
            />
          </div>

          {createOrderMutation.error ? (
            <ErrorState
              message={createOrderMutation.error instanceof Error ? createOrderMutation.error.message : 'Failed to create order'}
              title="Unable to create order"
            />
          ) : null}
        </>
      ) : !openSessionsQuery.isLoading ? (
        <EmptyState
          description="Open a session to load the catalog, cart, and order creation flow."
          title="No open POS session"
        />
      ) : null}

      <ConfirmActionDialog
        confirmLabel="Close session"
        danger
        description="Close the session only after every order has been completed or cancelled."
        onCancel={() => setCloseDialogOpen(false)}
        onConfirm={async () => {
          if (!currentSession) {
            return
          }

          await closeSessionMutation.mutateAsync(currentSession.id)
          clearOutletDrafts(selectedOutletId)
          setReusedSessionCode(selectedOutletId, null)
          setCloseDialogOpen(false)
        }}
        open={closeDialogOpen}
        title="Close POS session?"
      />
    </section>
  )
}
