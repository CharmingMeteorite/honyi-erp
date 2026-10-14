ALTER TABLE asset_devices ADD COLUMN version BIGINT NOT NULL DEFAULT 0, ADD CONSTRAINT ck_device_version CHECK (version >= 0);

CREATE TABLE asset_warranty_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    applicant_user_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    handler_user_id BIGINT NULL,
    approver_user_id BIGINT NULL,
    executor_user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(500) NOT NULL,
    remark VARCHAR(500) NULL,
    result VARCHAR(1000) NULL,
    submitted_at DATETIME NULL,
    accepted_at DATETIME NULL,
    processed_at DATETIME NULL,
    completed_at DATETIME NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    fault_description VARCHAR(1000) NOT NULL,
    warranty_start_at DATETIME NULL,
    warranty_end_at DATETIME NULL,
    warranty_status VARCHAR(32) NOT NULL,
    decision VARCHAR(32) NULL,
    warranty_no VARCHAR(64) GENERATED ALWAYS AS (order_no) STORED,
    UNIQUE KEY uk_warranty_no (warranty_no),
    UNIQUE KEY uk_warranty_request (client_request_id),
    UNIQUE KEY uk_warranty_device (id,device_id),
    KEY idx_warranty_scope (department_id,status,created_at),
    KEY idx_warranty_applicant (applicant_user_id,created_at),
    KEY idx_warranty_device (device_id,status),
    CONSTRAINT fk_warranty_device FOREIGN KEY (device_id) REFERENCES asset_devices(id),
    CONSTRAINT fk_warranty_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT fk_warranty_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_warranty_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_warranty_handler FOREIGN KEY (handler_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_warranty_approver FOREIGN KEY (approver_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_warranty_executor FOREIGN KEY (executor_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_warranty_version CHECK (version >= 0),
    CONSTRAINT ck_warranty_status CHECK (status IN ('DRAFT','SUBMITTED','ACCEPTED','APPROVED','REJECTED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_warranty_submitted CHECK (status IN ('DRAFT','CANCELLED') OR submitted_at IS NOT NULL),
    CONSTRAINT ck_warranty_complete CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT ck_warranty_dates CHECK (warranty_start_at IS NULL OR warranty_end_at IS NULL OR warranty_end_at >= warranty_start_at),
    CONSTRAINT ck_warranty_validity CHECK (warranty_status IN ('VALID','EXPIRED','NOT_STARTED','UNKNOWN')),
    CONSTRAINT ck_warranty_decision CHECK (decision IS NULL OR decision IN ('WARRANTY','PAID_REPAIR','REJECTED')),
    CONSTRAINT ck_warranty_approved CHECK (status NOT IN ('APPROVED','REJECTED','COMPLETED') OR (approver_user_id IS NOT NULL AND decision IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_repair_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    applicant_user_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    handler_user_id BIGINT NULL,
    approver_user_id BIGINT NULL,
    executor_user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(500) NOT NULL,
    remark VARCHAR(500) NULL,
    result VARCHAR(1000) NULL,
    submitted_at DATETIME NULL,
    accepted_at DATETIME NULL,
    processed_at DATETIME NULL,
    completed_at DATETIME NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    original_status VARCHAR(32) NOT NULL,
    original_warehouse_id BIGINT NULL,
    original_department_id BIGINT NULL,
    original_user_id BIGINT NULL,
    original_custodian_id BIGINT NULL,
    fault_phenomenon VARCHAR(200) NOT NULL,
    fault_description VARCHAR(1000) NOT NULL,
    notes VARCHAR(1000) NULL,
    service_provider VARCHAR(200) NULL,
    service_location VARCHAR(200) NULL,
    expected_complete_at DATETIME NULL,
    sent_at DATETIME NULL,
    warranty_id BIGINT NULL,
    repair_no VARCHAR(64) GENERATED ALWAYS AS (order_no) STORED,
    UNIQUE KEY uk_repair_no (repair_no),
    UNIQUE KEY uk_repair_request (client_request_id),
    UNIQUE KEY uk_repair_device (id,device_id),
    KEY idx_repair_scope (department_id,status,created_at),
    KEY idx_repair_applicant (applicant_user_id,created_at),
    KEY idx_repair_device (device_id,status),
    CONSTRAINT fk_repair_device FOREIGN KEY (device_id) REFERENCES asset_devices(id),
    CONSTRAINT fk_repair_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT fk_repair_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_repair_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_repair_handler FOREIGN KEY (handler_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_repair_approver FOREIGN KEY (approver_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_repair_executor FOREIGN KEY (executor_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_repair_version CHECK (version >= 0),
    CONSTRAINT ck_repair_status CHECK (status IN ('DRAFT','SUBMITTED','ACCEPTED','REPAIRING','COMPLETED','UNREPAIRABLE','CANCELLED','CLOSED')),
    CONSTRAINT ck_repair_submitted CHECK (status IN ('DRAFT','CANCELLED') OR submitted_at IS NOT NULL),
    CONSTRAINT ck_repair_complete CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT fk_repair_original_warehouse FOREIGN KEY (original_warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_repair_original_department FOREIGN KEY (original_department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_repair_original_user FOREIGN KEY (original_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_repair_original_custodian FOREIGN KEY (original_custodian_id) REFERENCES sys_user(id),
    UNIQUE KEY uk_repair_warranty (warranty_id),
    CONSTRAINT fk_repair_warranty FOREIGN KEY (warranty_id) REFERENCES asset_warranty_orders(id),
    CONSTRAINT ck_repair_original CHECK (original_status IN ('IN_STOCK','IN_USE')),
    CONSTRAINT ck_repair_sent CHECK (status NOT IN ('REPAIRING','COMPLETED','UNREPAIRABLE','CLOSED') OR sent_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_exchange_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    applicant_user_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    handler_user_id BIGINT NULL,
    approver_user_id BIGINT NULL,
    executor_user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(500) NOT NULL,
    remark VARCHAR(500) NULL,
    result VARCHAR(1000) NULL,
    submitted_at DATETIME NULL,
    accepted_at DATETIME NULL,
    processed_at DATETIME NULL,
    completed_at DATETIME NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    original_status VARCHAR(32) NOT NULL,
    original_warehouse_id BIGINT NULL,
    original_department_id BIGINT NULL,
    original_user_id BIGINT NULL,
    original_custodian_id BIGINT NULL,
    new_device_id BIGINT NOT NULL,
    new_device_code VARCHAR(64) NOT NULL,
    new_serial_number VARCHAR(128) NULL,
    new_mac_address VARCHAR(17) NULL,
    new_asset_no VARCHAR(64) NULL,
    new_warehouse_id BIGINT NOT NULL,
    new_department_id BIGINT NULL,
    new_user_id BIGINT NULL,
    new_custodian_id BIGINT NULL,
    exchange_no VARCHAR(64) GENERATED ALWAYS AS (order_no) STORED,
    old_device_id BIGINT GENERATED ALWAYS AS (device_id) STORED,
    old_device_code VARCHAR(64) GENERATED ALWAYS AS (device_code) STORED,
    UNIQUE KEY uk_exchange_no (exchange_no),
    UNIQUE KEY uk_exchange_request (client_request_id),
    UNIQUE KEY uk_exchange_device (id,device_id),
    KEY idx_exchange_scope (department_id,status,created_at),
    KEY idx_exchange_applicant (applicant_user_id,created_at),
    KEY idx_exchange_device (device_id,status),
    CONSTRAINT fk_exchange_device FOREIGN KEY (device_id) REFERENCES asset_devices(id),
    CONSTRAINT fk_exchange_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT fk_exchange_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_exchange_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_exchange_handler FOREIGN KEY (handler_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_exchange_approver FOREIGN KEY (approver_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_exchange_executor FOREIGN KEY (executor_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_exchange_version CHECK (version >= 0),
    CONSTRAINT ck_exchange_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_exchange_submitted CHECK (status IN ('DRAFT','CANCELLED') OR submitted_at IS NOT NULL),
    CONSTRAINT ck_exchange_complete CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
    CONSTRAINT fk_exchange_original_warehouse FOREIGN KEY (original_warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_exchange_original_department FOREIGN KEY (original_department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_exchange_original_user FOREIGN KEY (original_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_exchange_original_custodian FOREIGN KEY (original_custodian_id) REFERENCES sys_user(id),
    CONSTRAINT fk_exchange_new_device FOREIGN KEY (new_device_id) REFERENCES asset_devices(id),
    CONSTRAINT fk_exchange_new_warehouse FOREIGN KEY (new_warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_exchange_new_department FOREIGN KEY (new_department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_exchange_new_user FOREIGN KEY (new_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_exchange_new_custodian FOREIGN KEY (new_custodian_id) REFERENCES sys_user(id),
    CONSTRAINT ck_exchange_distinct CHECK (device_id <> new_device_id),
    CONSTRAINT ck_exchange_original CHECK (original_status IN ('IN_STOCK','IN_USE')),
    CONSTRAINT ck_exchange_approval CHECK (status NOT IN ('APPROVED','REJECTED','COMPLETED') OR approver_user_id IS NOT NULL),
    CONSTRAINT ck_exchange_execution CHECK (status <> 'COMPLETED' OR executor_user_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asset_exchange_devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    side VARCHAR(8) NOT NULL,
    UNIQUE KEY uk_exchange_side (order_id,side),
    UNIQUE KEY uk_exchange_link (order_id,device_id),
    UNIQUE KEY uk_exchange_device_identity (id,device_id),
    CONSTRAINT fk_exchange_link_order FOREIGN KEY (order_id) REFERENCES asset_exchange_orders(id),
    CONSTRAINT fk_exchange_link_device FOREIGN KEY (device_id) REFERENCES asset_devices(id),
    CONSTRAINT ck_exchange_side CHECK (side IN ('OLD','NEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE inv_order_device_claims
    DROP CHECK ck_order_claim_owner,
    ADD COLUMN repair_order_id BIGINT NULL,
    ADD COLUMN exchange_device_id BIGINT NULL,
    ADD UNIQUE KEY uk_claim_repair (repair_order_id),
    ADD UNIQUE KEY uk_claim_exchange (exchange_device_id),
    ADD CONSTRAINT fk_claim_repair FOREIGN KEY (repair_order_id,device_id) REFERENCES asset_repair_orders(id,device_id),
    ADD CONSTRAINT fk_claim_exchange FOREIGN KEY (exchange_device_id,device_id) REFERENCES asset_exchange_devices(id,device_id),
    ADD CONSTRAINT ck_order_claim_owner CHECK ((inbound_device_id IS NOT NULL)+(outbound_device_id IS NOT NULL)+(requisition_device_id IS NOT NULL)+(repair_order_id IS NOT NULL)+(exchange_device_id IS NOT NULL)=1);

INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.repair.view','维修 view','MENU','/assets/repairs',170);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.repair.create','维修 create','BUTTON',NULL,171);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.repair.update','维修 update','BUTTON',NULL,172);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.repair.submit','维修 submit','BUTTON',NULL,173);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.repair.process','维修 process','BUTTON',NULL,174);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.repair.complete','维修 complete','BUTTON',NULL,175);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.repair.cancel','维修 cancel','BUTTON',NULL,176);
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code='asset.repair.view' SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND p.permission_code LIKE 'asset.repair.%';
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.view','保修 view','MENU','/assets/warrantys',180);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.create','保修 create','BUTTON',NULL,181);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.update','保修 update','BUTTON',NULL,182);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.submit','保修 submit','BUTTON',NULL,183);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.process','保修 process','BUTTON',NULL,184);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.approve','保修 approve','BUTTON',NULL,185);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.reject','保修 reject','BUTTON',NULL,186);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.complete','保修 complete','BUTTON',NULL,187);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.warranty.cancel','保修 cancel','BUTTON',NULL,188);
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code='asset.warranty.view' SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND p.permission_code LIKE 'asset.warranty.%';
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.view','换货 view','MENU','/assets/exchanges',190);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.create','换货 create','BUTTON',NULL,191);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.update','换货 update','BUTTON',NULL,192);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.submit','换货 submit','BUTTON',NULL,193);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.approve','换货 approve','BUTTON',NULL,194);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.reject','换货 reject','BUTTON',NULL,195);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.execute','换货 execute','BUTTON',NULL,196);
INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES('asset.exchange.cancel','换货 cancel','BUTTON',NULL,197);
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code='asset.exchange.view' SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND p.permission_code LIKE 'asset.exchange.%';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('SYSTEM_ADMIN','WAREHOUSE_ADMIN','DEPARTMENT_MANAGER') AND (p.permission_code LIKE 'asset.repair.%' OR p.permission_code LIKE 'asset.warranty.%' OR p.permission_code LIKE 'asset.exchange.%');
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='MANAGEMENT' AND p.permission_code IN ('asset.repair.view','asset.warranty.view','asset.exchange.view');
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='EMPLOYEE' AND p.permission_code IN ('asset.repair.view','asset.repair.create','asset.repair.update','asset.repair.submit','asset.repair.cancel','asset.warranty.view','asset.warranty.create','asset.warranty.update','asset.warranty.submit','asset.warranty.cancel','asset.exchange.view');

CREATE TABLE asset_service_requests (
    client_request_id VARCHAR(64) NOT NULL PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    order_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_service_request_kind CHECK (kind IN ('repair','warranty','exchange')),
    CONSTRAINT fk_service_request_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id),
    KEY idx_service_request_order (kind,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
