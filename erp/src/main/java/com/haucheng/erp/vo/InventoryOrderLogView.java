package com.haucheng.erp.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InventoryOrderLogView {
    private Long id;
    private String operationType;
    private String detail;
    private Long operatorUserId;
    private String operatorName;
    private String requestId;
    private LocalDateTime createdAt;
}
