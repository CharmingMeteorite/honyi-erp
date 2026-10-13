package com.haucheng.erp.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InvOpeningBalanceItem {
    private Long id;
    private Long openingId;
    private Long productId;
    private BigDecimal quantity;
}
