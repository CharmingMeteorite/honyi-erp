package com.haucheng.erp.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockLedgerView {
    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String productCode;
    private String productName;
    private String businessType;
    private String businessNo;
    private String direction;
    private BigDecimal quantity;
    private BigDecimal beforeQuantity;
    private BigDecimal afterQuantity;
    private Long operatorUserId;
    private String operatorName;
    private LocalDateTime operatedAt;
    private String requestId;
}
