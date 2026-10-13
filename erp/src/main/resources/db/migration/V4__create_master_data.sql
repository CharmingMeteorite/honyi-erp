CREATE TABLE md_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    category_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_md_categories_code (category_code), UNIQUE KEY uk_md_categories_name (category_name),
    KEY idx_md_categories_parent (parent_id), CONSTRAINT fk_md_categories_parent FOREIGN KEY (parent_id) REFERENCES md_categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE md_units (
    id BIGINT NOT NULL AUTO_INCREMENT,
    unit_code VARCHAR(64) NOT NULL,
    unit_name VARCHAR(100) NOT NULL,
    precision_scale INT NOT NULL DEFAULT 0,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_md_units_code (unit_code), UNIQUE KEY uk_md_units_name (unit_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE md_brands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    brand_code VARCHAR(64) NOT NULL,
    brand_name VARCHAR(100) NOT NULL,
    remark VARCHAR(500) NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_md_brands_code (brand_code), UNIQUE KEY uk_md_brands_name (brand_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE md_products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    model VARCHAR(150) NULL,
    specification VARCHAR(500) NULL,
    unit_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    track_sn TINYINT(1) NOT NULL DEFAULT 0,
    track_mac TINYINT(1) NOT NULL DEFAULT 0,
    is_fixed_asset TINYINT(1) NOT NULL DEFAULT 0,
    enable_qrcode TINYINT(1) NOT NULL DEFAULT 0,
    safety_stock DECIMAL(18,4) NOT NULL DEFAULT 0,
    min_stock DECIMAL(18,4) NOT NULL DEFAULT 0,
    max_stock DECIMAL(18,4) NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    status TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_md_products_code (product_code),
    KEY idx_md_products_category (category_id), KEY idx_md_products_brand (brand_id), KEY idx_md_products_unit (unit_id),
    CONSTRAINT fk_md_products_category FOREIGN KEY (category_id) REFERENCES md_categories(id),
    CONSTRAINT fk_md_products_brand FOREIGN KEY (brand_id) REFERENCES md_brands(id),
    CONSTRAINT fk_md_products_unit FOREIGN KEY (unit_id) REFERENCES md_units(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (permission_code, permission_name, permission_type, route_path, sort_order) VALUES
('system.category.view','物资分类','MENU','/master-data/categories',50),('system.category.create','新增分类','BUTTON',NULL,51),('system.category.update','编辑分类','BUTTON',NULL,52),('system.category.disable','启停分类','BUTTON',NULL,53),('system.category.delete','删除分类','BUTTON',NULL,54),
('system.unit.view','单位管理','MENU','/master-data/units',60),('system.unit.create','新增单位','BUTTON',NULL,61),('system.unit.update','编辑单位','BUTTON',NULL,62),('system.unit.disable','启停单位','BUTTON',NULL,63),('system.unit.delete','删除单位','BUTTON',NULL,64),
('system.brand.view','品牌管理','MENU','/master-data/brands',70),('system.brand.create','新增品牌','BUTTON',NULL,71),('system.brand.update','编辑品牌','BUTTON',NULL,72),('system.brand.disable','启停品牌','BUTTON',NULL,73),('system.brand.delete','删除品牌','BUTTON',NULL,74),
('system.product.view','物资档案','MENU','/master-data/products',80),('system.product.create','新增物资','BUTTON',NULL,81),('system.product.update','编辑物资','BUTTON',NULL,82),('system.product.disable','启停物资','BUTTON',NULL,83);
INSERT INTO sys_role_permission (role_id, permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code='SYSTEM_ADMIN' AND p.permission_code LIKE 'system.%' AND p.permission_code IN ('system.category.view','system.category.create','system.category.update','system.category.disable','system.category.delete','system.unit.view','system.unit.create','system.unit.update','system.unit.disable','system.unit.delete','system.brand.view','system.brand.create','system.brand.update','system.brand.disable','system.brand.delete','system.product.view','system.product.create','system.product.update','system.product.disable');
UPDATE sys_permission p JOIN sys_permission m ON m.permission_code=CONCAT(SUBSTRING_INDEX(p.permission_code,'.',2),'.view') SET p.parent_id=m.id WHERE p.permission_type='BUTTON' AND p.parent_id IS NULL;
