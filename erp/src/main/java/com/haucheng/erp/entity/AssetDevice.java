package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AssetDevice {
    private Long id;
    private Long version;
    private String deviceCode;
    private Long productId;
    private String serialNumber;
    private String macAddress;
    private String assetNo;
    private String status = "IN_STOCK";
    private Long departmentId;
    private Long userId;
    private Long custodianId;
    private Long warehouseId;
    private Long locationId;
    private LocalDateTime purchaseAt;
    private LocalDateTime inboundAt;
    private LocalDateTime firstUseAt;
    private LocalDateTime warrantyStartAt;
    private LocalDateTime warrantyEndAt;
    private String remark;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
