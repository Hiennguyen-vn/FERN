-- =============================================================================
-- FERN IAM: Cấp lại toàn bộ quyền theo SRS (Mục 1.9, 3.12, Permission Matrix)
-- =============================================================================
-- Role IDs:
--   2 = staff
--   3 = outlet_manager
--   4 = regional_finance
--   5 = finance
--   6 = product_manager
--   7 = system_admin
--   8 = hr
-- =============================================================================

BEGIN;

-- Xóa toàn bộ quyền hiện tại của các role nghiệp vụ (giữ lại bootstrap_admin role 1)
DELETE FROM iam.role_permission WHERE role_id IN (2, 3, 4, 5, 6, 7, 8);

-- -----------------------------------------------------------------------------
-- ROLE 2: staff
-- Scope: outlet_set
-- Quyền: Xem menu POS, tạo/cập nhật/hủy/hoàn tất order, xem session, mở session
-- catalog.product.read, catalog.price.read cần để load menu POS
-- -----------------------------------------------------------------------------
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 2, p.id, NOW() FROM iam.permission p WHERE p.code IN (
    'catalog.product.read',   -- xem danh sách sản phẩm/menu POS
    'catalog.price.read',      -- xem giá bán
    'pos.session.read',        -- xem phiên POS
    'pos.session.open',        -- mở phiên POS (staff cũng có thể mở trong V1)
    'pos.order.read',          -- xem order
    'pos.order.create',        -- tạo order
    'pos.order.update',        -- cập nhật order line
    'pos.order.cancel',        -- hủy order trước khi thanh toán
    'pos.order.complete'       -- hoàn tất thanh toán
) ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- ROLE 3: outlet_manager
-- Scope: outlet_set hoặc region_subtree
-- Quyền: Toàn bộ POS + quản lý session + kho + thu mua + HR tại outlet
-- -----------------------------------------------------------------------------
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 3, p.id, NOW() FROM iam.permission p WHERE p.code IN (
    -- Catalog (đọc menu để dùng POS + tham chiếu supplier)
    'catalog.product.read',
    'catalog.price.read',
    'catalog.ingredient.read',
    -- POS - full session + order management
    'pos.session.read',
    'pos.session.open',
    'pos.session.close',
    'pos.session.reconcile',
    'pos.order.read',
    'pos.order.create',
    'pos.order.update',
    'pos.order.cancel',
    'pos.order.complete',
    -- Inventory (kho tại outlet)
    'inventory.balance.read',
    'inventory.ledger.read',
    'inventory.adjustment.write',
    'inventory.waste.write',
    'inventory.stock_count.write',
    'inventory.stock_count.post',
    -- Procurement (thu mua tại outlet)
    'procurement.supplier.read',   -- chỉ xem supplier đã approved
    'procurement.po.read',
    'procurement.po.create',
    'procurement.po.update',
    'procurement.po.submit',
    'procurement.po.issue',
    'procurement.po.cancel',
    'procurement.gr.read',
    'procurement.gr.create',
    'procurement.gr.post',
    'procurement.gr.cancel',
    -- HR tại outlet (phân ca + duyệt chấm công)
    'hr.shift.read',
    'hr.shift.write',
    'hr.attendance.write',
    'hr.attendance.review',
    -- Org (xem thông tin outlet/region của mình)
    'org.region.read',
    'org.outlet.read'
) ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- ROLE 4: regional_finance
-- Scope: region_subtree
-- Quyền: Rà soát + phê duyệt tài chính thu mua trong vùng, xem báo cáo vùng
-- -----------------------------------------------------------------------------
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 4, p.id, NOW() FROM iam.permission p WHERE p.code IN (
    -- Org (xem region/outlet trong vùng)
    'org.region.read',
    'org.outlet.read',
    -- Supplier (xem toàn bộ supplier master để rà soát nghiệp vụ)
    'procurement.supplier.read',
    -- PO: xem + phê duyệt
    'procurement.po.read',
    'procurement.po.approve',
    -- GR: xem
    'procurement.gr.read',
    -- Invoice: xem + rà soát + phê duyệt + tranh chấp
    'procurement.invoice.read',
    'procurement.invoice.review',
    'procurement.invoice.approve',
    'procurement.invoice.dispute',
    -- Payment: chỉ xem (không ghi nhận)
    'procurement.payment.read',
    -- Inventory: xem (giám sát)
    'inventory.balance.read',
    'inventory.ledger.read',
    -- Payroll: xem (phạm vi vùng)
    'finance.payroll.read',
    -- Report: xem + export
    'report.read',
    'report.export',
    'report.payroll.read',
    'report.payroll.export'
) ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- ROLE 5: finance
-- Scope: system
-- Quyền: Toàn bộ nghiệp vụ tài chính doanh nghiệp: supplier master, payment,
--         payroll approve/pay, finance config, report toàn hệ thống
-- -----------------------------------------------------------------------------
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 5, p.id, NOW() FROM iam.permission p WHERE p.code IN (
    -- Org
    'org.region.read',
    'org.outlet.read',
    -- Supplier master (tạo mới + cập nhật + kích hoạt)
    'procurement.supplier.read',
    'procurement.supplier.write',
    -- PO (xem + phê duyệt toàn hệ thống)
    'procurement.po.read',
    'procurement.po.approve',
    -- GR + Invoice + Payment (toàn hệ thống)
    'procurement.gr.read',
    'procurement.invoice.read',
    'procurement.invoice.review',
    'procurement.invoice.approve',
    'procurement.invoice.dispute',
    'procurement.payment.read',
    'procurement.payment.record',    -- ghi nhận chi trả NCC
    -- Payroll (toàn hệ thống: xem + phê duyệt + ghi nhận chi trả)
    'finance.payroll.read',
    'finance.payroll.detail.read',
    'finance.payroll.prepare',
    'finance.payroll.approve',
    'finance.payroll.pay',
    -- Finance config
    'finance.config.read',
    'finance.config.write',
    -- Report toàn hệ thống
    'report.read',
    'report.export',
    'report.payroll.read',
    'report.payroll.export',
    -- HR readonly (cần xem hồ sơ NV để tính lương)
    'hr.employee.read',
    'hr.contract.read',
    'hr.contract.detail.read',
    'hr.internal.read'
) ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- ROLE 6: product_manager
-- Scope: system
-- Quyền: Toàn bộ catalog (nguyên liệu, sản phẩm, công thức, giá, thuế, availability)
-- -----------------------------------------------------------------------------
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 6, p.id, NOW() FROM iam.permission p WHERE p.code IN (
    -- Org (đọc outlet/region để cấu hình availability)
    'org.region.read',
    'org.outlet.read',
    -- Catalog - full read/write
    'catalog.ingredient.read',
    'catalog.ingredient.write',
    'catalog.product.read',
    'catalog.product.write',
    'catalog.recipe.read',
    'catalog.recipe.write',
    'catalog.price.read',
    'catalog.price.write',
    'catalog.internal.resolve',
    -- Supplier: chỉ xem (tham chiếu supplier đã approved)
    'procurement.supplier.read',
    -- Inventory: xem balance (kiểm tra tồn)
    'inventory.balance.read'
) ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- ROLE 7: system_admin
-- Scope: system
-- Quyền: Toàn bộ IAM (users, roles, permissions, scopes) + Org + Audit
-- -----------------------------------------------------------------------------
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 7, p.id, NOW() FROM iam.permission p WHERE p.code IN (
    -- IAM - full management
    'iam.user.read',
    'iam.user.write',
    'iam.role.read',
    'iam.role.write',
    'iam.role.assign',
    'iam.scope.assign',
    'iam.permission.read',
    'iam.permission_override.read',
    'iam.permission_override.write',
    -- Org - full management
    'org.region.read',
    'org.region.write',
    'org.outlet.read',
    'org.outlet.write',
    'org.scope.resolve',
    -- Audit - full read + export
    'audit.read',
    'audit.detail.read',
    'audit.export'
) ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- ROLE 8: hr
-- Scope: system (thường), region scope cho phân bổ
-- Quyền: Quản lý hồ sơ NV, hợp đồng, chuẩn bị payroll
-- -----------------------------------------------------------------------------
INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 8, p.id, NOW() FROM iam.permission p WHERE p.code IN (
    -- Org
    'org.region.read',
    'org.outlet.read',
    -- HR - full employee + contract management
    'hr.employee.read',
    'hr.employee.write',
    'hr.contract.read',
    'hr.contract.write',
    'hr.contract.detail.read',       -- field-level: xem lương/thuế trong hợp đồng
    'hr.shift.read',
    'hr.shift.write',
    'hr.attendance.write',
    'hr.attendance.review',
    'hr.payroll.prepare',
    'hr.internal.read',
    -- Finance payroll (chuẩn bị kỳ lương)
    'finance.payroll.read',
    'finance.payroll.prepare',
    'finance.payroll.detail.read',
    -- Report
    'report.payroll.read',
    'report.payroll.export'
) ON CONFLICT DO NOTHING;

