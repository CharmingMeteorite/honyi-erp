package com.haucheng.erp.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class StocktakeView {
    private Long id;
    private String stocktakeNo;
    private String clientRequestId;
    private Long warehouseId;
    private String warehouseName;
    private String status;
    private Long version;
    private Long creatorUserId;
    private String creatorName;
    private LocalDateTime submittedAt;
    private Long confirmerUserId;
    private String confirmerName;
    private LocalDateTime confirmedAt;
    private String remark;
    private String requestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StocktakeItemView> items = new ArrayList<>();
    private List<InventoryOrderLogView> logs = new ArrayList<>();
    private List<StockLedgerView> ledgers = new ArrayList<>();
}
