ALTER TABLE asset_repair_orders ADD COLUMN device_version BIGINT NULL, ADD CONSTRAINT ck_repair_device_version CHECK (device_version IS NULL OR device_version >= 0);
ALTER TABLE asset_exchange_orders ADD COLUMN device_version BIGINT NULL, ADD COLUMN new_device_version BIGINT NULL,
    ADD CONSTRAINT ck_exchange_device_version CHECK (device_version IS NULL OR device_version >= 0),
    ADD CONSTRAINT ck_exchange_new_device_version CHECK (new_device_version IS NULL OR new_device_version >= 0);
ALTER TABLE asset_warranty_orders ADD COLUMN device_version BIGINT NULL, ADD CONSTRAINT ck_warranty_device_version CHECK (device_version IS NULL OR device_version >= 0);
