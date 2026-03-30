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
import { PosCatalogGrid } from '../components/PosCatalogGrid'
import { PosCartPanel } from '../components/PosCartPanel'
import { PosSessionSummaryCard } from '../components/PosSessionSummaryCard'
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
  const { selectedOutletId, selectedRegionId } = useScopeContext()
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

  const catalogQuery = usePosCatalog(
    currentSession && selectedOutletId
      ? {
          outletId: selectedOutletId,
          regionId: selectedRegionId ?? undefined,
          businessDate: currentSession.businessDate,
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

  if (!selectedOutletId || !selectedRegionId) {
    return (
      <section className="page-stack pos-home-page">
        <ReadonlyBanner message="Chọn outlet và region ở app shell trước khi dùng POS." />
        <EmptyState
          description="POS cần context outlet và region để kiểm tra session, resolve catalog và tạo order đúng scope."
          title="POS context missing"
        />
      </section>
    )
  }

  if (!canReadSessionsPermission) {
    return (
      <section className="page-stack pos-home-page">
        <PermissionDeniedInline
          message="Cần quyền `pos.session.read` để POS kiểm tra session hiện tại và hiển thị workspace bán hàng an toàn."
          title="Không thể mở POS workspace"
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
          title="Không thể tải POS session"
        />
      </section>
    )
  }

  return (
    <section className="page-stack pos-home-page">
      {reusedSessionCode ? (
        <div className="inline-banner inline-banner-success">
          Đã dùng lại open session hiện có: <strong>{reusedSessionCode}</strong>
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
        <FormSection description="Mở ca làm việc trước khi tạo order hoặc nhận payment." title="Open POS session">
          {!isOnline ? (
            <ReadonlyBanner message="Đang offline nên chưa thể mở session mới. Hãy kết nối lại để tiếp tục." />
          ) : !canOpenSessionPermission ? (
            <PermissionDeniedInline
              message="Cần quyền `pos.session.open` để mở ca làm việc mới tại outlet hiện tại."
              title="Không thể mở session"
            />
          ) : null}
          <div className="field-grid">
            <Input label="Region ID" readOnly value={selectedRegionId} />
            <Input label="Outlet ID" readOnly value={selectedOutletId} />
            <Input
              label="Business date"
              onChange={(event) => setBusinessDate(selectedOutletId, event.target.value)}
              type="date"
              value={businessDate}
            />
            <Input label="Currency" readOnly value="VND" />
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
                      regionId: selectedRegionId,
                      outletId: selectedOutletId,
                      businessDate,
                      currencyCode: 'VND',
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
                  regionId: selectedRegionId,
                  outletId: selectedOutletId,
                  businessDate,
                  currencyCode: 'VND',
                  note: openSessionNote || undefined,
                })
              }
              title="Không thể mở session"
            />
          ) : null}
        </FormSection>
      )}

      {closeSessionMutation.error ? (
        <ErrorState
          actionLabel="Retry close"
          message={closeSessionMutation.error instanceof Error ? closeSessionMutation.error.message : 'Failed to close POS session'}
          onAction={() => setCloseDialogOpen(true)}
          title="Không thể đóng session"
        />
      ) : null}

      {currentSession ? (
        <>
          {!isOnline ? (
            <ReadonlyBanner message="POS đang offline. Tạo order mới và đóng session tạm thời bị khóa cho đến khi kết nối trở lại." />
          ) : null}

          {!canReadCatalogPermission ? (
            <PermissionDeniedInline
              message="POS cần cả `catalog.product.read` và `catalog.price.read` để resolve menu theo outlet, availability và effective price."
              title="Không thể tải catalog"
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
                  <Input label="Business date" readOnly value={currentSession.businessDate} />
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
                  title="Không thể tải catalog"
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
              title="Không thể tạo order"
            />
          ) : null}
        </>
      ) : !openSessionsQuery.isLoading ? (
        <EmptyState
          description="Mở session để thấy catalog, cart và tạo order mới."
          title="No open POS session"
        />
      ) : null}

      <ConfirmActionDialog
        confirmLabel="Close session"
        danger
        description="Chỉ nên đóng session khi mọi order đã hoàn tất hoặc đã được hủy."
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
