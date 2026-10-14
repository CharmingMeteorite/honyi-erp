ALTER TABLE inv_stocktake_device_checks
    DROP INDEX uk_stocktake_device_code,
    ADD UNIQUE KEY uk_stocktake_item_device_code (item_id,device_code);
