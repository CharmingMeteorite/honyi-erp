CREATE TABLE sys_qr_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    product_id BIGINT NULL,
    device_id BIGINT NULL,
    format_version TINYINT NOT NULL DEFAULT 1,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_qr_token (token),
    UNIQUE KEY uk_sys_qr_product (product_id),
    UNIQUE KEY uk_sys_qr_device (device_id),
    CONSTRAINT ck_sys_qr_resource CHECK (
        (resource_type='PRODUCT' AND product_id IS NOT NULL AND device_id IS NULL)
        OR (resource_type='DEVICE' AND device_id IS NOT NULL AND product_id IS NULL)
    ),
    CONSTRAINT ck_sys_qr_version CHECK (format_version=1),
    CONSTRAINT fk_sys_qr_product FOREIGN KEY (product_id) REFERENCES md_products(id),
    CONSTRAINT fk_sys_qr_device FOREIGN KEY (device_id) REFERENCES asset_devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES
('asset.qr.view','二维码管理','MENU','/assets/qr',200),
('asset.qr.generate','生成二维码','BUTTON',NULL,201),
('asset.qr.scan','扫码查询','BUTTON',NULL,202),
('asset.qr.print','打印标签','BUTTON',NULL,203);
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code='asset.qr.view'
SET p.parent_id=m.id WHERE p.permission_code IN ('asset.qr.generate','asset.qr.scan','asset.qr.print');
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('SYSTEM_ADMIN','WAREHOUSE_ADMIN','DEPARTMENT_MANAGER')
  AND p.permission_code LIKE 'asset.qr.%';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('MANAGEMENT','EMPLOYEE')
  AND p.permission_code IN ('asset.qr.view','asset.qr.scan');
