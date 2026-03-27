INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES
    ('pos.session.read', 'Read POS sessions', 'Read POS session records', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.session.open', 'Open POS sessions', 'Open POS sessions for outlets', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.session.close', 'Close POS sessions', 'Close POS sessions for outlets', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.session.reconcile', 'Reconcile POS sessions', 'Reconcile closed POS sessions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.order.read', 'Read POS orders', 'Read POS sale orders and payments', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.order.create', 'Create POS orders', 'Create POS sale orders', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.order.update', 'Update POS orders', 'Update POS sale orders while open', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.order.cancel', 'Cancel POS orders', 'Cancel POS sale orders before payment completion', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('pos.order.complete', 'Complete POS orders', 'Complete POS sale orders and emit sale events', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('inventory.balance.read', 'Read inventory balances', 'Read inventory stock balances by outlet', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('inventory.ledger.read', 'Read inventory ledger', 'Read inventory transaction ledger by outlet', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('inventory.adjustment.write', 'Write stock adjustments', 'Create, post and cancel stock adjustments', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('inventory.waste.write', 'Write waste records', 'Create, post and cancel waste records', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('inventory.stock_count.write', 'Write stock counts', 'Create and update stock count sessions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('inventory.stock_count.post', 'Post stock counts', 'Post stock count sessions into inventory ledger', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.supplier.read', 'Read suppliers', 'Read supplier master data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.supplier.write', 'Write suppliers', 'Create and update supplier master data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.po.read', 'Read purchase orders', 'Read purchase orders', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.po.create', 'Create purchase orders', 'Create purchase orders', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.po.update', 'Update purchase orders', 'Update purchase orders before issue', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.po.submit', 'Submit purchase orders', 'Submit purchase orders for approval', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.po.approve', 'Approve purchase orders', 'Approve purchase orders within regional scope', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.po.issue', 'Issue purchase orders', 'Issue approved purchase orders to suppliers', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.po.cancel', 'Cancel purchase orders', 'Cancel purchase orders before completion', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.gr.read', 'Read goods receipts', 'Read goods receipt records', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.gr.create', 'Create goods receipts', 'Create and receive goods receipt documents', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.gr.post', 'Post goods receipts', 'Post goods receipts into stock and finance flows', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.gr.cancel', 'Cancel goods receipts', 'Cancel goods receipt documents', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.invoice.read', 'Read supplier invoices', 'Read supplier invoice documents', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.invoice.review', 'Review supplier invoices', 'Review supplier invoice matching and status', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.invoice.approve', 'Approve supplier invoices', 'Approve supplier invoices for payment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.invoice.dispute', 'Dispute supplier invoices', 'Dispute supplier invoices', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.payment.read', 'Read supplier payments', 'Read supplier payment records', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('procurement.payment.record', 'Record supplier payments', 'Record supplier payment settlement', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role (code, name, description, status, created_at, updated_at)
VALUES
    ('staff', 'Staff', 'POS operating staff within outlet scope', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('outlet_manager', 'Outlet Manager', 'Outlet operations manager for POS, inventory and procurement operations', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('regional_finance', 'Regional Finance', 'Regional finance approver for procurement invoices and purchase orders', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('finance', 'Finance', 'Corporate finance operator for supplier master and supplier payment recording', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'pos.order.read',
    'pos.order.create',
    'pos.order.update',
    'pos.order.cancel',
    'pos.order.complete',
    'catalog.internal.resolve',
    'catalog.product.read',
    'catalog.price.read'
)
WHERE role.code = 'staff'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'pos.session.read',
    'pos.session.open',
    'pos.session.close',
    'pos.session.reconcile',
    'pos.order.read',
    'pos.order.create',
    'pos.order.update',
    'pos.order.cancel',
    'pos.order.complete',
    'inventory.balance.read',
    'inventory.ledger.read',
    'inventory.adjustment.write',
    'inventory.waste.write',
    'inventory.stock_count.write',
    'inventory.stock_count.post',
    'procurement.supplier.read',
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
    'catalog.internal.resolve',
    'catalog.product.read',
    'catalog.price.read',
    'org.scope.resolve'
)
WHERE role.code = 'outlet_manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'procurement.supplier.read',
    'procurement.po.read',
    'procurement.po.approve',
    'procurement.gr.read',
    'procurement.invoice.read',
    'procurement.invoice.review',
    'procurement.invoice.approve',
    'procurement.invoice.dispute',
    'procurement.payment.read',
    'org.scope.resolve'
)
WHERE role.code = 'regional_finance'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM iam.role role
JOIN iam.permission permission ON permission.code IN (
    'procurement.supplier.read',
    'procurement.supplier.write',
    'procurement.invoice.read',
    'procurement.payment.read',
    'procurement.payment.record',
    'org.scope.resolve'
)
WHERE role.code = 'finance'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 1, permission.id, CURRENT_TIMESTAMP
FROM iam.permission permission
WHERE permission.code IN (
    'pos.session.read',
    'pos.session.open',
    'pos.session.close',
    'pos.session.reconcile',
    'pos.order.read',
    'pos.order.create',
    'pos.order.update',
    'pos.order.cancel',
    'pos.order.complete',
    'inventory.balance.read',
    'inventory.ledger.read',
    'inventory.adjustment.write',
    'inventory.waste.write',
    'inventory.stock_count.write',
    'inventory.stock_count.post',
    'procurement.supplier.read',
    'procurement.supplier.write',
    'procurement.po.read',
    'procurement.po.create',
    'procurement.po.update',
    'procurement.po.submit',
    'procurement.po.approve',
    'procurement.po.issue',
    'procurement.po.cancel',
    'procurement.gr.read',
    'procurement.gr.create',
    'procurement.gr.post',
    'procurement.gr.cancel',
    'procurement.invoice.read',
    'procurement.invoice.review',
    'procurement.invoice.approve',
    'procurement.invoice.dispute',
    'procurement.payment.read',
    'procurement.payment.record'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;
