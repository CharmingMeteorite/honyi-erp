ALTER TABLE asset_devices
    ADD COLUMN warehouse_id BIGINT NULL AFTER custodian_id,
    ADD COLUMN location_id BIGINT NULL AFTER warehouse_id,
    ADD KEY idx_asset_devices_warehouse (warehouse_id),
    ADD KEY idx_asset_devices_location (location_id);
