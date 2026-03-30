# Kế hoạch UI/UX & Frontend Implementation Plan — ERP F&B

**Phiên bản:** 1.1  
**Ngày:** 29/03/2026  
**Mục tiêu:** Cung cấp kế hoạch triển khai UI/UX và frontend đủ chi tiết để đội ngũ có thể bắt đầu implement toàn bộ dự án theo từng phase, bám sát kiến trúc thư mục frontend đã chốt và định hướng thiết kế từ tài liệu UI/UX gốc.

---

# 1. Mục tiêu tài liệu

Tài liệu này dùng để chuyển từ giai đoạn **định hướng kiến trúc + design direction** sang giai đoạn **thực thi thực tế**.

Nó trả lời 10 câu hỏi chính:

1. Làm module nào trước, module nào sau  
2. Actor nào dùng những màn nào  
3. Màn nào là P0/P1/P2  
4. Từng phase cần giao những gì  
5. Team UX/UI phải produce những gì trước khi dev code  
6. Team frontend map screen vào module/folder như thế nào  
7. API, permission, scope, lifecycle phản ánh vào UI ra sao  
8. Layout nào dùng cho loại màn nào  
9. Từng bước triển khai từ wireframe đến production  
10. Định nghĩa “Done” cho từng giai đoạn là gì  

---

# 2. Nguồn gốc và phạm vi

Tài liệu này kế thừa và mở rộng từ:
- định hướng thiết kế UI/UX gốc
- screen planning Phase 1
- kiến trúc thư mục frontend đã chốt
- mapping role / scope / lifecycle của hệ thống ERP F&B

Nó không thay thế:
- SRS nghiệp vụ
- API contract chi tiết
- design system specification chi tiết

Mà đóng vai trò là **master execution plan** cho UX/UI + frontend implementation.

---

# 3. Nguyên tắc triển khai tổng thể

## 3.1 Triết lý triển khai
- **Phase-first, không big bang**
- **Ưu tiên outlet operations trước back-office**
- **Thiết kế theo action-first, không dashboard-only**
- **UI phải phản ánh permission + scope + lifecycle**
- **Mọi màn hình quan trọng phải đi qua screen spec trước khi code**
- **Không code tràn màn hình nếu chưa có route → API → state → UX contract rõ ràng**
- **Reuse layout/pattern trước, tránh vẽ lại từ đầu cho mỗi module**

## 3.2 Ưu tiên business
Thứ tự ưu tiên nghiệp vụ:
1. Auth + shell
2. POS
3. Procurement
4. Inventory
5. Workforce
6. HR
7. Finance
8. Reports
9. IAM / Audit / Org / Catalog / Regional Ops

## 3.3 Ưu tiên UX
- **Touch-first** cho POS
- **Table-first** cho Procurement / Inventory / Admin / Audit
- **Approval-first** cho Finance / Regional Finance / Attendance review
- **Section-first** cho Employee / Contract / Payroll prep
- **Dashboard action-first** cho Outlet Dashboard / Regional Dashboard

---

# 4. Phương thức triển khai từng bước

Đây là quy trình chuẩn để triển khai một module hoặc một màn hình mới.

## Bước 1 — Chốt business objective
Cho mỗi màn, trả lời:
- user là ai?
- mục tiêu hành động chính là gì?
- output business mong đợi là gì?
- trạng thái nào được tạo ra hoặc thay đổi?

**Ví dụ:**  
Màn `Goods Receipt Create`
- user: Outlet Manager
- mục tiêu: ghi nhận số lượng nhận thực tế
- output: GR từ `DRAFT` → `RECEIVED` hoặc `POSTED`
- tác động business: chuẩn bị tăng tồn kho và cập nhật PO progress

## Bước 2 — Chốt screen contract
Xác định:
- route
- API dùng
- request/response
- permission
- scope
- lifecycle
- idempotency requirement
- empty/loading/error states

## Bước 3 — Chọn layout mẫu
Mỗi screen phải chọn 1 layout gốc:
- POS Layout
- Dashboard Layout
- Table-Detail Layout
- Approval Layout
- Form Layout
- Report/Export Layout
- Auth Layout

