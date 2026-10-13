package com.haucheng.erp.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvStock {
    private Long id;
    private Long warehouseId;
    private Long productId;
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal lockedQuantity = BigDecimal.ZERO;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
