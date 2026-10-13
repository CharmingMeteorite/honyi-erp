package com.haucheng.erp.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Data
public class RequisitionView {
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
    private String warehouseName;
    private String creatorName;
    private String departmentName;
    private String approverName;
    private String executorName;
    private List<InventoryOrderItemView> items = new ArrayList<>();
    private List<InventoryOrderLogView> logs = new ArrayList<>();
    private List<StockLedgerView> ledgers = new ArrayList<>();
}