Không thiết kế tự do nếu chưa có lý do đặc biệt.

## Bước 4 — Vẽ low-fi wireframe
Phải có:
- hierarchy của thông tin
- vị trí action chính
- vị trí status
- vị trí filter/search
- skeleton loading
- empty/error states

## Bước 5 — Viết screen spec
Bao gồm:
- fields
- components
- actions
- validation
- readonly rules
- action visibility rules
- UI notes
- frontend notes
- QA notes

## Bước 6 — Mapping vào frontend architecture
Map screen vào:
- `routes/`
- `api/`
- `model/`
- `components/`
- `services/`
- `hooks/`
- `forms/`
- `state/` nếu cần

## Bước 7 — Implement design-system usage
Không code UI trực tiếp bằng div/button/input thô nếu đã có component tương ứng.
Mọi màn phải dùng:
- common layout
- common table wrapper
- common form wrappers
- common status badges
- common confirm dialog

## Bước 8 — Build UI policy
Action/lifecycle logic phải nằm ở:
- `*UiPolicy.service.ts`
- hoặc `*Workflow.service.ts`

Không hardcode ở JSX.

## Bước 9 — Test screen contract
Test:
- permission
- scope
- lifecycle transitions
- loading/error/empty
- destructive action confirmation
- readonly terminal states

## Bước 10 — Polish & ready for release
- responsive check
- accessibility check
- copywriting check
- analytics/logging if needed
- final QA checklist

---

# 5. Personas & ứng dụng vào implementation

## 5.1 Staff / Cashier
### Mục tiêu
- thao tác cực nhanh
- ít màn
- ít quyết định
- trạng thái session và payment rõ

### Màn chính
- Login
- POS Home
- Order Detail / Cart
- Payment
- My Attendance

### Rule implementation
- POS screens ưu tiên lớn, đơn giản, ít chrome
- Không nhét nhiều bảng điều hướng
- Luôn hiển thị session status + network status

---

## 5.2 Outlet Manager
### Mục tiêu
- điều hành outlet
- thấy task pending
- xử lý procurement/inventory/workforce nhanh

### Màn chính
- Outlet Dashboard
- Session Control
- Session Reconciliation
- PO List/Create/Detail
- GR List/Create/Detail
- Stock Overview
- Waste / Adjustment / Stock Count
- Shift / Attendance Review

### Rule implementation
- Dashboard phải là action dashboard
- Table/detail/timeline là layout chính
- Badge trạng thái phải rõ

---

## 5.3 Regional Finance
### Mục tiêu
- duyệt chứng từ trong vùng
- thấy trace chain rõ

### Màn chính
- PO Approval Queue
- PO Approval Detail
- Payment Request Queue
- Payment Request Detail
- Payroll reports vùng

### Rule implementation
- Queue-based layout
- Approval panel sticky
- Link trace PO → GR → Invoice → Payment

---

## 5.4 HR
### Mục tiêu
- employee/contracts
- attendance summary
- payroll prep

### Rule implementation
- Forms chia section
- Detail pages phải hỗ trợ field masking
- Payroll prep cần exception panel riêng

---

## 5.5 Finance
### Mục tiêu
- supplier master
- payroll approval / paid
- reports

### Rule implementation
- Approval layouts rõ
- Terminal states readonly mạnh
- Audit metadata hiện rõ ở các action nhạy cảm

---

## 5.6 System Admin
### Mục tiêu
- user/role/scope
- effective access
- audit/debug permission

### Rule implementation
- Cần explainable access UI
- Permission matrix/table-first
- Route + component guards phải test được

---

# 6. Information Architecture final

## 6.1 Top-level navigation
- Home
- POS
- Procurement
- Inventory
- Workforce
- HR
- Finance
- Reports
- Regional Ops
- Catalog
- IAM
- Org
- Audit

## 6.2 Navigation theo role

### Staff
- Home
- POS
  - POS Home
  - Orders
  - Payments
- Workforce
  - My Attendance

### Outlet Manager
- Home
- POS
  - Session Control
  - Session Reconciliation
  - Orders
- Procurement
  - Purchase Orders
  - Goods Receipts
