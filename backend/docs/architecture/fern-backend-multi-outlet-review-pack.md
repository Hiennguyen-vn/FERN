# FERN Backend Multi-Outlet Review Pack

## 1. Mục tiêu
Tài liệu này mô tả flow backend hiện tại của FERN theo mô hình chuỗi F&B multi-outlet, đồng thời chốt 5 lỗi hoặc rủi ro lớn nhất đã được ưu tiên xử lý trong codebase.

Phạm vi:
- `org-service`
- `catalog-service`
- `pos-service`
- `inventory-service`
- `procurement-service`
- `finance-service`
- `hr-service`
- `report-service`

## 2. Flow Map Theo Service

### 2.1 `org-service`
- Là nguồn sự thật cho `region`, `outlet`, route, status outlet, currency và timezone theo region.
- Cấp route validation cho downstream service qua API đọc nội bộ và mở rộng scope cho IAM.
- Giữ lifecycle của outlet.
- Trước khi đóng outlet, service gọi close-check sang `pos-service`, `inventory-service`, `procurement-service`, `finance-service`.

### 2.2 `catalog-service`
- Resolve menu theo outlet và business date.
- Resolve giá theo scope `OUTLET -> REGION -> COUNTRY -> GLOBAL`.
- Resolve tax theo hiệu lực thời gian.
- Resolve recipe version theo `productId + businessDate`.
- `pos-service` phụ thuộc đồng bộ vào catalog để snapshot menu, price, tax, recipe trước khi hoàn tất sale.

### 2.3 `pos-service`
- Mở phiên POS theo `regionId + outletId + terminalId`.
- Khi mở session:
  - validate outlet route từ `org-service`
  - derive currency từ region
  - chặn mismatch giữa currency request và region currency
- Tạo sale order, snapshot menu và payment.
- Trước khi complete order:
  - resolve recipe từ `catalog-service`
  - reserve stock qua `inventory-service`
  - ghi outbox `pos.sale.completed`

### 2.4 `inventory-service`
- Là nguồn sự thật cho stock operational theo outlet.
- `reserveSale()` giữ `qty_reserved` dựa trên recipe usage từ POS.
- `commitSaleCompletion()` commit sale theo `stock_reservation` và `stock_reservation_line`, không còn tin recipe payload của event làm source cuối.
- Consume `procurement.goods_receipt.posted` để tăng tồn.
- Quản lý stock adjustment, waste, stock count.
- Cung cấp internal close-check để `org-service` chặn đóng outlet khi còn reservation hoặc stock count chưa hoàn tất.

### 2.5 `procurement-service`
- Quản lý `purchase_order -> goods_receipt -> supplier_invoice -> supplier_payment`.
- `goods_receipt` chỉ hợp lệ khi line khớp đúng `purchase_order_line`.
- Khi post goods receipt:
  - validate không over-receipt
  - update `qty_received` trên PO line
  - publish `procurement.goods_receipt.posted`

### 2.6 `finance-service`
- Nhận attendance/contracts từ HR để tính payroll.
- `PayrollCalculationEngine` chọn contract theo business date.
- `PayrollAllocationService` phân bổ `netPay` theo outlet dựa trên work hours.
- Cung cấp internal close-check cho outlet closure.

### 2.7 `hr-service`
- Nguồn dữ liệu attendance, employee, contract.
- Attendance đã approved là input chính cho payroll.

### 2.8 `report-service`
- Không phải source of truth giao dịch.
- Consume cross-service events để build fact tables và daily summary.
- Các event inventory/procurement/finance/POS chủ yếu là projection trigger cho reporting.

## 3. Flow Map Theo Event

### 3.1 `pos.sale.completed`
- Producer: `pos-service`
- Consumers:
  - `inventory-service`
  - `report-service`
- Vai trò:
  - inventory commit stock usage
  - sales/report projection
- Quy tắc:
  - event phải mang `reservationId`
  - inventory lấy reservation state làm source cuối
  - event payload recipe chỉ còn là dữ liệu phụ trợ, không quyết định lượng trừ kho

### 3.2 `procurement.goods_receipt.posted`
- Producer: `procurement-service`
- Consumers:
  - `inventory-service`
  - `finance-service`
  - `report-service`
- Vai trò:
  - tăng stock
  - ghi nhận cost/procurement expense
  - projection reporting

### 3.3 `attendance.approved`
- Producer: `hr-service`
- Consumer chính: `finance-service`
- Vai trò:
  - input tính payroll theo employee, outlet, business date

### 3.4 `payroll.calculated` và `payroll.posted`
- Producer: `finance-service`
- Consumer: `report-service`
- Vai trò:
  - payroll cost projection theo outlet, region, company

### 3.5 `finance.expense.posted`
- Producer: `finance-service`
- Consumer: `report-service`
- Vai trò:
  - expense reporting

### 3.6 `inventory.adjustment.posted`, `inventory.waste.posted`, `inventory.stock_count.posted`
- Producer: `inventory-service`
- Consumer: `report-service`
- Vai trò:
  - tồn kho, shrinkage, correction reporting

## 4. Cross-Service Rules

### 4.1 Source of truth
- `org-service` là source of truth cho route, currency, timezone, lifecycle outlet.
- `inventory-service` là source of truth cho stock operational.
- `procurement-service` là source of truth cho PO/GR/invoice/payment.
- `finance-service` là source of truth cho payroll run và payroll posting.
- `report-service` chỉ là projection layer.

