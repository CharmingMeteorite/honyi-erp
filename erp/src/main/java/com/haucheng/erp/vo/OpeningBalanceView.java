package com.haucheng.erp.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class OpeningBalanceView {
    private Long id;
    private String openingNo;
    private String clientRequestId;
    private Long warehouseId;
    private String warehouseName;
    private String status;
    private String remark;
    private LocalDateTime completedAt;
    private Long operatorUserId;
    private String requestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OpeningBalanceItemView> items = new ArrayList<>();

    @Data
    public static class OpeningBalanceItemView {
        private Long id;
        private Long productId;
        private String productCode;
        private String productName;
        private String productType;
        private BigDecimal quantity;
        private List<Long> deviceIds = new ArrayList<>();
    }
}
