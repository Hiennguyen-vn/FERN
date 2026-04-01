# FERN Staging Verification Guide

> **Audience:** Frontend engineers, QA, product owners  
> **Last updated:** 2026-04  
> **Scope:** Local development stack + staging environment

---

## Quick Start

### 1. Start the Stack

```bash
# Start infrastructure (PostgreSQL, Redis, Kafka)
cd backend
./scripts/bootstrap-local.sh

# In separate terminals, start all services via smoke flow
# (or use docker compose --profile app up if images are built)
SKIP_BUILD=0 SKIP_MIGRATE=1 ./scripts/smoke/run-all.sh
```

### 2. Apply Migrations

```bash
cd backend
./scripts/migrate-platform.sh all
```

### 3. Seed Demo Data

```bash
cd backend
./scripts/seed-demo.sh
```

The script is **idempotent** — re-running is safe. Entities that already exist are skipped via conflict handling.

**Expected output:** A summary table of all created entity IDs and account credentials.

### 4. Start the Frontend

```bash
cd frontend
npm run dev
```

Open **http://localhost:3000**

---

## Demo Account Matrix

All demo accounts share password **`Demo123!`**.

| Username | Role(s) | Scope | Readable Modules | Test Flow |
|----------|---------|-------|-----------------|-----------|
| `demo-cashier` | `staff` | DIST1 outlet | POS | Open POS home → start session → create order → add payment |
| `demo-outlet-mgr` | `outlet_manager` | DIST1 + HCM region | POS, Inventory, Procurement, HR (shift/attendance) | Navigate all operational tabs, verify procurement workflow, shift management |
| `demo-region-mgr` | `outlet_manager` + `regional_finance` | HCM region (all outlets) | POS, Inventory, Procurement, HR, Finance (read), Reports | Approve supplier invoice, view regional payroll status |
| `demo-reg-finance` | `regional_finance` | HCM region | Procurement (approval), Finance (payroll read), Reports | Approve PO-DEMO-002, verify ReadonlyBanner on ISSUED POs |
| `demo-hr` | `hr` | HCM region | HR (full: employee, contract, shift, attendance, payroll prep) | View employee contracts → salary visible in `fully-visible` mode, prepare payroll |
| `demo-finance` | `finance` | SYSTEM | Finance (payroll approve/pay), Reports, Supplier master | Approve submitted payroll run, export payroll report, view supplier payment records |
| `demo-product-mgr` | `product_manager` | SYSTEM | Catalog (full CRUD) | Create ingredient, add recipe version, set product price |
| `demo-sysadmin` | `system_admin` | SYSTEM | IAM (users, roles, permissions, overrides), Audit | View users, manage role assignments, inspect audit trail |
| `demo-audit` | `system_admin` | SYSTEM | Audit (full: events, detail, export) | Filter audit events, view detail payload, trigger export |
| `demo-readonly` | `regional_finance` | DIST3 outlet only | Limited: sees no DIST1 data | Verify scope isolation — procurement/inventory screens empty for DIST1 entities |

---

## Per-Module Verification Flows

### 🏠 Home / Dashboard
- Login as any user → dashboard should load without error
- Scope context selector shows `District 1 Demo Outlet` for outlet-scoped users

### 🛒 POS
1. Login as `demo-cashier` or `demo-outlet-mgr`
2. Select `District 1 Demo Outlet` from scope selector
3. Open POS home → open a new session
4. Create an order → add `Hot Latte` or `Iced Americano` (seeded products)
5. Submit payment → order completes
6. **Note:** POS orders are created live, not pre-seeded. The POS catalog and session flow are the primary test vectors here.

### 📦 Procurement
1. Login as `demo-outlet-mgr` → navigate to `/procurement/purchase-orders`
2. **Terminal state test:** Open PO-DEMO-001 (ISSUED) → verify `ReadonlyBanner` appears above entity header, no action buttons visible
3. **In-queue test:** Open PO-DEMO-002 (SUBMITTED) → verify `Approve` button is present
4. Login as `demo-reg-finance` → navigate to same PO-DEMO-002 → verify Approve button works
5. Navigate to Goods Receipts → GR-DEMO-001 (POSTED) → verify ReadonlyBanner, no Post/Cancel buttons
6. **Confirm dialog test:** Create a new GR → complete receive → click Post → verify ConfirmActionDialog appears

### 📊 Inventory
1. Login as `demo-outlet-mgr` → select DIST1 outlet
2. Navigate to `/inventory/stock-overview` → should show non-zero balances for Arabica Coffee, Full Cream Milk, White Sugar
3. Navigate to `/inventory/transactions` → should show OPENING_BALANCE and PURCHASE_IN transactions

### 👥 HR / Workforce
1. Login as `demo-hr`
2. Navigate to `/hr/employees` → verify 3 employees visible (EMP-DEMO-001, 002, 003)
3. Click on EMP-DEMO-001 → Employee Detail
4. Navigate to contract → **salary visible in `fully-visible` mode** (HR has `hr.contract.detail.read`)
5. Login as `demo-reg-finance` → same contract → **salary masked** (no `hr.contract.detail.read`)
6. Navigate to `/workforce/attendance` → DIST1 filter → verify CLOCK_IN/CLOCK_OUT events + APPROVED status

