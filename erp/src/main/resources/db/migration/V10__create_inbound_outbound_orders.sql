CREATE TABLE inv_inbound_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    creator_user_id BIGINT NOT NULL,
    submitted_at DATETIME NULL,
    approver_user_id BIGINT NULL,
    approved_at DATETIME NULL,
    executor_user_id BIGINT NULL,
    executed_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inbound_no (order_no),
    UNIQUE KEY uk_inbound_request (client_request_id),
    KEY idx_inbound_warehouse_status (warehouse_id,status,created_at),
    KEY idx_inbound_created (created_at),
    CONSTRAINT fk_inbound_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_inbound_creator FOREIGN KEY (creator_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_inbound_approver FOREIGN KEY (approver_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_inbound_executor FOREIGN KEY (executor_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_inbound_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_inbound_version CHECK (version >= 0),
    CONSTRAINT ck_inbound_approval CHECK (status NOT IN ('APPROVED','REJECTED','COMPLETED') OR (submitted_at IS NOT NULL AND approver_user_id IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT ck_inbound_execution CHECK ((status='COMPLETED' AND executor_user_id IS NOT NULL AND executed_at IS NOT NULL) OR (status<>'COMPLETED' AND executor_user_id IS NULL AND executed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_inbound_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    UNIQUE KEY uk_inbound_item_product (order_id,product_id),
    UNIQUE KEY uk_inbound_item_order (id,order_id),
    CONSTRAINT fk_inbound_item_order FOREIGN KEY (order_id) REFERENCES inv_inbound_orders(id),
    CONSTRAINT fk_inbound_item_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT ck_inbound_item_quantity CHECK (quantity>0),
    CONSTRAINT ck_inbound_item_type CHECK (product_type IN ('DEVICE','QUANTITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_inbound_devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    UNIQUE KEY uk_inbound_device (order_id,device_id),
    UNIQUE KEY uk_inbound_device_link (id,device_id),
    CONSTRAINT fk_inbound_device_item FOREIGN KEY (item_id,order_id) REFERENCES inv_inbound_items(id,order_id),
    CONSTRAINT fk_inbound_device FOREIGN KEY (device_id) REFERENCES asset_devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_outbound_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    creator_user_id BIGINT NOT NULL,
    submitted_at DATETIME NULL,
    approver_user_id BIGINT NULL,
    approved_at DATETIME NULL,
    executor_user_id BIGINT NULL,
    executed_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_outbound_no (order_no),
    UNIQUE KEY uk_outbound_request (client_request_id),
    KEY idx_outbound_warehouse_status (warehouse_id,status,created_at),
    KEY idx_outbound_created (created_at),
    CONSTRAINT fk_outbound_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_outbound_creator FOREIGN KEY (creator_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_outbound_approver FOREIGN KEY (approver_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_outbound_executor FOREIGN KEY (executor_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_outbound_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_outbound_version CHECK (version >= 0),
    CONSTRAINT ck_outbound_approval CHECK (status NOT IN ('APPROVED','REJECTED','COMPLETED') OR (submitted_at IS NOT NULL AND approver_user_id IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT ck_outbound_execution CHECK ((status='COMPLETED' AND executor_user_id IS NOT NULL AND executed_at IS NOT NULL) OR (status<>'COMPLETED' AND executor_user_id IS NULL AND executed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_outbound_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    UNIQUE KEY uk_outbound_item_product (order_id,product_id),
    UNIQUE KEY uk_outbound_item_order (id,order_id),
    CONSTRAINT fk_outbound_item_order FOREIGN KEY (order_id) REFERENCES inv_outbound_orders(id),
    CONSTRAINT fk_outbound_item_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT ck_outbound_item_quantity CHECK (quantity>0),
    CONSTRAINT ck_outbound_item_type CHECK (product_type IN ('DEVICE','QUANTITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_outbound_devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    UNIQUE KEY uk_outbound_device (order_id,device_id),
    UNIQUE KEY uk_outbound_device_link (id,device_id),
    CONSTRAINT fk_outbound_device_item FOREIGN KEY (item_id,order_id) REFERENCES inv_outbound_items(id,order_id),
    CONSTRAINT fk_outbound_device FOREIGN KEY (device_id) REFERENCES asset_devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inv_order_device_claims (
    device_id BIGINT NOT NULL PRIMARY KEY,
    inbound_device_id BIGINT NULL,
    outbound_device_id BIGINT NULL,
    UNIQUE KEY uk_order_claim_inbound (inbound_device_id),
    UNIQUE KEY uk_order_claim_outbound (outbound_device_id),
    CONSTRAINT fk_order_claim_device FOREIGN KEY (device_id) REFERENCES asset_devices(id),
    CONSTRAINT fk_order_claim_inbound FOREIGN KEY (inbound_device_id,device_id) REFERENCES inv_inbound_devices(id,device_id),
    CONSTRAINT fk_order_claim_outbound FOREIGN KEY (outbound_device_id,device_id) REFERENCES inv_outbound_devices(id,device_id),
    CONSTRAINT ck_order_claim_owner CHECK ((inbound_device_id IS NOT NULL AND outbound_device_id IS NULL) OR (inbound_device_id IS NULL AND outbound_device_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE asset_device_history
    ADD COLUMN from_warehouse_id BIGINT NULL,
    ADD COLUMN to_warehouse_id BIGINT NULL,
    ADD COLUMN business_type VARCHAR(32) NULL,
    ADD COLUMN business_no VARCHAR(64) NULL,
    ADD CONSTRAINT fk_device_history_from_warehouse FOREIGN KEY (from_warehouse_id) REFERENCES wh_warehouses(id),
    ADD CONSTRAINT fk_device_history_to_warehouse FOREIGN KEY (to_warehouse_id) REFERENCES wh_warehouses(id);

INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.inbound.view','入库查看','MENU','/inventory/inbounds',120);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.inbound.create','入库创建','BUTTON',NULL,121);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.inbound.update','入库编辑/取消','BUTTON',NULL,122);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.inbound.submit','入库提交','BUTTON',NULL,123);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.inbound.approve','入库审批','BUTTON',NULL,124);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.inbound.execute','入库执行','BUTTON',NULL,125);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.outbound.view','出库查看','MENU','/inventory/outbounds',130);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.outbound.create','出库创建','BUTTON',NULL,131);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.outbound.update','出库编辑/取消','BUTTON',NULL,132);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.outbound.submit','出库提交','BUTTON',NULL,133);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.outbound.approve','出库审批','BUTTON',NULL,134);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('inventory.outbound.execute','出库执行','BUTTON',NULL,135);
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('SYSTEM_ADMIN','WAREHOUSE_ADMIN') AND (p.permission_code LIKE 'inventory.inbound.%' OR p.permission_code LIKE 'inventory.outbound.%');
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('MANAGEMENT','DEPARTMENT_MANAGER') AND p.permission_code IN ('inventory.inbound.view','inventory.outbound.view');
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code=CONCAT(SUBSTRING_INDEX(p.permission_code,'.',2),'.view')
SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND (p.permission_code LIKE 'inventory.inbound.%' OR p.permission_code LIKE 'inventory.outbound.%');
