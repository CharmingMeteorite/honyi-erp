package com.haucheng.erp.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReportRowView {
    private String reportType;
    private String serviceKind;
    private Long id;
    private Long orderId;
    private String number;
    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private Long warehouseId;
    private String warehouseName;
    private Long departmentId;
    private String departmentName;
    private Long userId;
    private String userName;
    private String deviceCode;
    private String newDeviceCode;
    private String status;
    private String warrantyStatus;
    private String differenceCode;
    private String differenceDirection;
    private String result;
    private String remark;
    private String notes;
    private String reason;
    private BigDecimal quantity;
    private BigDecimal executedQuantity;
    private BigDecimal lockedQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal safetyStock;
    private BigDecimal snapshotQuantity;
    private BigDecimal countedQuantity;
    private BigDecimal differenceQuantity;
    private BigDecimal finalDifferenceQuantity;
    private Long deviceCount;
    private Long deviceExceptions;
    private Boolean lowStock;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime executedAt;
    private LocalDateTime completedAt;
}
