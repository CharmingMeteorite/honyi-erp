CREATE TABLE apply_requisitions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    creator_user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    submitted_at DATETIME NULL,
    approver_user_id BIGINT NULL,
    approved_at DATETIME NULL,
    executor_user_id BIGINT NULL,
    executed_at DATETIME NULL,
    remark VARCHAR(500) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_requisition_no (order_no),
    UNIQUE KEY uk_requisition_request (client_request_id),
    KEY idx_requisition_warehouse_status (warehouse_id,status,created_at),
    KEY idx_requisition_created (created_at),
    KEY idx_requisition_applicant (creator_user_id,created_at),
    KEY idx_requisition_department (department_id,status,created_at),
    CONSTRAINT fk_requisition_department FOREIGN KEY (department_id) REFERENCES sys_department(id),
    CONSTRAINT fk_requisition_warehouse FOREIGN KEY (warehouse_id) REFERENCES wh_warehouses(id),
    CONSTRAINT fk_requisition_creator FOREIGN KEY (creator_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_requisition_approver FOREIGN KEY (approver_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_requisition_executor FOREIGN KEY (executor_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_requisition_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_requisition_version CHECK (version >= 0),
    CONSTRAINT ck_requisition_approval CHECK (status NOT IN ('APPROVED','REJECTED','COMPLETED') OR (submitted_at IS NOT NULL AND approver_user_id IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT ck_requisition_execution CHECK ((status='COMPLETED' AND executor_user_id IS NOT NULL AND executed_at IS NOT NULL) OR (status<>'COMPLETED' AND executor_user_id IS NULL AND executed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE apply_requisition_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    UNIQUE KEY uk_requisition_item_product (order_id,product_id),
    UNIQUE KEY uk_requisition_item_order (id,order_id),
    CONSTRAINT fk_requisition_item_order FOREIGN KEY (order_id) REFERENCES apply_requisitions(id),
    CONSTRAINT fk_requisition_item_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT ck_requisition_item_quantity CHECK (quantity>0),
    CONSTRAINT ck_requisition_item_type CHECK (product_type IN ('DEVICE','QUANTITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE apply_requisition_devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    serial_number VARCHAR(128) NULL,
    mac_address VARCHAR(17) NULL,
    asset_no VARCHAR(64) NULL,
    UNIQUE KEY uk_requisition_device (order_id,device_id),
    UNIQUE KEY uk_requisition_device_link (id,device_id),
    CONSTRAINT fk_requisition_device_item FOREIGN KEY (item_id,order_id) REFERENCES apply_requisition_items(id,order_id),
    CONSTRAINT fk_requisition_device FOREIGN KEY (device_id) REFERENCES asset_devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 所有正式单据共用 Device 主键占用，避免领用与出库各自占用同一设备。
ALTER TABLE inv_order_device_claims
    DROP CHECK ck_order_claim_owner,
    ADD COLUMN requisition_device_id BIGINT NULL,
    ADD UNIQUE KEY uk_order_claim_requisition (requisition_device_id),
    ADD CONSTRAINT fk_order_claim_requisition FOREIGN KEY (requisition_device_id,device_id) REFERENCES apply_requisition_devices(id,device_id),
    ADD CONSTRAINT ck_order_claim_owner CHECK (
        (inbound_device_id IS NOT NULL) + (outbound_device_id IS NOT NULL) + (requisition_device_id IS NOT NULL) = 1
    );

INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES
('apply.requisition.view','领用申请查看','MENU','/apply/requisitions',140),
('apply.requisition.create','领用申请创建','BUTTON',NULL,141),
('apply.requisition.update','领用申请编辑/取消','BUTTON',NULL,142),
('apply.requisition.submit','领用申请提交','BUTTON',NULL,143),
('apply.requisition.approve','领用申请审批','BUTTON',NULL,144),
('apply.requisition.issue','领用仓库发放','BUTTON',NULL,145);
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code='apply.requisition.view'
SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND p.permission_code LIKE 'apply.requisition.%';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SYSTEM_ADMIN' AND p.permission_code LIKE 'apply.requisition.%';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('WAREHOUSE_ADMIN','DEPARTMENT_MANAGER','EMPLOYEE')
AND p.permission_code IN ('apply.requisition.view','apply.requisition.create','apply.requisition.update','apply.requisition.submit');
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code='WAREHOUSE_ADMIN' AND p.permission_code='apply.requisition.issue')
OR (r.role_code='DEPARTMENT_MANAGER' AND p.permission_code='apply.requisition.approve')
OR (r.role_code='MANAGEMENT' AND p.permission_code='apply.requisition.view');
