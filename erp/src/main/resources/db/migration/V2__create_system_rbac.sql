ALTER TABLE sys_user
    ADD COLUMN phone VARCHAR(32) NULL AFTER employee_no,
    ADD COLUMN email VARCHAR(128) NULL AFTER phone,
    ADD COLUMN department_id BIGINT NULL AFTER email,
    ADD COLUMN last_login_at DATETIME NULL AFTER status,
    ADD KEY idx_sys_user_department_id (department_id);

CREATE TABLE sys_department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    department_code VARCHAR(64) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    leader_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_sys_department_code (department_code),
    KEY idx_sys_department_parent_id (parent_id), KEY idx_sys_department_leader_id (leader_id),
    CONSTRAINT fk_sys_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department (id),
    CONSTRAINT fk_sys_department_leader FOREIGN KEY (leader_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE sys_user ADD CONSTRAINT fk_sys_user_department FOREIGN KEY (department_id) REFERENCES sys_department (id);

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    data_scope VARCHAR(16) NOT NULL DEFAULT 'SELF',
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_sys_role_code (role_code), UNIQUE KEY uk_sys_role_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(16) NOT NULL,
    route_path VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_sys_permission_code (permission_code),
    KEY idx_sys_permission_parent_id (parent_id),
    CONSTRAINT fk_sys_permission_parent FOREIGN KEY (parent_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_user_id BIGINT NULL,
    operation_type VARCHAR(64) NOT NULL,
    operation_target VARCHAR(128) NULL,
    request_id VARCHAR(64) NULL,
    detail VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_sys_operation_log_operator (operator_user_id), KEY idx_sys_operation_log_created_at (created_at),
    CONSTRAINT fk_sys_operation_log_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (permission_code, permission_name, permission_type, route_path, sort_order) VALUES
('system.user.view','用户管理','MENU','/system/users',10),
('system.user.create','新增用户','BUTTON',NULL,11),
('system.user.update','编辑用户','BUTTON',NULL,12),
('system.user.disable','启停用户','BUTTON',NULL,13),
('system.user.reset-password','重置密码','BUTTON',NULL,14),
('system.user.roles','分配角色','BUTTON',NULL,15),
('system.department.view','部门管理','MENU','/system/departments',20),
('system.department.create','新增部门','BUTTON',NULL,21),
('system.department.update','编辑部门','BUTTON',NULL,22),
('system.department.disable','启停部门','BUTTON',NULL,23),
('system.role.view','角色管理','MENU','/system/roles',30),
('system.role.create','新增角色','BUTTON',NULL,31),
('system.role.update','编辑角色','BUTTON',NULL,32),
('system.role.disable','启停角色','BUTTON',NULL,33),
('system.role.permissions','配置角色权限','BUTTON',NULL,34),
('system.permission.view','权限管理','MENU','/system/permissions',40);

INSERT INTO sys_role (role_code, role_name, description, data_scope) VALUES
('SYSTEM_ADMIN','系统管理员','拥有全部系统权限','ALL'),
('WAREHOUSE_ADMIN','仓库管理员','仓库相关管理权限','DEPARTMENT'),
('DEPARTMENT_MANAGER','部门负责人','本部门数据权限','DEPARTMENT'),
('EMPLOYEE','普通员工','个人数据权限','SELF'),
('MANAGEMENT','管理层','全局查看权限','ALL');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code = 'SYSTEM_ADMIN';
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('system.user.view','system.department.view','system.role.view','system.permission.view')
WHERE r.role_code IN ('MANAGEMENT');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('system.user.view','system.department.view') WHERE r.role_code = 'DEPARTMENT_MANAGER';
