CREATE TABLE wh_warehouses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_code VARCHAR(64) NOT NULL,
    warehouse_name VARCHAR(100) NOT NULL,
    remark VARCHAR(500) NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wh_warehouses_code (warehouse_code),
    UNIQUE KEY uk_wh_warehouses_name (warehouse_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE asset_devices
    ADD CONSTRAINT fk_asset_devices_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id);

CREATE TABLE inv_stocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    locked_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_stocks_warehouse_product (warehouse_id, product_id),
    KEY idx_inv_stocks_product (product_id),
    CONSTRAINT fk_inv_stocks_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_inv_stocks_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT ck_inv_stocks_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_inv_stocks_locked CHECK (locked_quantity >= 0 AND locked_quantity <= quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_stock_ledgers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_no VARCHAR(64) NULL,
    direction VARCHAR(16) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    before_quantity DECIMAL(18,4) NOT NULL,
    after_quantity DECIMAL(18,4) NOT NULL,
    operator_user_id BIGINT NULL,
    operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_id VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_ledger_business (business_type, business_no, warehouse_id, product_id),
    KEY idx_inv_ledger_stock (warehouse_id, product_id, operated_at),
    KEY idx_inv_ledger_request (request_id),
    CONSTRAINT fk_inv_ledger_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_inv_ledger_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT fk_inv_ledger_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_inv_ledger_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inv_ledger_after CHECK (after_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_opening_balances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    opening_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    remark VARCHAR(500) NULL,
    completed_at DATETIME NULL,
    operator_user_id BIGINT NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_opening_no (opening_no),
    KEY idx_inv_opening_warehouse_status (warehouse_id, status),
    CONSTRAINT fk_inv_opening_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_inv_opening_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_opening_balance_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    opening_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_opening_item_product (opening_id, product_id),
    KEY idx_inv_opening_item_product (product_id),
    CONSTRAINT fk_inv_opening_item_opening FOREIGN KEY (opening_id) REFERENCES inv_opening_balances(id),
    CONSTRAINT fk_inv_opening_item_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT ck_inv_opening_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_opening_balance_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    opening_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_opening_device (opening_id, device_id),
    UNIQUE KEY uk_inv_opening_item_device (item_id, device_id),
    KEY idx_inv_opening_device_device (device_id),
    CONSTRAINT fk_inv_opening_device_opening FOREIGN KEY (opening_id) REFERENCES inv_opening_balances(id),
    CONSTRAINT fk_inv_opening_device_item FOREIGN KEY (item_id) REFERENCES inv_opening_balance_items(id),
    CONSTRAINT fk_inv_opening_device_device FOREIGN KEY (device_id) REFERENCES asset_devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (permission_code, permission_name, permission_type, route_path, sort_order) VALUES
('system.warehouse.view','仓库查看','MENU','/system/warehouses',100),
('system.warehouse.create','新增仓库','BUTTON',NULL,101),
('system.warehouse.update','编辑仓库','BUTTON',NULL,102),
('system.warehouse.disable','启停仓库','BUTTON',NULL,103),
('inventory.stock.view','库存查看','MENU','/inventory/stocks',110),
('inventory.stock.opening','期初库存','BUTTON',NULL,111),
('inventory.stock.ledger','库存流水','BUTTON',NULL,112);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SYSTEM_ADMIN' AND p.permission_code IN
('system.warehouse.view','system.warehouse.create','system.warehouse.update','system.warehouse.disable',
 'inventory.stock.view','inventory.stock.opening','inventory.stock.ledger');
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('system.warehouse.view','system.warehouse.create','system.warehouse.update','system.warehouse.disable',
 'inventory.stock.view','inventory.stock.opening','inventory.stock.ledger')
WHERE r.role_code='WAREHOUSE_ADMIN';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('system.warehouse.view','inventory.stock.view','inventory.stock.ledger')
WHERE r.role_code IN ('MANAGEMENT','DEPARTMENT_MANAGER');
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code = CONCAT(SUBSTRING_INDEX(p.permission_code, '.', 2), '.view')
SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND p.parent_id IS NULL;
