# Frontend F&B ERP Assessment Report
**Role**: Staff Frontend Engineer takeover review  
**Date**: 2026-03-31  
**Status**: Initial assessment complete

---

## 1. CURRENT STATE (Working Correctly)

### Architecture & Structure
- ✅ **Modular design** with clear feature modules (POS, Catalog, IAM, HR, Finance, Procurement, etc.)
- ✅ **Lazy-loaded routes** with proper error boundaries
- ✅ **Single entry point** - API Gateway pattern enforced
- ✅ **State management** - Zustand + React Query layered approach
  - `useAuthStore`: Auth state with localStorage persistence
  - `useScopeContextStore`: Region/outlet selection context
  - React Query: Server-side caching
- ✅ **TypeScript** with proper type definitions

### Authentication & Authorization
- ✅ **JWT handling**: Secure token parsing and validation via `token.service.ts`
- ✅ **Refresh token flow**: Auto-retry on 401 with graceful fallback to `/session-expired`
- ✅ **Permission-first model**: Uses `FernPrincipal.permissions` array, not role-based
- ✅ **Permission constants**: Well-structured namespace in `permission.constants.ts`
  - `iam.user.read`, `iam.user.write`, `iam.role.*`
  - `pos.session.read`, `pos.order.create`
  - `procurement.po.read`, `procurement.gr.create`
  - `hr.attendance.write`, `hr.payroll.prepare`
  - `finance.payroll.*`, `report.read`, `report.export`
  - etc.

### Scope Handling
- ✅ **Three-tier scope model**:
  1. `scopeRoots`: User's assigned scope from JWT
  2. `accessibleScope`: Optional override for delegation
  3. `selectedRegionId`/`selectedOutletId`: App shell context selection
- ✅ **Scope resolution**: `accessibleScope ?? scopeRoots` ensures proper fallback
- ✅ **Route guards**: `RequireOutletContext` prevents POS access without outlet
- ✅ **Regional Ops**: Complex scope validation with typed resolution states

### API Integration
- ✅ **Gateway-only pattern**: All calls via `gatewayClient` 
- ✅ **HTTP interceptors**:
  - Adds `Authorization`, `X-Correlation-Id`, `Idempotency-Key` headers
  - 401 handling with token refresh
- ✅ **Error handling**: `ApiError` class with typed methods (`isUnauthorized()`, `isForbidden()`, etc.)
- ✅ **React Query**: Proper cache management and stale-while-revalidate

### Testing Infrastructure
- ✅ **Test factories**: `createTestPrincipal()`, `createTestSession()`, `setAuthenticatedSession()`
- ✅ **Permission mocking**: Proper test helpers for scope context
- ✅ **Component tests**: Coverage in permission-critical modules

### Routing & Navigation
- ✅ **Route structure**: 11 main module branches with proper nesting
- ✅ **Auth guard**: `RequireAuth` wrapper on all protected routes
- ✅ **Navigation builder**: `buildNavigation()` filters modules by permissions (permission-first visibility)
- ✅ **Error pages**: 404, /unauthorized, /session-expired properly handled

---

## 2. HIGHEST-RISK ISSUES (Immediate Action Required)

### **CRITICAL: Missing Route-Level Permission Guards**
**Severity**: 🔴 **CRITICAL**  
**Impact**: Frontend permission checks are UI-only; can be bypassed

#### Current State
```tsx
// Routes have NO permission guards, only authentication
{ path: 'procurement', element: <LazyRouteBoundary /> }
{ path: 'workforce', element: <LazyRouteBoundary /> }
{ path: 'hr', element: <LazyRouteBoundary /> }
```

**Risk**: A user with `outlet_set` scope but no `procurement.po.read` permission could:
1. Navigate directly to `/procurement/purchase-orders/new`
2. Frontend renders page (no guard)
3. Page shows `PermissionDeniedInline` UI (too late, already loaded)
4. Backend enforces permission on API call (good) but UX is wrong

#### What Needs Fixing
All 11 main modules + sub-routes need `RequirePermission` guards:
```tsx
// BEFORE: No guard
{ path: 'procurement', element: <LazyRouteBoundary /> }

// AFTER: With guard
{ 
  path: 'procurement',
  element: <RequirePermission permissions={[permissionConstants.procurement.purchaseOrderRead]} />,
  children: [ /* routes */ ]
}
```

**Files to Update**:
- `/frontend/src/app/router/index.tsx` (11 module routes)

---

### **CRITICAL: Hardcoded Permission Returns in Workforce Module**
**Severity**: 🔴 **CRITICAL**  
**Impact**: Anyone with outlet access can record/approve attendance regardless of permissions

#### Current Code
```typescript
// /frontend/src/modules/workforce/services/workforcePermission.service.ts
export function canRecordAttendance() {
  return true  // ← ALWAYS TRUE, ignores permissions
}

export function canApproveAttendance() {
  return true  // ← ALWAYS TRUE, ignores permissions
}
```

