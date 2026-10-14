CREATE TABLE inv_stocktakes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stocktake_no VARCHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    creator_user_id BIGINT NOT NULL,
    submitted_at DATETIME NULL,
    confirmer_user_id BIGINT NULL,
    confirmed_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stocktake_no (stocktake_no),
    UNIQUE KEY uk_stocktake_request (client_request_id),
    KEY idx_stocktake_warehouse_status (warehouse_id,status,created_at),
    CONSTRAINT fk_stocktake_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_stocktake_creator FOREIGN KEY (creator_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_stocktake_confirmer FOREIGN KEY (confirmer_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_stocktake_status CHECK (status IN ('DRAFT','COUNTING','COUNTED','CONFIRMED','CANCELLED')),
    CONSTRAINT ck_stocktake_version CHECK (version >= 0),
    CONSTRAINT ck_stocktake_confirmed CHECK ((status='CONFIRMED' AND confirmed_at IS NOT NULL AND confirmer_user_id IS NOT NULL)
        OR (status<>'CONFIRMED' AND confirmed_at IS NULL AND confirmer_user_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_stocktake_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stocktake_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    snapshot_quantity DECIMAL(18,4) NULL,
    snapshot_locked_quantity DECIMAL(18,4) NULL,
    snapshot_ledger_id BIGINT NULL,
    counted_quantity DECIMAL(18,4) NULL,
    difference_quantity DECIMAL(18,4) NULL,
    UNIQUE KEY uk_stocktake_item_product (stocktake_id,product_id),
    UNIQUE KEY uk_stocktake_item_order (id,stocktake_id),
    KEY idx_stocktake_item_product (product_id),
    CONSTRAINT fk_stocktake_item_order FOREIGN KEY (stocktake_id) REFERENCES inv_stocktakes(id),
    CONSTRAINT fk_stocktake_item_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT ck_stocktake_item_type CHECK (product_type IN ('QUANTITY','DEVICE')),
    CONSTRAINT ck_stocktake_item_snapshot CHECK (snapshot_quantity IS NULL OR snapshot_quantity >= 0),
    CONSTRAINT ck_stocktake_item_count CHECK (counted_quantity IS NULL OR counted_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_stocktake_device_checks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stocktake_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    device_code VARCHAR(64) NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    expected TINYINT(1) NOT NULL,
    observed TINYINT(1) NOT NULL DEFAULT 0,
    difference_code VARCHAR(32) NULL,
    actual_product_id BIGINT NULL,
    actual_warehouse_id BIGINT NULL,
    actual_status VARCHAR(32) NULL,
    UNIQUE KEY uk_stocktake_device_code (stocktake_id,device_code),
    KEY idx_stocktake_device_item (item_id,expected,difference_code),
    KEY idx_stocktake_device_id (device_id),
    CONSTRAINT fk_stocktake_device_item FOREIGN KEY (item_id,stocktake_id) REFERENCES inv_stocktake_items(id,stocktake_id),
    CONSTRAINT fk_stocktake_device FOREIGN KEY (device_id) REFERENCES asset_devices(id),
    CONSTRAINT ck_stocktake_device_expected CHECK (expected IN (0,1) AND observed IN (0,1)),
    CONSTRAINT ck_stocktake_device_difference CHECK (difference_code IS NULL OR difference_code IN
        ('MATCH','MISSING','EXTRA','UNREGISTERED','WRONG_PRODUCT','OUTSIDE_WAREHOUSE','STATUS_ABNORMAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES
('inventory.stocktake.view','盘点查看','MENU','/inventory/stocktakes',150),
('inventory.stocktake.create','盘点创建','BUTTON',NULL,151),
('inventory.stocktake.update','盘点编辑/取消','BUTTON',NULL,152),
('inventory.stocktake.submit','提交盘点','BUTTON',NULL,153),
('inventory.stocktake.count','录入实盘','BUTTON',NULL,154),
('inventory.stocktake.confirm','确认差异','BUTTON',NULL,155);
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code='inventory.stocktake.view'
SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND p.permission_code LIKE 'inventory.stocktake.%';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('SYSTEM_ADMIN','WAREHOUSE_ADMIN') AND p.permission_code LIKE 'inventory.stocktake.%';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='DEPARTMENT_MANAGER' AND p.permission_code IN ('inventory.stocktake.view','inventory.stocktake.count');
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='MANAGEMENT' AND p.permission_code='inventory.stocktake.view';