- Inventory
  - Stock Overview
  - Transactions
  - Waste
  - Adjustments
  - Stock Count
- Workforce
  - Shift Templates
  - Shift Assignments
  - Attendance Review

### Region Manager
- Home
- Regional Ops
- Reports

### Regional Finance
- Home
- Procurement Approval
- Reports

### HR
- Home
- HR
- Reports

### Finance
- Home
- Finance
- Reports

### Product Manager
- Home
- Catalog

### System Admin
- Home
- IAM
- Org
- Audit

---

# 7. Layout mẫu chuẩn

Phần này là bộ layout mẫu để team UX/UI và frontend reuse nhất quán.

---

## 7.1 App Shell Layout

**Dùng cho:** mọi màn back-office không phải POS fullscreen.

```text
┌──────────────────────────────────────────────────────────────────────┐
│ TOP BAR                                                             │
│ [Logo] [Breadcrumb / Title]         [Global Search] [Bell] [User]   │
├───────────────┬──────────────────────────────────────────────────────┤
│ SIDEBAR       │ MAIN CONTENT                                         │
│               │                                                      │
│ Home          │ ┌──────────────────────────────────────────────────┐ │
│ POS           │ │ PAGE HEADER                                      │ │
│ Procurement   │ │ [Title] [Subtitle]            [Primary Actions]  │ │
│ Inventory     │ ├──────────────────────────────────────────────────┤ │
│ Workforce     │ │                                                  │ │
│ HR            │ │ PAGE CONTENT                                     │ │
│ Finance       │ │                                                  │ │
│ Reports       │ │                                                  │ │
│ Catalog       │ │                                                  │ │
│ IAM           │ │                                                  │ │
│ Org           │ │                                                  │ │
│ Audit         │ └──────────────────────────────────────────────────┘ │
└───────────────┴──────────────────────────────────────────────────────┘
```

### Quy tắc
- sidebar chỉ chứa module level navigation
- top bar chứa user/session/global states
- page header phải luôn có title rõ
- breadcrumb chỉ bật ở màn sâu

---

## 7.2 POS Layout (Fullscreen, touch-first)

**Dùng cho:** POS Home, Order, Payment

```text
┌──────────────────────────────────────────────────────────────────────┐
│ POS HEADER                                                          │
│ [← Back] [Outlet] [Session Status] [Network Status] [Time] [User]   │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────┬──────────────────────────────────┐ │
│  │ MENU / SEARCH / CATEGORY     │ ORDER / CART                     │ │
│  │                              │                                  │ │
│  │ [Search]                     │ [Order Header]                   │ │
│  │ [Category Tabs]              │ [Order Items]                    │ │
│  │ [Product Grid/List]          │ [Subtotal / Tax / Total]         │ │
│  │                              │ [Primary CTA: Payment]           │ │
│  └──────────────────────────────┴──────────────────────────────────┘ │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Quy tắc
- session status luôn visible
- network status luôn visible
- cart luôn visible
- primary CTA luôn dễ thấy
- touch target lớn
- tối đa hóa số thao tác bằng một tay/ít click nhất có thể

---

## 7.3 Dashboard Layout

**Dùng cho:** Outlet Dashboard, Regional Dashboard, Reports Dashboard

```text
┌──────────────────────────────────────────────────────────────────────┐
│ PAGE HEADER                                                         │
│ [Dashboard Title]                              [Filters] [Refresh]   │
├──────────────────────────────────────────────────────────────────────┤
│ KPI CARDS                                                           │
│ ┌────────────┬────────────┬────────────┬────────────┐                │
│ │ Card 1     │ Card 2     │ Card 3     │ Card 4     │                │
│ └────────────┴────────────┴────────────┴────────────┘                │
│                                                                      │
│ ┌─────────────────────────────────┬────────────────────────────────┐ │
│ │ MAIN PANEL                      │ SIDE PANEL                     │ │
│ │ chart / summary / recent        │ pending tasks / alerts         │ │
│ └─────────────────────────────────┴────────────────────────────────┘ │
│                                                                      │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ RECENT ACTIVITIES / TASK TABLE                                  │ │
│ └──────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### Quy tắc
- đây là action dashboard, không phải report-only dashboard
- cards phải dẫn tới hành động hoặc detail
- pending tasks luôn nằm trên màn
- dashboard widget có thể load độc lập

