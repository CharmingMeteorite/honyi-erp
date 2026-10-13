INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.permission_code IN ('system.device.view','system.device.create','system.device.update','system.device.status')
WHERE r.role_code = 'WAREHOUSE_ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.permission_code = 'system.device.view'
WHERE r.role_code = 'EMPLOYEE';