### 4.2 Hard synchronous dependency
- `pos-service -> org-service` khi mở session.
- `pos-service -> catalog-service` khi resolve menu và recipe.
- `pos-service -> inventory-service` khi reserve stock.
- `org-service -> pos/inventory/procurement/finance` khi close outlet.

### 4.3 Idempotency và replay
- `inventory-service` dùng inbox cho event ingest.
- `report-service` dùng landing + projector pattern.
- `finance-service` dùng `integration_event` làm landing/idempotency guard cho procurement events.
- `procurement-service` và `pos-service` dùng outbox cho event publish.
- `inventory-service`, `finance-service`, `report-service` đã được harden để malformed payload và semantic poison để lại bản ghi `FAILED` thay vì biến mất khỏi observability.

## 5. Top 5 Lỗi/Rủi Ro Lớn Nhất

### 5.1 Inventory commit sale tin payload event hơn reservation state
**Hiện trạng cũ**
- `commitSaleCompletion()` rebuild usage từ `recipeUsageItems` trong `pos.sale.completed`.
- Nếu payload drift so với reservation đã giữ, stock commit có thể sai hoặc làm lệch outlet balance.

**Tác động trong chuỗi F&B**
- Sai usage theo outlet.
- Replay event xấu có thể trừ kho vượt reservation.

**Patch đã chốt**
- `inventory-service` commit sale theo `stock_reservation_line`.
- Verify:
  - `reservation.outletId == event.outletId`
  - `reservation.sourceOrderId == event.saleOrderId`
- Guard:
  - `qty_on_hand >= qty`
  - `qty_reserved >= qty`

**Test**
- payload recipe lệch reservation vẫn commit đúng theo reservation
- mismatch source order bị reject
- commit không được làm reserved âm

### 5.2 Procurement chưa chặn over-receipt và line mismatch với PO line
**Hiện trạng cũ**
- GR line có thể chỉ tới sai `purchase_order_line`.
- `qty_received` được cộng dồn mà không chặn vượt `qty_ordered`.

**Tác động trong chuỗi F&B**
- sai tồn kho nhập
- sai công nợ supplier
- sai cost đi downstream

**Patch đã chốt**
- validate line GR khi tạo:
  - `purchaseOrderLineId` bắt buộc có
  - line phải thuộc đúng PO
  - `ingredientId`, `uomCode` phải khớp PO line
- validate lại khi post:
  - aggregate qty theo PO line
  - chặn `existing + current > qtyOrdered`

**Test**
- reject GR line sai ingredient
- reject post khi over-receipt

### 5.3 Payroll có thể chọn sai contract effective và lệch tổng allocation
**Hiện trạng cũ**
- `selectContract()` fallback sang contract mới nhất nếu không tìm thấy contract effective.
- allocation theo outlet round từng phần độc lập, có thể lệch tổng `netPay`.

**Tác động trong chuỗi F&B**
- tính lương sai cho attendance lịch sử
- P&L outlet sai do tổng allocation không khớp net pay

**Patch đã chốt**
- `selectContract()` fail explicit nếu không có contract effective tại `businessDate`
- residual rounding được dồn vào outlet cuối để tổng allocation bằng đúng `netPay`

**Test**
- reject future contract cho ngày công quá khứ
- allocation 3 outlet vẫn sum đúng `netPay`

### 5.4 Outlet close guard chưa khóa inventory state
**Hiện trạng cũ**
- `org-service` chỉ check `POS`, `procurement`, `finance`.
- Không check reservation và stock count của inventory.

**Tác động trong chuỗi F&B**
- outlet bị đóng khi còn stock workflow dang dở
- state mồ côi sau close

**Patch đã chốt**
- thêm internal close-check:
  - `org-service -> inventory-service`
- inventory trả:
  - `blockingReservations`
  - `blockingStockCountSessions`
  - `hasBlockingOperations`
- `org-service` reject close nếu inventory còn open workflow

**Test**
- internal inventory close-check trả đúng count
- org close outlet fail khi inventory còn blocking state

### 5.5 POS session đang lấy currency từ request thay vì derive từ org route
**Hiện trạng cũ**
- session insert dùng `request.currencyCode()`.

**Tác động trong chuỗi F&B**
- sai currency theo region
- sai cash reconciliation
- sai reporting downstream

**Patch đã chốt**
- `pos-service` gọi `org-service` lấy `RegionRoute`
- session currency được derive từ region
- nếu request currency mismatch với region currency thì reject

**Test**
- mở session với currency sai so với region bị reject

## 6. Deferred Risks
- `catalog-service` chưa support recipe override theo `region/outlet`
- một số consumer ngoài `inventory-service`, `finance-service`, `report-service` vẫn cần tiếp tục review theo cùng pattern hardening
- inter-outlet transfer chưa là flow hoàn chỉnh trong operational inventory

## 7. Trạng Thái Sau Đợt Sửa Này
- 5 fix ưu tiên đã được implement trong codebase.
- đợt rà soát tiếp theo đã vá thêm:
  - poison-message handling cho `inventory-service` và `finance-service`
  - chặn commit sale trên reservation đã `CANCELLED`
  - dùng `reservation.businessDate` làm source cuối cho `SALE_USAGE`
- Regression tests đã được thêm cho:
  - inventory
  - procurement
  - finance
  - org
  - pos
- `report-service` không bị thay đổi thêm trong đợt này ngoài các fix đã thực hiện trước đó.
