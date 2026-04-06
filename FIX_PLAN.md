# FERN Backend — Fix Plan

> Generated: 2026-04-06  
> Branch: version2  
> Scope: Tất cả các lỗi tiềm ẩn được phát hiện qua code review toàn hệ thống F&B ERP

---

## Tóm tắt mức độ ưu tiên

| # | Vấn đề | Mức độ | Service | Trạng thái |
|---|---|---|---|---|
| 1 | Flyway V8 version conflict | CRITICAL | pos-service | ✅ Done |
| 2 | `tableName` null trong dine-in SaleOrderResponse | HIGH | pos-service | ✅ Done |
| 3 | Sync HTTP call trong saga `completeOrder` | HIGH | pos-service / inventory-service | ✅ Done |
| 4 | `session_code` UNIQUE thiếu partial index | MEDIUM | pos-service | ✅ Done |
| 5 | Service JWT dùng chung secret với user JWT | MEDIUM | platform-security / api-gateway | ✅ Done |
| 6 | Kafka DLQ chưa có trong report-service | MEDIUM | report-service | ✅ Already done (KafkaErrorHandlerConfig tồn tại) |
| 7 | Payroll run không có idempotency guard | MEDIUM | finance-service | ✅ Done |
| 8 | BUG-008 comment trong production code | LOW | inventory-service | ✅ Done |
| 9 | Shard routing double-query fragile pattern | MEDIUM | pos-service | ✅ Done |

---

## Chi tiết từng fix

---

### Fix #1 — Flyway V8 Version Conflict (CRITICAL)

**Vị trí:**
- `backend/services/pos-service/src/main/resources/db/migration/postgresql/operational/V8__outbox_purge_published_index.sql`

**Vấn đề:**  
Tồn tại 2 file cùng version `V8`:
- `V8__customer_and_loyalty.sql` — migration thêm bảng customer, loyalty
- `V8__outbox_purge_published_index.sql` — migration thêm index cho outbox

Flyway sẽ fail checksum validation khi khởi động, dẫn đến application không start được.

**Fix:**  
Đổi tên `V8__outbox_purge_published_index.sql` → `V11__outbox_purge_published_index.sql`  
(V9 = dine_in_and_promotion, V10 = audit_composite_indices, V11 là slot trống tiếp theo)

**Verification:**  
- `mvn flyway:info` không báo checksum conflict
- Application khởi động thành công

---

### Fix #2 — `tableName` null trong Dine-In Response (HIGH)

**Vị trí:**
- `backend/services/pos-service/src/main/java/com/fern/posservice/service/PosStore.java` — method `mapOrder()`
- `backend/services/pos-service/src/main/java/com/fern/posservice/service/PosOrderService.java` — method `getOrder()`, `listOrdersBySession()`

**Vấn đề:**  
`PosStore.mapOrder()` luôn set `tableName = null` với comment "resolved by caller if needed".  
Nhưng các caller như `getOrder()` và `listOrdersBySession()` không thực hiện resolve.  
Kết quả: API trả về `tableName: null` cho tất cả DINE_IN orders.

**Fix:**  
- Trong `PosStore.mapOrder(NamedParameterJdbcTemplate, OrderRecord, CustomerSummaryResponse)`:
  Tự động resolve `tableName` từ `order.tableId()` bằng cách gọi `PosDineInService.resolveTableName()` nếu `tableId != null`
- Inject `PosDineInService` vào `PosStore` hoặc truyền `tableName` qua method parameter
- Đơn giản hơn: thêm overload `mapOrder()` nhận thêm `String tableName` và sửa tất cả callers

**Verification:**
- GET `/sale-orders/{id}` với DINE_IN order trả về `tableName` đúng
- GET `/sale-orders/{id}` với TAKEAWAY order trả về `tableName: null` (đúng)

---

### Fix #3 — Sync HTTP Call trong Saga `completeOrder` (HIGH)

**Vị trí:**
- `backend/services/pos-service/src/main/java/com/fern/posservice/service/PosOrderService.java` — method `completeOrder()` dòng ~417