### 📋 Catalog
1. Login as `demo-product-mgr`
2. Navigate to `/catalog/products` → verify `Hot Latte` and `Iced Americano`
3. Navigate to `/catalog/ingredients` → verify 3 ingredients with ACTIVE status
4. Navigate to `/catalog/products/[latte-id]` → Recipe tab → verify recipe version v1 with ingredient proportions

### 💰 Finance / Payroll
1. Login as `demo-hr` → navigate to `/finance/payroll` → verify 2 payroll periods
2. View Payroll Run 1 (PAID) → **terminal state: ReadonlyBanner shown, no Mark Paid button**
3. View Payroll Run 2 (SUBMITTED) → verify Approve button visible
4. Login as `demo-finance` → approve Payroll Run 2 → verify status transition to APPROVED
5. Navigate to Supplier Payments → verify INV-DEMO-2026-001 settlement record

### 📈 Reports
1. Login as `demo-finance` or `demo-reg-finance`
2. Navigate to `/reports` → trigger payroll summary report
3. Navigate to export job → verify status (may be COMPLETED from seed trigger)
4. **Auth gap:** Download via `fetch` works; download via `<a href>` drops auth header (see Known Gaps)

### 🔑 IAM
1. Login as `demo-sysadmin`
2. Navigate to `/iam/users` → verify 10 demo users visible
3. Navigate to `/iam/roles` → verify all system roles present
4. Navigate to `/iam/permissions` → full permission catalog visible

### 🔍 Audit
1. Login as `demo-audit`
2. Navigate to `/audit/events` → should show events from seed operations (employee creation, PO lifecycle, payroll lifecycle)
3. Open an event → verify event detail payload visible (has `audit.detail.read`)
4. **Verify masking:** Login as `demo-outlet-mgr` → audit events should return 403 (no `audit.read` permission)

### 🏢 Org / Regional Ops
1. Login as any user with `org.region.read`
2. Navigate to `/org/regions` → verify `DEMO-HCM` region
3. Navigate to `/org/outlets` → verify DIST1 and DIST3 outlets

---

## Edge Case Verification

| Scenario | Account | Expected Behavior |
|----------|---------|------------------|
| Permission denied inline | `demo-cashier` → `/hr/employees` | `PermissionDeniedInline` in DashboardLayout |
| ReadonlyBanner on terminal PO | `demo-outlet-mgr` → PO-DEMO-001 | Banner renders ABOVE entity header |
| Masked salary for non-HR | `demo-reg-finance` → Employee contract | Salary shows `••••••••` |
| Full salary for HR | `demo-hr` → Employee contract | Salary shows real value |
| Scope isolation | `demo-readonly` → Inventory (DIST1) | Empty state — no DIST1 data visible |
| Idempotent confirm dialog | `demo-outlet-mgr` → Cancel PO-DEMO-002 | ConfirmActionDialog appears before action fires |
| Terminal GR, no actions | `demo-outlet-mgr` → GR-DEMO-001 | ReadonlyBanner, zero action buttons |
| PAID payroll, no mark-paid | `demo-finance` → Payroll Run 1 | ReadonlyBanner, no Mark Paid button |

---

## Known Gaps (Do Not Test)

> These gaps are documented in `/FRONTEND_KNOWN_GAPS.md`. Do not attempt to verify them — they are intentionally unresolved.

1. **Export streaming download via `<a href>`** — auth header dropped; use `fetch`-based download button in UI
2. **Contract direct deep link** — requires `?employeeId=X` query parameter; bookmark without it shows resolve form
3. **Mid-session token revocation** — stale JWT shows ghost buttons until next page load
4. **Supplier invoice list** — no paginated list endpoint; finance module uses lookup-first model
5. **POS order list** — no persistent list endpoint; POS orders only accessible live from active session
6. **Attendance write path** — attendance events are read-only in currently published frontend screens

---

## Re-seeding

To reset and re-seed:

```bash
# Option 1: Drop and recreate databases (full reset)
KEEP_INFRA_UP=1 SKIP_SMOKE=1 ./scripts/bootstrap-local.sh
./scripts/migrate-platform.sh all
./scripts/seed-demo.sh

# Option 2: Re-run seed only (entities with same codes will be skipped)
./scripts/seed-demo.sh
```

> **Note:** The seed script does not delete existing data. If you need a clean slate, use Option 1.

---

## Ports Reference

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| IAM Service | 8081 |
| Org Service | 8082 |
| Audit Service | 8084 |
| Catalog Service | 8085 |
| POS Service | 8086 |
| Inventory Service | 8087 |
| Procurement Service | 8088 |
| HR Service | 8089 |
| Report Service | 8090 |
| Finance Service | 8091 |
| PostgreSQL | 55432 |
| Redis | 6379 |
| Kafka | 9092 |
| Frontend Dev Server | 3000 |
