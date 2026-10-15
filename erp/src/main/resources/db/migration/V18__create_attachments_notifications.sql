CREATE TABLE sys_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_type VARCHAR(24) NOT NULL,
    business_id BIGINT NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    object_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uploader_user_id BIGINT NOT NULL,
    client_request_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_attachment_object_key (object_key),
    UNIQUE KEY uk_attachment_request (uploader_user_id,client_request_id),
    KEY idx_attachment_business (business_type,business_id,status,created_at,id),
    KEY idx_attachment_uploader (uploader_user_id),
    CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploader_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_attachment_type CHECK (business_type IN ('PRODUCT','DEVICE','INBOUND','OUTBOUND','REQUISITION','STOCKTAKE','REPAIR','WARRANTY','EXCHANGE')),
    CONSTRAINT ck_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 20971520),
    CONSTRAINT ck_attachment_status CHECK (status IN ('READY','DELETE_PENDING','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    event_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    business_type VARCHAR(24) NOT NULL,
    business_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    UNIQUE KEY uk_notification_event_recipient (event_key,recipient_user_id),
    KEY idx_notification_inbox (recipient_user_id,read_at,created_at,id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(permission_code,permission_name,permission_type,route_path,sort_order) VALUES
('attachment.view','查看附件','BUTTON',NULL,230),
('attachment.upload','上传附件','BUTTON',NULL,231),
('attachment.delete','删除附件','BUTTON',NULL,232),
('notification.view','站内通知','MENU','/notifications',240);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('SYSTEM_ADMIN','WAREHOUSE_ADMIN','DEPARTMENT_MANAGER')
  AND p.permission_code IN ('attachment.view','attachment.upload','attachment.delete','notification.view');
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('MANAGEMENT','EMPLOYEE')
  AND p.permission_code IN ('attachment.view','notification.view');
