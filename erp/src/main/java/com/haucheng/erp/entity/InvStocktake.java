package com.haucheng.erp.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InvStocktake {
    private Long id;
    private String stocktakeNo;
    private String clientRequestId;
    private Long warehouseId;
    private String status;
    private Long version;
    private Long creatorUserId;
    private LocalDateTime submittedAt;
    private Long confirmerUserId;
    private LocalDateTime confirmedAt;
    private String remark;
    private String requestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
