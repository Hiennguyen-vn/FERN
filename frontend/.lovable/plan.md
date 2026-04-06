

## Finance Module — Complete CRUD & Workflow Implementation

### Current State
The Finance module has 7 tabs, all displaying data from mock sources with non-functional buttons. No create/edit/delete dialogs exist. Payroll workflow actions (Submit, Approve, Reject, Mark Paid) are rendered but do nothing.

### What Will Be Built

**1. Payroll Periods — Full CRUD**
- Create Period dialog: region selector, start/end date pickers, status selector
- Edit Period: inline or dialog-based editing
- Status actions: Close Period, Lock Period with confirmation dialogs

**2. Payroll Runs — Create + Workflow Actions**
- "New Payroll Run" dialog: select period, region, add employees from pool with salary inputs
- Wire all workflow buttons to actually update state:
  - Draft → Submit for Approval
  - Submitted → Approve / Reject
  - Approved → Mark Paid / Cancel
  - Rejected → Resubmit
- Toast notifications on each transition
- Audit trail entries auto-generated on status change

**3. Chart of Accounts — CRUD**
- "Add Account" dialog: code, name, type (asset/liability/equity/revenue/expense), parent account selector, active toggle
- Edit account via row action button
- Delete with confirmation (only leaf accounts)
- Search filter wired to actually filter the tree

**4. Tax Setup — CRUD**
- "Add Tax Rate" dialog: name, rate %, type selector, default toggle, active toggle
- Edit dialog on edit button click
- Delete with confirmation
- Toggle active/inactive inline

**5. Fiscal Periods — CRUD + Actions**
- "Add Period" dialog: name, start/end dates
- "Close Period" button wired with confirmation dialog
- Reopen closed period action (admin only)

**6. Finance Config — Editable**
- Each setting becomes editable inline or via an edit dialog
- Save/Cancel buttons per section
- Toast on save

**7. Export Center — Request New Export**
- "Request Export" dialog: select module, label, scope
- Retry failed exports button

### Technical Approach

- All state managed via React `useState` (mock data, no DB migration needed since tables already exist but aren't wired)
- Dialog components using existing `Dialog` from `@/components/ui/dialog`
- Form inputs using existing `Input`, `Select`, `Switch` components
- Toast feedback via `sonner`
- Consistent UI patterns matching other modules (KPI cards, search bar, status pills, table layout)

### Files Modified
- `src/components/finance/FinanceModule.tsx` — Add create payroll run dialog, wire workflow actions, add create period dialog, config editing, export request
- `src/components/finance/ChartOfAccountsModule.tsx` — Add/Edit/Delete account dialogs, wire search
- `src/components/finance/TaxSetupModule.tsx` — Add/Edit tax rate dialogs, toggle active
- `src/components/finance/FiscalPeriodsModule.tsx` — Add/Close/Reopen period dialogs

