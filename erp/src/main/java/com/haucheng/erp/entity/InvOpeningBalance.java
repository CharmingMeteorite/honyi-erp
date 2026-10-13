package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InvOpeningBalance {
    private Long id;
    private String openingNo;
    private String clientRequestId;
    private Long warehouseId;
    private String status = "DRAFT";
    private String remark;
    private LocalDateTime completedAt;
    private Long operatorUserId;
    private String requestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
