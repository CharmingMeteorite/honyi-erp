package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApplyRequisition {
    private Long id;
    private String orderNo;
    private String clientRequestId;
    private Long warehouseId;
    private String status;
    private Long version;
    private Long creatorUserId;
    private Long departmentId;
    private LocalDateTime submittedAt;
    private Long approverUserId;
    private LocalDateTime approvedAt;
    private Long executorUserId;
    private LocalDateTime executedAt;
    private String remark;
    private String requestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
