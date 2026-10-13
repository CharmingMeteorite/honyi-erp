package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AssetDeviceHistory {
    private Long id;
    private Long deviceId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long fromDepartmentId;
    private Long toDepartmentId;
    private Long fromUserId;
    private Long toUserId;
    private Long fromCustodianId;
    private Long toCustodianId;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private String businessType;
    private String businessNo;
    private String detail;
    private Long operatorUserId;
    private String requestId;
    private LocalDateTime createdAt;
}
