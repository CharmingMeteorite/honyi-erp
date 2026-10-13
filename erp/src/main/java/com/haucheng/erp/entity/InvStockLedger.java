package com.haucheng.erp.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvStockLedger {
    private Long id;
    private Long warehouseId;
    private Long productId;
    private String businessType;
    private String businessNo;
    private String direction;
    private BigDecimal quantity;
    private BigDecimal beforeQuantity;
    private BigDecimal afterQuantity;
    private Long operatorUserId;
    private LocalDateTime operatedAt;
    private String requestId;
}
