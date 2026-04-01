# FERN Frontend — Known Integration Gaps

> **Status:** Stabilization close-out batch — 2026-04-01  
> **Scope:** Frontend-facing gaps only. Backend internals are documented separately.  
> These gaps are **intentionally unresolved** in this batch. Each entry states the impact, the root cause, and the resolution path.

---

## 1. Export Streaming Auth Gap

**Area:** Reports module (export endpoints)  
**Status:** Open — frontend cannot fully resolve  

**Reality:**  
Backend export endpoints stream file responses (CSV/XLSX). The current frontend initiates exports via a standard `fetch` call with the Authorization bearer token in the request header. However, browser-based file downloads initiated via `<a href>` or `window.open` do **not** attach the Authorization header — only `fetch`/XHR do.

**Impact:**  
- If the frontend switches to link-based download (for better UX), the token is silently dropped.
- The current `fetch`-then-Blob pattern is correct but requires the entire export to be buffered in memory before the browser prompts download. For large exports, this is a UX and memory concern.

**Frontend assumption:**  
Authorization is always passed via `fetch` with an Authorization header. No signed-URL or cookie-based auth fallback exists from the backend gateway.

**Resolution path:**  
Backend to expose signed/temporary download URLs, or gateway to support cookie-based auth for download paths. Until then, export size must be bounded.

---

## 2. Mid-Session Token Revocation Gap

**Area:** Auth / IAM  
**Status:** Open — by design until backend pushes revocation events  

**Reality:**  
The frontend holds the JWT in memory (Zustand store). If a user's IAM role is revoked or permissions changed server-side mid-session, the frontend continues to show UI elements based on the stale principal snapshot until the next full re-login or explicit token refresh.

**Impact:**  
- User may see action buttons (e.g., Approve, Mark Paid) that the backend will reject on the next API call.
- The backend enforces the correct permission on every request — the frontend may show stale "can do X" state between refreshes.
- Permission-denied inline UI only shows on explicit page load or query failure, not proactively.

**Mitigations in place:**  
- All mutations produce `ErrorState` on 403 — the user sees a rejection message even if the button was visible.
- `ReadonlyBanner` on all sensitive screens reminds users that displayed state is a snapshot.

**Resolution path:**  
Backend pushes a revocation event via WebSocket/SSE; frontend subscribes and forces token re-validation or logout.

---

## 3. Procurement Read-Only Root Landing Limitation

**Area:** Procurement module  
**Status:** Accepted limitation  

**Reality:**  
The procurement module root (`/procurement`) currently serves as a static read-only landing. There is no server-side purchase order list endpoint with pagination that the frontend can use to render a live queue without scope filtering. The `PurchaseOrdersPage` and `GoodsReceiptsPage` require explicit PO/GR IDs or are reached from known entity hyperlinks.

**Impact:**  
- Operators cannot browse open POs from a root landing without knowing the ID.
- The procurement root does not mirror the "work queue" mental model operators expect.

**Resolution path:**  
Backend to expose a paginated `GET /procurement/purchase-orders?status=OPEN&outletId=X` endpoint. Frontend to add a filterable list page when the endpoint is available.

---

## 4. Contract Detail Endpoint Dependency on Employee Scope

**Area:** HR — Contract Detail  
**Status:** Accepted limitation, documented in UI  

**Reality:**  
The backend does not expose a `GET /hr/contracts/{contractId}` direct lookup endpoint. Contracts are only queryable via `GET /hr/employees/{employeeId}/contracts`. The frontend resolves this by requiring an `employeeId` query param on `/hr/contracts/:contractId`.

**Impact:**  
- Deep linking to a contract detail without `?employeeId=X` shows a fallback resolve form.
- Bookmarked contract URLs without the employee ID require manual input.

**Mitigation in place:**  
- `recentHrLookups.service` caches recent employee→contract associations in localStorage so returning users do not need to re-enter the employee ID.
- The resolve form in `ContractDetailPage` has explicit UX with a `ReadonlyBanner` explaining the gap.

**Resolution path:**  
Backend to add a direct `GET /hr/contracts/{contractId}` endpoint with appropriate scope enforcement.

---

## 5. Finance Supplier Invoice List Endpoint Gap

**Area:** Finance — Payment Requests  
**Status:** Accepted limitation, documented in UI  