#### What's Broken
- `MyAttendancePage` calls `canRecordAttendance()` → always shows UI
- `AttendanceReviewPage` calls `canApproveAttendance()` → always shows UI
- Backend will reject if missing `hr.attendance.write` / `hr.attendance.review` permissions
- But frontend already rendered the page, wasting user time

#### What Needs Fixing
Implement real permission checks:
```typescript
export function canRecordAttendance(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.attendanceWrite)
}

export function canApproveAttendance(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.attendanceReview)
}
```

**Files to Update**:
- `/frontend/src/modules/workforce/services/workforcePermission.service.ts`

---

### **CRITICAL: Empty Procurement Permission Service**
**Severity**: 🔴 **CRITICAL**  
**Impact**: No permission checks on procurement pages

#### Current Code
```typescript
// /frontend/src/modules/procurement/services/procurementPermission.service.ts
// FILE IS EMPTY (0 bytes)
```

#### Pages Affected (No Permission Validation)
- `PurchaseOrderCreatePage` - can create PO with any outlet access
- `PurchaseOrderDetailPage` - can view/edit PO with any outlet access
- `GoodsReceiptCreatePage` - can create GR with any outlet access
- `GoodsReceiptDetailPage` - can view/edit GR with any outlet access

#### What Needs Fixing
Implement permission service:
```typescript
export function canReadPurchaseOrders(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.procurement.purchaseOrderRead)
}

export function canCreatePurchaseOrder(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.procurement.purchaseOrderCreate)
}

export function canReadGoodsReceipts(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.procurement.goodsReceiptRead)
}

export function canCreateGoodsReceipt(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.procurement.goodsReceiptCreate)
}
```

**Files to Update**:
- `/frontend/src/modules/procurement/services/procurementPermission.service.ts`
- `/frontend/src/modules/procurement/routes/procurementRoutes.bundle.ts` (add guard)

---

### **HIGH: Missing Permission Checks on 9 Pages**
**Severity**: 🟠 **HIGH**  
**Impact**: UI rendered for users without permissions

#### Pages Without Permission Validation
1. **Inventory Module** (2 pages):
   - `StockOverviewPage` - only scope checked, no permission check
   - `InventoryTransactionsPage` - only scope checked, no permission check

2. **Workforce Module** (3 pages):
   - `MyAttendancePage` - hardcoded `canRecordAttendance()` returns `true`
   - `AttendanceReviewPage` - hardcoded `canApproveAttendance()` returns `true`
   - `AttendanceDetailPage` - no permission check

3. **Procurement Module** (4 pages):
   - All 4 pages (PurchaseOrder Create/Detail, GoodsReceipt Create/Detail) - no checks

#### What Needs Fixing
Add permission checks to each page before rendering main content:
```tsx
export function StockOverviewPage() {
  const principal = useAuthStore((state) => state.principal)
  
  if (!canReadInventoryBalance(principal)) {
    return <PermissionDeniedInline />
  }
  
  // Render page...
}
```

**Files to Update** (will be done in Batch 1):
- All 9 page components

---

### **MEDIUM: No Validation Before API Calls**
**Severity**: 🟡 **MEDIUM**  
**Impact**: Frontend doesn't check permissions before API calls; relies entirely on backend

#### Current Pattern
```typescript
// Component makes API call WITHOUT checking permissions first
export function PurchaseOrderCreate() {
  const handleCreate = async () => {
    try {
      await procurementApi.createPurchaseOrder(payload)  // ← No permission check before this
    } catch (err) {
      // Backend rejects with 403
    }
  }
}
```

#### What Needs Fixing
Add permission validation before API calls in action handlers:
```typescript
export function PurchaseOrderCreate() {
  const principal = useAuthStore((state) => state.principal)
  
  const handleCreate = async () => {
    if (!canCreatePurchaseOrder(principal)) {
      // Reject optimistically, don't call API
      showError('Permission denied')
      return
    }
    
    try {
      await procurementApi.createPurchaseOrder(payload)
    } catch (err) { /* ... */ }
  }
}
```

**Files to Update**: All action handlers in affected modules

---

### **MEDIUM: Empty Placeholder Permission Files**
**Severity**: 🟡 **MEDIUM**  
**Impact**: Feature stubs exist but aren't implemented

#### Empty Files
1. `/core/permissions/fieldAccess.checker.ts` - for field-level masking
2. `/core/permissions/exportPermission.checker.ts` - for export-level permissions
3. `/core/permissions/permissionMappers.ts` - for permission transformations

#### What These Should Do
1. **fieldAccess.checker.ts**: Validate which fields user can read/write
   - Used by pages showing sensitive fields (SSN, bank account, salary)
   - Should respect `FernPrincipal.fieldAccess` scope

2. **exportPermission.checker.ts**: Validate export permissions separately from read
   - `report.read` ≠ `report.export`
   - `report.payroll.read` ≠ `report.payroll.export`

3. **permissionMappers.ts**: Transform backend permission responses
   - Normalize permission arrays
   - Merge override permissions with base permissions

#### What Needs Fixing
Implement these files in later batches after core permission guards are done.

---

## 3. AT-RISK AREAS (Need Attention)