---

## 7.4 Table-Detail Layout

**Dùng cho:** PO List/Detail, GR List/Detail, Employees, Suppliers, Users, Audit

```text
┌──────────────────────────────────────────────────────────────────────┐
│ PAGE HEADER                                                         │
│ [Screen Title]                                  [Primary Action]    │
├──────────────────────────────────────────────────────────────────────┤
│ TOOLBAR                                                             │
│ [Search] [Filters] [Status Tabs] [Export]                           │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  TABLE / LIST                                                        │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ Row 1                                                          │  │
│  │ Row 2                                                          │  │
│  │ Row 3                                                          │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│ [Pagination / hasMore handling]                                      │
└──────────────────────────────────────────────────────────────────────┘

↓ Click row →

┌──────────────────────────────────────────────────────────────────────┐
│ DETAIL PAGE or DETAIL DRAWER                                         │
│ [Header + Status + Actions]                                          │
│ [Sections / Timeline / Linked docs / Audit meta]                     │
└──────────────────────────────────────────────────────────────────────┘
```

### Quy tắc
- screen list và detail có thể tách route hoặc dùng drawer
- nếu detail phức tạp hơn 3 section → dùng detail page
- nếu detail chỉ quick review → dùng drawer
- status badge và actions phải gần header

---

## 7.5 Approval Layout

**Dùng cho:** PO Approval, Payroll Approval, Attendance Review Detail, Payment Request Detail