**Reality:**  
The finance module's Payment Requests page (`PaymentRequestsPage`) uses a lookup-first model because the backend does not publish a paginated supplier invoice list endpoint accessible to the Finance role without procurement-level permissions.

**Impact:**  
- Finance operators must know or search for invoice IDs rather than browsing a queue.
- The recent-history panel (localStorage) partially mitigates this for repeat lookups.

**Resolution path:**  
Backend to expose `GET /finance/payment-requests?status=PENDING&supplierId=X` with finance-scoped authorization.

---

## 6. Attendance Event Idempotency — Same vs. New Attempt UX

**Area:** HR — Attendance / Workforce  
**Status:** Informational gap — frontend cannot distinguish  

**Reality:**  
The `HrAttendanceService` backend accepts attendance events (CHECK_IN, CHECK_OUT) with idempotency guarantees based on `(employeeId, shiftDate, eventType)`. If the frontend submits a duplicate event, the backend silently deduplicates and returns the existing event.

**Impact:**  
- Frontend has no way to distinguish a new submission from a de-duplicated replay.
- The "success" response looks identical whether it was a new event or a replay.
- No visual feedback to the user about whether their action created a new record.

**Mitigation in place:**  
- Attendance events are read-only in the current frontend (attendance summary is review-only).
- No write path for attendance events exists in the published frontend at this time.

**Resolution path:**  
When a write path is added, backend should return a response flag (e.g., `created: boolean`) to distinguish new vs. de-duplicated. Frontend should show appropriate UX copy.

---

## 7. POS Payment Offline Queue — Same-Attempt vs. New-Attempt UX

**Area:** POS — Order Detail  
**Status:** Partially addressed — idempotency key scoped to attempt  

**Reality:**  
When a payment is submitted offline, the frontend generates an `Idempotency-Key` and queues the action. On reconnect, the same key is retried. If the user navigates away and back, the queued payment is displayed in the queue panel with its status.

**Residual gap:**  
If the user submits a *new* payment for the same order after a failed offline attempt without clearing the queue, the UI shows both the queued item and the new payment form simultaneously. The queue banner explains the state, but a new submission would carry a *different* idempotency key — meaning two payments could be attempted for the same effective intent.

**Mitigation in place:**  
- Queue status banner warns users of pending/retrying/failed queue items before showing the "Add payment" form.
- `canAddPayment` policy gating prevents submission on fully-paid orders.

**Resolution path:**  
Frontend to block new payment submission while a queued payment for the same order is in `pending` or `retrying` state.

---

## 8. Unpublished Standalone Screen Dependencies

**Area:** Workforce module, Regional-Ops module  
**Status:** Acknowledged — screens exist but backend endpoints are partial  

**Reality:**  
The following screens are navigable but depend on backend endpoints that may not be fully implemented or may not be accessible without specific scope privileges:

| Screen | Dependency | Gap |
|--------|-----------|-----|
| `/workforce/attendance-approvals/:id` | `GET /hr/shifts/{shiftAssignmentId}/approval` | Not confirmed as published endpoint |
| `/regional-ops/*` | Regional aggregation endpoints | Read-only, scoped to system principal only |
| `/reports/*` | Report service streaming endpoints | Auth gap documented in §1 above |

**Impact:**  
Navigation to these screens may result in empty states or 404s until backend confirms endpoint availability.

**Resolution path:**  
Per-module backend endpoint publication confirmation needed before promoting these screens to primary navigation.

---

## 9. `window.confirm` Removed from Procurement Actions

**Area:** Procurement — Purchase Order Detail, Goods Receipt Detail  
**Status:** ✅ Fixed in this batch  

`useConfirmAction` (which wraps `window.confirm`) was used for destructive actions in PO and GR detail pages. These have been replaced with `ConfirmActionDialog` for proper async-safe, non-blocking confirmation UX.

**Note:** `useConfirmAction` remains in shared hooks but should not be used in new code. It is a legacy bridge and should be removed after confirming no remaining usages.

---

## 10. Hooks-After-Conditional-Return (React Rules of Hooks)

**Area:** Inventory — StockOverviewPage, InventoryTransactionsPage; Procurement — GoodsReceiptCreatePage  
**Status:** ✅ Fixed in this batch  

These pages had `useState`/query hooks called after early permission-guard `return` statements, violating React's Rules of Hooks. Hooks have been hoisted above all conditional returns. Query enabling is now controlled via the `enabled` flag rather than by placement after the guard.
