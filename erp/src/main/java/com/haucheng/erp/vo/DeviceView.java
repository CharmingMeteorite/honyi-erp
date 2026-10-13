package com.haucheng.erp.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeviceView {
    private Long id;
    private Boolean requisitionIssued;
    private String deviceCode;
    private Long productId;
    private String productCode;
    private String productName;
    private String brandName;
    private String model;
    private String specification;
    private Boolean trackSn;
    private Boolean trackMac;
    private Boolean identityEditable;
    private List<String> allowedStatuses = new ArrayList<>();
    private String serialNumber;
    private String macAddress;
    private String assetNo;
    private String status;
    private Long departmentId;
    private String departmentName;
    private Long userId;
    private String userName;
    private Long custodianId;
    private String custodianName;
    private Long warehouseId;
    private String warehouseName;
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
    private List<DeviceHistoryView> history = new ArrayList<>();
}