```text
┌──────────────────────────────────────────────────────────────────────┐
│ PAGE HEADER                                                         │
│ [Approval Title]                                      [Close/Back]   │
├──────────────────────────────────────────────────────────────────────┤
│ SUMMARY BANNER                                                      │
│ [Key metrics / who submitted / when / current status]               │
├──────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────┬───────────────────────────┐ │
│ │ DETAIL CONTENT                       │ ACTION PANEL              │ │
│ │                                      │                           │ │
│ │ sections / tabs / line items         │ status                    │ │
│ │ history / timeline / audit           │ comment box               │ │
│ │ linked documents                     │ approve / reject buttons  │ │
│ └──────────────────────────────────────┴───────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### Quy tắc
- action panel sticky nếu màn dài
- history/timeline luôn có
- approval comment phải rõ
- hành động destructive cần confirm

---

## 7.6 Form Layout (multi-section)

**Dùng cho:** PO Create, GR Create, Employee Detail, Contract Detail, Product Detail

```text
┌──────────────────────────────────────────────────────────────────────┐
│ PAGE HEADER                                                         │
│ [Title]                                             [Cancel] [Save] │
├──────────────────────────────────────────────────────────────────────┤
│ SECTION 1                                                           │
│ [Basic info fields]                                                 │
├──────────────────────────────────────────────────────────────────────┤
│ SECTION 2                                                           │
│ [Line items / details / nested data]                                │
├──────────────────────────────────────────────────────────────────────┤
│ SECTION 3                                                           │
│ [Summary / totals / validations]                                    │
├──────────────────────────────────────────────────────────────────────┤
│ STICKY FOOTER                                                       │
│                                          [Cancel] [Save] [Submit]   │
└──────────────────────────────────────────────────────────────────────┘
```

### Quy tắc
- section title rõ
- sticky footer cho form dài
- field lỗi hiển thị inline
- readonly sections phải visually khác editable sections

---

## 7.7 Report / Export Layout

**Dùng cho:** Reports, Export Jobs, Preview

```text
┌──────────────────────────────────────────────────────────────────────┐
│ PAGE HEADER                                                         │
│ [Reports Title]                                   [Export Action]   │
├──────────────────────────────────────────────────────────────────────┤
│ FILTER PANEL                                                        │
│ [Date Range] [Outlet/Region] [Report Type] [Apply]                  │
├──────────────────────────────────────────────────────────────────────┤
│ SUMMARY / CHART / TABLE                                              │
│ [Cards] [Charts] [Data Table]                                       │
├──────────────────────────────────────────────────────────────────────┤
│ EXPORT JOBS / ASYNC STATUS                                          │
│ [Queued] [Running] [Completed] [Failed]                             │
└──────────────────────────────────────────────────────────────────────┘
```

### Quy tắc
- export không chặn user ở màn hiện tại
- status async job phải rõ
- preview/download tách riêng

---

## 7.8 Auth Layout

**Dùng cho:** Login, Session Expired, Unauthorized

```text
┌──────────────────────────────────────────────┐
│                 AUTH CARD                    │
│           [Logo / Product name]              │
│                                              │
│             [Title / Subtitle]               │
│             [Form / Message]                 │
│             [Primary Action]                 │
└──────────────────────────────────────────────┘
```

### Quy tắc
- càng đơn giản càng tốt
- lỗi phải rõ nhưng không technical
- chỉ tập trung vào action chính

---

# 8. Module map → frontend folder map

## 8.1 Mapping chuẩn

| Domain | Frontend module |
|---|---|
| Auth | `modules/auth/` |
| POS | `modules/pos/` |
| Procurement | `modules/procurement/` |
| Inventory | `modules/inventory/` |
| Workforce | `modules/workforce/` |
| HR | `modules/hr/` |
| Finance | `modules/finance/` |
| Reports | `modules/reports/` |
| Regional Ops | `modules/regional-ops/` |
| Catalog | `modules/catalog/` |
| IAM | `modules/iam/` |
| Org | `modules/org/` |
| Audit | `modules/audit/` |

## 8.2 Pattern chuẩn cho mọi module
```text
routes/      -> page containers
api/         -> transport, queries, mutations
model/       -> types, DTO contracts, enums
components/  -> module-specific UI
services/    -> UiPolicy, workflow, permission glue
hooks/       -> orchestration hooks
forms/       -> schema, mapper, form hook
state/       -> module-local complex state only
tests/       -> colocated tests
```

---

# 9. Deliverables UX/UI trước khi frontend code

## 9.1 Deliverables bắt buộc theo thứ tự

### A. UX Blueprint
- personas
- module map
- navigation by role
- top user flows
- screen inventory

### B. Screen Inventory
Mỗi screen có:
- ID
- module
- route
- role
- priority
- API chính
- state chính

### C. Wireframes
- low-fi trước
- high-fi sau
- bắt đầu từ P0 screens

### D. Screen Specs
Mỗi screen cần:
- goal
- layout
- components
- fields
- actions
- permission rules
- scope rules
- lifecycle rules
- empty/loading/error/success states
- frontend notes
- QA notes

### E. Component Spec
- DataTable
- FilterBar
- StatusBadge
- ApprovalPanel
- ConfirmActionDialog
- CurrencyInput
- QuantityInput
- AsyncJobProgress
- NetworkOfflineBanner
- MaskedField

---

# 10. Screen Inventory tổng thể

## 10.1 Phase 0

### Auth
- Login
- Logout
- Unauthorized
- Session Expired

### Shell
- AppShell
- role-based navigation
- route guards
- default home routing

---

## 10.2 Phase 1

### Home
- Outlet Dashboard

### POS
- POS Home
- Orders List
- Order Detail / Cart
- Payment
- Session Control
- Session Reconciliation

### Procurement
- Purchase Orders List
- Purchase Order Create
- Purchase Order Detail
- Goods Receipts List
- Goods Receipt Create
- Goods Receipt Detail

### Inventory
- Stock Overview
- Inventory Transactions
- Waste Record Create/List
- Stock Adjustment Create/List
- Stock Count Sessions List
- Stock Count Entry
- Stock Count Review

### Workforce
- My Attendance
- Shift Templates
- Shift Assignments
- Attendance Review
- Attendance Detail

---

## 10.3 Phase 2

### HR
- Employees List
- Employee Detail
- Contracts List
- Contract Detail
- Attendance Summary
- Payroll Preparation
- Payroll Draft Review

### Finance
- Suppliers List
- Supplier Detail
- Payment Requests
- Payroll Approval Queue
- Payroll Approval Detail
- Payroll Paid

### Reports
- Reports Dashboard
- Revenue Report
- Inventory Report
- Payroll Report
- Export Jobs
- Export Preview
- Export Download

---

## 10.4 Phase 3

### IAM
- Users
- User Detail
- Roles
- Assignments
- Effective Access

### Org
- Regions
- Region Detail
- Outlets
- Outlet Detail
- Currencies
- Exchange Rates

### Audit
- Audit Events
- Audit Detail
- Security Events
- Request Traces

### Catalog
- Products
- Product Detail
- Ingredients
- Recipes
- Pricing
- Availability

### Regional Ops
- Regional Dashboard
- Outlet Summary
- Outlet Detail

---

# 11. Screen priority matrix

## P0 — phải có để chạy business
- Login
- POS Home
- Order Detail / Cart
- Payment
- Outlet Dashboard
- Session Control
- Session Reconciliation
- PO List/Create/Detail
- GR List/Create/Detail
- Stock Overview
- Inventory Transactions
- Waste / Adjustment
- Stock Count List/Entry/Review
- My Attendance
- Attendance Review

## P1 — cần sớm để vận hành back-office
- Employees / Contracts
- Attendance Summary
- Payroll Preparation / Draft Review
- Suppliers
- Payroll Approval / Paid
- Reports Dashboard
- Export Jobs

## P2 — quản trị và mở rộng
- IAM
- Audit
- Org
- Catalog
- Regional Ops

---

# 12. Wireframe plan

## 12.1 Wireframe phải làm trước
1. Login
2. Outlet Dashboard
3. POS Home
4. Order Detail / Cart
5. Payment
6. Session Control
7. Purchase Order Create
8. Purchase Order Detail
9. Goods Receipt Create
10. Stock Count Entry
11. Attendance Review
12. Employee Detail
13. Payroll Draft Review
14. Payroll Approval Detail
15. User Detail / Assignments / Effective Access
16. Export Jobs

## 12.2 Thứ tự ưu tiên wireframe
### Wave 1
- Login
- POS Home
- Payment
- Outlet Dashboard
- PO Create

### Wave 2
- PO Detail
- GR Create
- Stock Count Entry
- Attendance Review

### Wave 3
- Employees
- Payroll Draft Review
- Payroll Approval
- IAM Effective Access
- Export Jobs

---

# 13. Screen Spec template chuẩn

Mỗi màn phải có đúng format này:

1. Screen ID  
2. Screen Name  
3. Module  
4. Primary Roles  
5. Goal  
6. Route  
7. Entry Points  
8. APIs Used  
9. Layout Structure  
10. Main Components  
11. Main Fields  
12. Main Actions  
13. Permission Rules  
14. Scope Rules  
15. Lifecycle / State Rules  
16. Validation Rules  
17. Empty / Loading / Error / Success States  
18. UX Notes  
19. Frontend Technical Notes  
20. QA Notes  

---

# 14. Layout strategy theo loại màn

## 14.1 POS Layout
Dùng cho:
- POS Home
- Order / Cart
- Payment

**Đặc điểm**
- fullscreen
- touch-first
- status visible
- ít chrome
- cart luôn visible

## 14.2 Dashboard Layout
Dùng cho:
- Outlet Dashboard
- Regional Dashboard
- Reports Dashboard

**Đặc điểm**
- summary cards
- pending tasks
- shortcuts
- top filters

## 14.3 Table-Detail Layout
Dùng cho:
- PO / GR
- Employees
- Suppliers
- Users
- Audit

**Đặc điểm**
- table/list + detail
- filters/search/status tabs
- row click → detail page or drawer

## 14.4 Approval Layout
Dùng cho:
- PO Approval
- Payroll Approval
- Attendance Review Detail
- Payment Request Detail

**Đặc điểm**
- summary on top
- detail in middle
- sticky action panel
- audit meta visible

## 14.5 Form-heavy Layout
Dùng cho:
- PO Create
- GR Create
- Employee Detail
- Contract Detail
- Product Detail

**Đặc điểm**
- section-based
- sticky footer actions
- validation rõ
- readonly sections rõ

## 14.6 Report Layout
Dùng cho:
- Reports Dashboard
- Revenue / Inventory / Payroll report
- Export jobs / preview

**Đặc điểm**
- filter panel nổi rõ
- summary trước table
- async job states rõ

---

# 15. Design system plan

## 15.1 Atomic components
- Button
- Input
- Select
- Textarea
- DatePicker
- Dialog
- Drawer
- Badge
- Tabs
- Pagination
- Skeleton
- EmptyState
- ErrorState
- LoadingOverlay
- Checkbox
- Radio
- Tooltip

## 15.2 Enterprise patterns
- EntityHeader
- StatusBadge
- SummaryCards
- FilterBar
- SearchToolbar
- DetailDrawer
- ApprovalPanel
- AuditMetaBlock
- ConfirmActionDialog
- AsyncJobProgress

## 15.3 Form patterns
- FormField
- FormSection
- FormActions
- CurrencyInput
- QuantityInput
- MaskedField

## 15.4 Table patterns
- DataTable
- DataTableToolbar
- DataTableFilters
- DataTablePagination
- DataTableColumnToggle

---

# 16. State management plan

## 16.1 Server state
Dùng TanStack Query cho:
- list data
- detail data
- dashboard data
- approval queues
- reports
- export job polling

## 16.2 Client/business state
Dùng Zustand cho:
- auth state
- current selected outlet/region context
- cart
- POS UI state
- purchase order draft nếu cần
- module-local complex state

## 16.3 Shared state factories
Dùng cho:
- list filters
- pagination state đơn giản

Áp dụng cho:
- PO filters
- inventory transaction filters
- employee filters
- audit filters
- report filters

---

# 17. Permission & scope plan

## 17.1 Route guard
Mọi màn nghiệp vụ đều đi qua:
- auth guard
- permission guard
- scope guard nếu cần
- outlet context guard với module outlet-scoped

## 17.2 Component/action guard
Action hiển thị theo:
- permission
- scope
- lifecycle state

Ví dụ:
- Issue PO chỉ hiện khi `APPROVED`
- Post GR chỉ hiện khi `RECEIVED`
- Reconcile session chỉ hiện khi `CLOSED`
- Mark payroll paid chỉ hiện khi `APPROVED`

## 17.3 Field-level masking
Các field nhạy cảm phải hỗ trợ:
- hidden
- masked
- readonly

Áp dụng cho:
- employee sensitive data
- supplier bank details
- payroll detail
- some audit fields

---

# 18. API integration plan

## 18.1 Quy tắc chung
- chỉ dùng gateway client
- không gọi `internal/*`
- chuẩn hóa error mapping theo `code`
- chuẩn hóa query keys
- side-effect actions phải đi qua helper tạo:
  - `X-Correlation-Id`
  - `Idempotency-Key`

## 18.2 Async job handling
Áp dụng cho reports/export:
- create export job
- poll status
- preview
- download
- handle `QUEUED/RUNNING/COMPLETED/FAILED`

## 18.3 Retry/idempotency
Áp dụng cho:
- payment
- inventory post actions
- GR post
- supplier payment
- attendance event
- export create

---

# 19. Frontend implementation plan theo sprint

## Sprint 0 — Bootstrap
### Deliver
- App shell
- Router
- Providers
- Auth flow
- Permission/scope core
- Navigation builder
- Error boundaries
- Design-system foundation

### Done when
- login/logout được
- app shell chạy được
- route guard hoạt động
- navigation render theo role/scope

---

## Sprint 1 — POS core
### Deliver
- POS Home
- Orders List
- Order Detail / Cart
- Payment
- POS basic state + policy
- POS resilience layer (network + retry baseline)

### Done when
- Staff có thể tạo order, thêm payment, complete flow cơ bản
- terminal states readonly đúng
- payment idempotency flow chuẩn

---

## Sprint 2 — Session + Dashboard
### Deliver
- Session Control
- Session Reconciliation
- Outlet Dashboard
- Pending tasks widgets

### Done when
- Outlet Manager mở/đóng/reconcile session được
- Dashboard hiển thị quick actions + pending tasks

---

## Sprint 3 — Procurement
### Deliver
- PO List/Create/Detail
- GR List/Create/Detail
- lifecycle badges/timeline
- procurement UI policies

### Done when
- tạo/submit/issue PO được
- tạo/receive/post GR được
- readonly đúng theo state

---

## Sprint 4 — Inventory
### Deliver
- Stock Overview
- Inventory Transactions
- Waste
- Adjustment
- Stock Count List/Entry/Review

### Done when
- inventory actions usable
- post actions có confirm + idempotency
- stock count flow end-to-end chạy được

---

## Sprint 5 — Workforce
### Deliver
- My Attendance
- Shift Templates
- Shift Assignments
- Attendance Review
- Attendance Detail

### Done when
- self-attendance chạy được
- manager review chạy được
- event progression logic phản ánh đúng UI

---

## Sprint 6 — HR
### Deliver
- Employees
- Employee Detail
- Contracts
- Attendance Summary
- Payroll Preparation
- Payroll Draft Review

### Done when
- HR có thể prepare payroll draft
- exception panel usable
- forms sectioned rõ

---

## Sprint 7 — Finance + Reports
### Deliver
- Suppliers
- Payroll Approval
- Payroll Paid
- Reports Dashboard
- Export Jobs / Preview

### Done when
- final payroll approval/pay flow usable
- export async UX usable

---

## Sprint 8 — Governance
### Deliver
- IAM
- Org
- Audit
- Effective Access
- basic Regional Ops / Catalog reads if needed

### Done when
- admin/governance flows usable
- audit/read visibility rõ
- effective access page giải thích được access

---

# 20. QA & test plan theo layer

## Unit tests
- UiPolicy services
- Workflow services
- Permission checkers
- Scope resolvers
- Mappers
- Form schemas

## Integration tests
- route guards
- auth/session flow
- POS payment flow
- PO/GR lifecycle transitions
- stock count transitions
- attendance approval flow
- export polling flow

## E2E priority flows
1. Staff login → POS → payment
2. Outlet Manager open session → reconcile
3. OM create PO → submit → issue
4. OM create GR → receive → post
5. OM stock count → post
6. Staff attendance → OM attendance review
7. HR prepare payroll
8. Finance approve payroll → mark paid
9. Admin view effective access

---

# 21. Definition of Done theo screen

Một screen chỉ được coi là xong khi đủ:

- route hoạt động
- loading/empty/error/success states đầy đủ
- permission + scope behavior đúng
- lifecycle actions đúng
- form validation đúng
- responsive ở breakpoint mục tiêu
- design-system components dùng đúng
- unit/integration tests tối thiểu có
- QA checklist pass

---

# 22. Checklist bắt đầu implementation ngay

## Product / UX
- [ ] chốt screen inventory
- [ ] chốt route map
- [ ] chốt wireframe wave 1
- [ ] chốt screen spec template

## UI
- [ ] dựng tokens
- [ ] dựng atomic components
- [ ] dựng table wrapper
- [ ] dựng approval panel
- [ ] dựng async job progress

## Frontend
- [ ] app shell
- [ ] gateway client
- [ ] auth core
- [ ] permission/scope core
- [ ] navigation builder
- [ ] error boundaries

## Domain
- [ ] pos module skeleton
- [ ] procurement module skeleton
- [ ] inventory module skeleton
- [ ] workforce module skeleton

---

# 23. Phụ lục — Mẫu phân rã công việc cho một screen

Ví dụ `Purchase Order Create`

## UX
- chọn layout: Form Layout
- low-fi wireframe
- field list
- action list
- validation + readonly rules
- error states

## Frontend
- route page
- form schema
- form mapper
- create mutation
- ui policy
- line item editor component
- sticky footer actions
- tests

## QA
- create draft
- submit
- validation errors
- permission denied
- wrong outlet scope
- readonly after submit

---

# 24. Kết luận

Nếu bám đúng kế hoạch này, team có thể triển khai theo hướng:
- **khóa UX trước**
- **code shell/core trước**
- **build Phase 1 theo dòng giá trị outlet**
- **mở rộng sang HR/Finance/Reports**
- **kết thúc bằng governance**

Đây là cách an toàn và thực dụng nhất để implement toàn bộ dự án mà vẫn kiểm soát được chất lượng, state complexity và permission/scope behavior.
