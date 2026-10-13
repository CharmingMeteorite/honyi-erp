CREATE TABLE asset_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_code VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_STOCK',
    department_id BIGINT NULL,
    user_id BIGINT NULL,
    custodian_id BIGINT NULL,
    purchase_at DATETIME NULL,
    inbound_at DATETIME NULL,
    first_use_at DATETIME NULL,
    warranty_start_at DATETIME NULL,
    warranty_end_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    notes VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_devices_code (device_code),
    UNIQUE KEY uk_asset_devices_serial (serial_number),
    UNIQUE KEY uk_asset_devices_mac (mac_address),
    UNIQUE KEY uk_asset_devices_asset_no (asset_no),
    KEY idx_asset_devices_product (product_id),
    KEY idx_asset_devices_status (status),
    KEY idx_asset_devices_department (department_id),
    KEY idx_asset_devices_user (user_id),
    KEY idx_asset_devices_custodian (custodian_id),
    CONSTRAINT fk_asset_devices_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT fk_asset_devices_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_asset_devices_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_asset_devices_custodian FOREIGN KEY (custodian_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_device_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    from_department_id BIGINT NULL,
    to_department_id BIGINT NULL,
    from_user_id BIGINT NULL,
    to_user_id BIGINT NULL,
    from_custodian_id BIGINT NULL,
    to_custodian_id BIGINT NULL,
    detail VARCHAR(500) NULL,
    operator_user_id BIGINT NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_asset_device_history_device (device_id, created_at),
    KEY idx_asset_device_history_operator (operator_user_id),
    CONSTRAINT fk_asset_device_history_device FOREIGN KEY (device_id) REFERENCES asset_devices(id),
    CONSTRAINT fk_asset_device_history_from_department FOREIGN KEY (from_department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_asset_device_history_to_department FOREIGN KEY (to_department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_asset_device_history_from_user FOREIGN KEY (from_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_asset_device_history_to_user FOREIGN KEY (to_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_asset_device_history_from_custodian FOREIGN KEY (from_custodian_id) REFERENCES sys_user(id),
    CONSTRAINT fk_asset_device_history_to_custodian FOREIGN KEY (to_custodian_id) REFERENCES sys_user(id),
    CONSTRAINT fk_asset_device_history_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (permission_code, permission_name, permission_type, route_path, sort_order) VALUES
('system.device.view','设备管理','MENU','/assets/devices',90),
('system.device.create','新增设备','BUTTON',NULL,91),
('system.device.update','编辑设备','BUTTON',NULL,92),
('system.device.status','设备状态','BUTTON',NULL,93);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.permission_code IN ('system.device.view','system.device.create','system.device.update','system.device.status')
WHERE r.role_code = 'SYSTEM_ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.permission_code = 'system.device.view'
WHERE r.role_code IN ('WAREHOUSE_ADMIN','DEPARTMENT_MANAGER','MANAGEMENT');

UPDATE sys_permission p
JOIN sys_permission m ON m.permission_code = CONCAT(SUBSTRING_INDEX(p.permission_code, '.', 2), '.view')
SET p.parent_id = m.id
WHERE p.permission_type = 'BUTTON' AND p.parent_id IS NULL;
