ALTER TABLE wh_warehouses
    ADD COLUMN department_id BIGINT NULL AFTER warehouse_name,
    ADD KEY idx_wh_warehouses_department (department_id),
    ADD CONSTRAINT fk_wh_warehouses_department FOREIGN KEY (department_id) REFERENCES sys_department(id);

ALTER TABLE inv_opening_balances ADD COLUMN client_request_id VARCHAR(64) NULL AFTER opening_no;
UPDATE inv_opening_balances SET client_request_id=CONCAT('legacy-opening-',id) WHERE client_request_id IS NULL;
ALTER TABLE inv_opening_balances
    MODIFY client_request_id VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY uk_inv_opening_client_request (client_request_id),
    ADD CONSTRAINT ck_inv_opening_status CHECK (status IN ('DRAFT','COMPLETED'));

ALTER TABLE inv_opening_balance_devices
    ADD COLUMN initialized_device_id BIGINT NULL,
    ADD UNIQUE KEY uk_inv_initialized_device (initialized_device_id),
    ADD CONSTRAINT fk_inv_initialized_device FOREIGN KEY (initialized_device_id) REFERENCES asset_devices(id),
    ADD CONSTRAINT ck_inv_initialized_device CHECK (initialized_device_id IS NULL OR initialized_device_id=device_id);

ALTER TABLE inv_stock_ledgers
    MODIFY business_no VARCHAR(64) NOT NULL,
    ADD CONSTRAINT ck_inv_ledger_before CHECK (before_quantity >= 0),
    ADD CONSTRAINT ck_inv_ledger_direction CHECK (
        (direction='IN' AND after_quantity=before_quantity+quantity) OR
        (direction='OUT' AND after_quantity=before_quantity-quantity)
    );

CREATE INDEX idx_asset_devices_stock_count ON asset_devices(warehouse_id,product_id,status);

UPDATE sys_permission SET route_path='/inventory/openings',permission_type='MENU'
WHERE permission_code='inventory.stock.opening';
UPDATE sys_permission SET route_path='/inventory/ledgers',permission_type='MENU'
WHERE permission_code='inventory.stock.ledger';
