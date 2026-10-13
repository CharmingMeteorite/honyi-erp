package com.haucheng.erp.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeviceHistoryView {
    private Long id;
    private Long deviceId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long fromDepartmentId;
    private Long toDepartmentId;
    private String fromDepartmentName;
    private String toDepartmentName;
    private Long fromUserId;
    private Long toUserId;
    private String fromUserName;
    private String toUserName;
    private Long fromCustodianId;
    private Long toCustodianId;
    private String fromCustodianName;
    private String toCustodianName;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private String businessType;
    private String businessNo;
    private String detail;
    private Long operatorUserId;
    private String operatorName;
    private String requestId;
    private LocalDateTime createdAt;
}
