INSERT INTO sys_permission (permission_code, permission_name, permission_type, sort_order)
VALUES ('system.role.delete', '删除角色', 'BUTTON', 35);
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code = 'system.role.delete'
WHERE r.role_code = 'SYSTEM_ADMIN';

UPDATE sys_permission p
JOIN sys_permission m ON m.permission_code = CONCAT(SUBSTRING_INDEX(p.permission_code, '.', 2), '.view')
SET p.parent_id = m.id
WHERE p.permission_type = 'BUTTON' AND p.parent_id IS NULL;