**Vấn đề:**  
Luồng complete order thực hiện blocking HTTP call đến inventory-service (`inventoryClient.reserveInventory()`) trước khi commit DB transaction. Nếu inventory-service chậm hoặc circuit breaker mở:
- Order bị stuck ở `COMPLETING` status
- Nếu reservation thành công nhưng POS DB commit fail → inventory bị lock đến hết TTL
- Timeout của HTTP call ảnh hưởng trực tiếp đến cashier UX

**Fix:**  
Giảm thiểu rủi ro (không refactor toàn bộ sang event-driven trong 1 sprint):
1. Đặt explicit timeout ngắn cho `PosInventoryClient` (≤ 2 giây thay vì default)
2. Thêm `@Value` config cho inventory reservation timeout
3. Thêm log rõ ràng khi reservation timeout để dễ debug
4. Document rõ trong code về invariant: "nếu reservation thành công nhưng commit fail → recovery job sẽ release reservation sau TTL"

**Verification:**
- Inventory client có timeout ≤ 2 giây
- Circuit breaker config được kiểm tra đúng

---

### Fix #4 — `session_code` UNIQUE Constraint Thiếu Partial Index (MEDIUM)

**Vị trí:**
- `backend/services/pos-service/src/main/resources/db/migration/postgresql/operational/V1__init_pos_schema.sql` — bảng `pos_session`

**Vấn đề:**  
```sql
session_code VARCHAR(100) NOT NULL UNIQUE  -- toàn bộ table
```
Khi outlet đóng/mở lại hoặc terminal được reset và cần dùng lại code convention cũ (ví dụ: `OUTLET-001-20240101`), sẽ bị unique constraint violation.

Catalog dùng partial index đúng cách:
```sql
UNIQUE (product_code) WHERE deleted_at IS NULL
```

**Fix:**  
Thêm migration mới `V12__session_code_partial_unique.sql`:
1. Drop constraint `session_code UNIQUE`
2. Thêm partial unique index: `UNIQUE (outlet_id, session_code) WHERE status = 'OPEN'`
3. Giữ non-unique index trên `session_code` để lookup vẫn nhanh

**Lý do chọn `(outlet_id, session_code) WHERE status = 'OPEN'`:**
- Chỉ cần session_code unique trong cùng outlet và đang OPEN
- Cho phép reuse code sau khi session đã CLOSED/RECONCILED

**Verification:**
- Không thể mở 2 sessions cùng outlet cùng code khi cả 2 OPEN
- Có thể reopen outlet với session_code từng dùng trước đó

---

### Fix #5 — Service JWT Dùng Chung Secret Với User JWT (MEDIUM)

**Vị trí:**
- Platform security — `FernServiceTokenSupport`, `FernJwtKeyMaterial`

**Vấn đề:**  
Service-to-service tokens được ký bằng cùng key material với user tokens. Nếu secret bị lộ, attacker có thể tạo service token với bất kỳ permission nào, vượt qua mọi service-level check.

**Fix:**  
Thêm property `fern.jwt.service-secret` riêng biệt:
1. Nếu `fern.jwt.service-secret` được set → dùng key đó để ký/verify service tokens
2. Fallback về `fern.jwt.secret` nếu không set (backward compatible)
3. `FernJwtService.validateToken()` check `token_type` claim trước khi dùng key tương ứng
4. Thêm validation: user token không được có `token_type: service`, service token không được có `token_type: user`

**Verification:**
- Service token ký bằng `service-secret` bị reject khi validate bằng `jwt-secret` và ngược lại
- Existing user tokens vẫn hoạt động bình thường

---

### Fix #6 — Kafka DLQ Chưa Có Trong report-service (MEDIUM)

**Vị trí:**
- `backend/services/report-service/src/main/java/` — Kafka consumer config

**Vấn đề:**  
`inventory-service` đã có `KafkaErrorHandlerConfig` với DLQ đầy đủ (retry 3 lần → DLQ topic).  
`report-service` consume 11+ topics nhưng chưa có DLQ config tương tự.  
Nếu một message lỗi (malformed JSON, DB constraint fail), consumer có thể bị stuck hoặc skip message tùy `auto-offset-reset`.

