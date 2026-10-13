package com.haucheng.erp.vo;


import lombok.Data;

import java.math.BigDecimal;

@Data

public class StockConsistencyIssueView {
    private Long warehouseId;
    private Long productId;
    private String message;
    private BigDecimal stockQuantity;
    private BigDecimal deviceQuantity;
}