-- =============================================================================
-- Cập nhật scope cho các tài khoản demo theo đúng SRS
-- =============================================================================

-- Xóa toàn bộ scope assignment hiện tại của demo accounts
DELETE FROM iam.user_scope_assignment
WHERE user_id IN (
    SELECT id FROM iam.user_account WHERE username LIKE 'demo-%'
);

-- Lấy region DEMO-HCM và outlet IDs
DO $$
DECLARE
    v_region_id  INT;
    v_outlet_d1  INT;
    v_outlet_d3  INT;
    v_user_id    INT;
BEGIN
    SELECT id INTO v_region_id FROM org.region WHERE code = 'DEMO-HCM' LIMIT 1;
    SELECT id INTO v_outlet_d1 FROM org.outlet WHERE code = 'DEMO-HCM-DIST1' LIMIT 1;
    SELECT id INTO v_outlet_d3 FROM org.outlet WHERE code = 'DEMO-HCM-DIST3' LIMIT 1;

    -- demo-cashier: Staff, scope = outlet DIST1 only
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-cashier';
    IF v_user_id IS NOT NULL AND v_outlet_d1 IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, FALSE, '{}', ARRAY[v_outlet_d1], NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET outlet_ids = ARRAY[v_outlet_d1], system_scope = FALSE, updated_at = NOW();
    END IF;

    -- demo-outlet-mgr: Outlet Manager, scope = outlet DIST1 + region DEMO-HCM
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-outlet-mgr';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, FALSE, ARRAY[v_region_id], ARRAY[v_outlet_d1], NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET region_ids = ARRAY[v_region_id], outlet_ids = ARRAY[v_outlet_d1], system_scope = FALSE, updated_at = NOW();
    END IF;

    -- demo-region-mgr: Region Manager (outlet_manager + regional_finance), scope = region DEMO-HCM
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-region-mgr';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, FALSE, ARRAY[v_region_id], '{}', NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET region_ids = ARRAY[v_region_id], outlet_ids = '{}', system_scope = FALSE, updated_at = NOW();
    END IF;

    -- demo-reg-finance: Regional Finance, scope = region DEMO-HCM
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-reg-finance';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, FALSE, ARRAY[v_region_id], '{}', NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET region_ids = ARRAY[v_region_id], outlet_ids = '{}', system_scope = FALSE, updated_at = NOW();
    END IF;

    -- demo-hr: HR, system scope
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-hr';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, TRUE, '{}', '{}', NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET system_scope = TRUE, updated_at = NOW();
    END IF;

    -- demo-finance: Finance, system scope
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-finance';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, TRUE, '{}', '{}', NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET system_scope = TRUE, updated_at = NOW();
    END IF;

    -- demo-product-mgr: Product Manager, system scope
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-product-mgr';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, TRUE, '{}', '{}', NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET system_scope = TRUE, updated_at = NOW();
    END IF;

    -- demo-sysadmin: System Admin, system scope
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'demo-sysadmin';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, TRUE, '{}', '{}', NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET system_scope = TRUE, updated_at = NOW();
    END IF;

    -- admin-uiux: Full God-mode, system scope
    SELECT id INTO v_user_id FROM iam.user_account WHERE username = 'admin-uiux';
    IF v_user_id IS NOT NULL THEN
        INSERT INTO iam.user_scope_assignment
            (user_id, system_scope, region_ids, outlet_ids, created_at, updated_at)
        VALUES (v_user_id, TRUE, '{}', '{}', NOW(), NOW())
        ON CONFLICT (user_id) DO UPDATE SET system_scope = TRUE, updated_at = NOW();
    END IF;
END $$;

COMMIT;

-- Verify
SELECT r.name AS role_name, COUNT(rp.permission_id) AS perm_count
FROM iam.role r
LEFT JOIN iam.role_permission rp ON r.id = rp.role_id
WHERE r.id IN (2,3,4,5,6,7,8)
GROUP BY r.id, r.name
ORDER BY r.id;