**Fix:**  
Tạo `KafkaErrorHandlerConfig.java` trong report-service giống hệt inventory-service:
- Retry 3 lần, interval 1 giây
- Sau khi hết retry → publish vào `<topic>.DLQ`
- Non-retryable: `JsonProcessingException`, `IllegalArgumentException`

**Verification:**
- Malformed message không block consumer group
- Message bị lỗi xuất hiện trong `<topic>.DLQ`

---

### Fix #7 — Payroll Run Không Có Idempotency Guard (MEDIUM)

**Vị trí:**
- `backend/services/finance-service/src/main/java/com/fern/financeservice/service/PayrollRunOrchestrator.java` — method `createPayrollRun()`

**Vấn đề:**  
Nếu `createPayrollRun()` được gọi 2 lần với cùng `payrollPeriodId` (double-click, network retry):
- Có thể tạo 2 `payroll_run` records cho cùng 1 period ở trạng thái DRAFT
- `recalculateRun()` gọi `deleteExistingRunArtifacts()` nhưng chỉ xóa artifacts của run cụ thể, không ngăn tạo run trùng

**Fix:**  
Thêm uniqueness check trước khi insert:
```sql
-- Chỉ cho phép 1 DRAFT/SUBMITTED run per period
SELECT COUNT(*) FROM finance.payroll_run 
WHERE payroll_period_id = :periodId AND status IN ('DRAFT', 'SUBMITTED', 'APPROVED')
```
Nếu đã tồn tại → throw `ConflictException("A payroll run already exists for this period")`

**Verification:**
- Gọi `createPayrollRun()` 2 lần với cùng period → lần 2 nhận 409 Conflict
- Run đã PAID/REJECTED cho phép tạo run mới

---

### Fix #8 — BUG-008 Comment Trong Production Code (LOW)

**Vị trí:**
- `backend/services/inventory-service/src/main/java/com/fern/inventoryservice/service/StockReservationService.java` — method `lockReservationBalances()` dòng ~434

**Vấn đề:**  
Comment `// BUG-008: Ensure balance row exists before locking` để lại ambiguity về trạng thái của fix. Không rõ "BUG-008" đã được đóng chưa hay vẫn còn open.

**Fix:**  
Thay comment thành mô tả business logic rõ ràng:
```java
// Upsert balance row to handle ingredients never stocked at this outlet.
// This prevents a "row not found" deadlock when acquiring a pessimistic lock
// on an ingredient that has no existing stock record.
```

**Verification:**
- Không còn reference đến "BUG-008" trong codebase

---

### Fix #9 — Shard Routing Double-Query Fragile Pattern (MEDIUM)

**Vị trí:**
- `backend/services/pos-service/src/main/java/com/fern/posservice/service/PosOrderService.java`  
  Methods: `createOrder()`, `getOrder()`, `updateOrder()`, `addPayment()`, `completeOrder()`

**Vấn đề:**  
Pattern hiện tại:
```java
// 1. Query root template để lấy regionId/outletId
OrderRecord order = store.requireOrder(rootJdbcTemplate(), id);
// 2. Resolve shard từ regionId/outletId
NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(order.regionId(), order.outletId());
// 3. Query lại trên shard đúng
OrderRecord currentOrder = store.requireOrderForUpdate(jdbcTemplate, id);
```

Với single-shard hiện tại: query #1 và #3 hit cùng DB → wasteful nhưng đúng.  
Khi có multi-shard: data có thể stale giữa query #1 và #3 trong window ngắn.

**Fix:**  
Cải thiện method `requireOrder()` trong `PosStore` để cache `regionId`/`outletId` trong OrderRecord và sử dụng thẳng từ đó mà không cần query lại root lần 2. Cụ thể:
- `PosStore.requireOrderShardKey(rootJdbc, id)` → chỉ SELECT `id, region_id, outlet_id` (lightweight)
- Tách biệt "shard resolution query" (chỉ lấy key) vs "full entity query" (lấy toàn bộ fields trên shard đúng)
- Document rõ invariant: shard key (regionId/outletId) không thay đổi sau khi order được tạo

**Verification:**
- Số lượng queries giảm 1 per operation
- Behavior không thay đổi trong single-shard mode
