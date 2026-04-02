# Stitch Route Mapping Baseline

This baseline captures the canonical Stitch families currently wired into the frontend rebuild.
It is intentionally family-level, not a full 85-route exhaustive matrix yet.

| Frontend Surface | Stitch Canonical Screen / Family | Notes |
| --- | --- | --- |
| `/login` | `login_*` family | Implemented as the new auth shell entry surface. |
| `/session-expired` | `login_*` family fallback | Security/error state inside the auth shell. |
| `/unauthorized` | `login_*` family fallback | Permission-denied auth surface. |
| `/home` | `action_hub_system_admin_view` and sibling action hub variants | Implemented as the new role-aware action hub. |
| `AppShell` | `action_hub_system_admin_view` chrome | Sidebar, glass topbar, scope switchers, shell search slot. |
| `PosLayout` | `pos_terminal_outlet_view` | POS chrome now follows the Stitch terminal direction. |
| `/pos/*` | `pos_home_*`, `pos_terminal_outlet_view`, `pos_payment_*`, `pos_session_*` | Wave 2 direct mapping family. |
| `/inventory/*` | `stock_*`, `inventory_*`, `waste_*` | Wave 2 direct mapping family. |
| `/procurement/*` | `po_*`, `gr_*` | Wave 2 direct mapping family. |
| `/regional-ops/*` | `regional_*`, `region_outlet_tree_overview` | Wave 2 direct mapping family. |
| `/catalog/*` | `product_*`, `ingredient_*`, `recipe_*`, `pricing_*` | Wave 3 family mapping. |
| `/hr/*` | `employee_*`, `contract_*`, `attendance_*`, `shift_*`, `payroll_preparation_*` | Wave 3 family mapping. |
| `/finance/*` | `supplier_*`, `payment_requests_*`, `payroll_*`, `system_config_exchange_rates` | Wave 3 family mapping. |
| `/iam/*` | `user_*`, `system_permissions_scopes` | Wave 4 family mapping. |
| `/audit/*` | `audit` forensic patterns | Wave 4 family mapping. |
| `/reports/*` | `regional_sales_dashboard`, `export_jobs_management` | Wave 4 inference family. |

## Current status

- Implemented in code:
  - Indigo Kitchen OS token foundation
  - new auth shell
  - new app shell
  - new POS shell
  - `/home` action hub
  - backend `/ui/action-hub`
  - backend `/ui/shell-context`
- Still pending:
  - full page-by-page route matrix
  - module-by-module screen composition refactors beyond the shared shell/design-system uplift