### Scope Delegation Not Tested
- `accessibleScope` field exists but minimal test coverage
- Future access control could have issues
- Need tests for region_subtree delegation scenarios

### No Permission Change Detection on Token Refresh
- Token refresh updates principal but doesn't check for permission changes
- If permissions revoked server-side, frontend won't know until next login
- Cache should be invalidated when permissions change

### Navigation Config Duplication
- Permissions checked in `buildNavigation()` AND in each page component
- Maintenance burden if permission constant changes
- No DRY principle for permission definitions

### Hardcoded Persona References in Dev
- Login page has hardcoded username: `'bootstrap-admin'` for dev
- Future PRs might add more persona-specific logic
- Should use environment config instead

---

## 4. WHAT'S BUILT CORRECTLY (Don't Change)

✅ **Auth Flow** - JWT parsing, refresh, session rehydration working perfectly  
✅ **Scope Model** - Three-tier approach (roots → accessible → selected) is solid  
✅ **API Gateway Pattern** - All calls properly routed through gateway  
✅ **Test Infrastructure** - Factories and helpers in place  
✅ **Error Handling** - ApiError class with proper typing  
✅ **Route Structure** - Clean module bundling with lazy loading  
✅ **React Query Integration** - Proper cache management  

---

## 5. FIX STRATEGY (By Batch)

### Batch 1: Route Guards & Workforce Permissions
**Goal**: Protect all routes with permission guards; fix workforce hardcoded checks  
**Effort**: Medium (6-8 hours)  
**Risk**: Low (guards only prevent navigation, don't change behavior)

**What Gets Fixed**:
1. Add `RequirePermission` guards to all 11 module routes
2. Implement real permission checks in workforce service
3. Add permission validation to 3 affected workforce pages
4. Update tests to verify route guard behavior

**Files**:
- `router/index.tsx` (add guards to 11 modules)
- `workforce/services/workforcePermission.service.ts` (fix hardcoded returns)
- `workforce/pages/*.tsx` (3 pages with permission checks)

---

### Batch 2: Procurement Service & Page Guards
**Goal**: Complete procurement permission service; add guards to all pages  
**Effort**: Medium (4-6 hours)  
**Risk**: Low (adds missing functionality, no behavior change)

**What Gets Fixed**:
1. Implement `procurementPermission.service.ts` with 4 permission checkers
2. Add `RequirePermission` to procurement route group
3. Add permission checks to 4 procurement pages
4. Update procurement tests

**Files**:
- `procurement/services/procurementPermission.service.ts` (create 4 functions)
- `procurement/routes/procurementRoutes.bundle.ts` (add guard)
- `procurement/pages/*.tsx` (4 pages)

---

### Batch 3: Inventory Guards & Other Missing Checks
**Goal**: Add permission checks to remaining modules  
**Effort**: Low (2-3 hours)  
**Risk**: Low

**What Gets Fixed**:
1. Implement inventory permission service (2 functions)
2. Add guards to inventory routes
3. Add checks to 2 inventory pages
4. Add permission validation before API calls in action handlers

**Files**:
- `inventory/services/inventoryPermission.service.ts` (create)
- `inventory/routes/inventoryRoutes.bundle.ts` (add guard)
- `inventory/pages/*.tsx` (2 pages)

---

### Batch 4: Scope Masking & Advanced Permissions (Future)
**Goal**: Implement field-level and export-level permission checking  
**Effort**: High (12-16 hours)  
**Risk**: Medium (touches auth validation)

**What Gets Fixed** (deferred):
1. Implement `fieldAccess.checker.ts`
2. Implement `exportPermission.checker.ts`
3. Add field masking to sensitive pages
4. Add export permission validation to reports

---

## 6. SUCCESS CRITERIA

✅ **All 11 main module routes have `RequirePermission` guards**  
✅ **No pages render without permission validation**  
✅ **Workforce permission service returns real permission values**  
✅ **Procurement permission service fully implemented**  
✅ **Inventory permission checks in place**  
✅ **Tests verify route guard behavior**  
✅ **Build passes, typecheck passes, tests pass**  
✅ **No hardcoded `return true` in permission checks**  

---

## 7. RISKS & MITIGATIONS

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Removing hardcoded `true` breaks tests | 🟡 Medium | Update workforce tests to expect real permission values |
| Routes guard too strictly | 🟡 Medium | Test with test principals having required permissions |
| Breaking existing pages | 🟠 High | Don't remove existing code, only add guards |
| Cache invalidation on permission change | 🟡 Medium | Document in comments, plan for Batch 4 |

---

## Next Steps

1. **Implement Batch 1** (route guards + workforce)
2. **Verify build/test pass**
3. **Implement Batch 2** (procurement)
4. **Verify build/test pass**
5. **Implement Batch 3** (inventory)
6. **Verify build/test pass**
7. **Document implementation & create PR**
8. **Plan Batch 4** (advanced permissions)

---

**Prepared by**: Staff Frontend Engineer  
**Review Date**: Ready for implementation  
**Target Completion**: Week 1 (Batch 1-3)
